package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChordRepository {
    private static final String[] EXAMPLES = {"C", "Am", "G7", "Fmaj7", "Cmaj7", "Csus4", "Cadd9", "G/B"};
    private static final String[] CHROMATIC_ROOTS = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private final Map<String, ChordQuality> qualitiesById = new LinkedHashMap<>();
    private final List<ChordShape> shapes = new ArrayList<>();
    private final Map<String, List<ChordShape>> shapesBySymbol = new LinkedHashMap<>();
    private final Map<String, Chord> chordsBySymbol = new LinkedHashMap<>();
    private final Map<String, Chord> chordsByAlias = new LinkedHashMap<>();
    private ChordFormulaRepository formulaRepository;
    private GuitarVoicingRepository guitarVoicingRepository;
    private ChordTheoryEngine theoryEngine;
    private ChordNameParser nameParser;
    private ChordVoicingGenerator voicingGenerator;
    private boolean usingFallbackData;
    private String dataLoadMessage = "";

    public ChordRepository() {
        try {
            applyLoadedData(new ChordDataLoader().loadDefault());
        } catch (ChordDataLoader.ChordDataException exception) {
            loadSafeFallback(exception.getMessage());
        }
        rebuildIndexes();
    }

    public ChordRepository(ChordDataLoader.AssetSource assetSource) {
        try {
            applyLoadedData(new ChordDataLoader().load(assetSource));
        } catch (ChordDataLoader.ChordDataException exception) {
            loadSafeFallback(exception.getMessage());
        }
        rebuildIndexes();
    }

    public ChordRepository(
            ChordFormulaRepository formulaRepository,
            GuitarVoicingRepository guitarVoicingRepository
    ) {
        if (formulaRepository == null || guitarVoicingRepository == null) {
            throw new IllegalArgumentException("Formula and voicing repositories must not be null.");
        }
        this.formulaRepository = formulaRepository;
        this.guitarVoicingRepository = guitarVoicingRepository;
        for (ChordFormula formula : formulaRepository.getAll()) {
            registerQuality(formula.toChordQuality());
        }
        for (ChordShape shape : guitarVoicingRepository.getAllShapes()) {
            registerShape(shape);
        }
        initializeEngines();
        generateMissingRootPositionShapes();
        dataLoadMessage = "Chord data supplied by caller.";
        rebuildIndexes();
    }

    void registerQuality(ChordQuality quality) {
        qualitiesById.put(quality.id, quality);
    }

    void registerShape(ChordShape shape) {
        if (!qualitiesById.containsKey(shape.qualityId)) {
            throw new IllegalArgumentException("Unknown qualityId: " + shape.qualityId);
        }
        shapes.add(shape);
    }

    public List<ChordQuality> getAllQualities() {
        return new ArrayList<>(qualitiesById.values());
    }

    public List<ChordShape> getAllShapes() {
        return new ArrayList<>(shapes);
    }

    public List<ChordShape> getShapesByRoot(String root) {
        String canonicalRoot = canonicalRootFilter(root);
        if (canonicalRoot.isEmpty()) {
            return getAllShapes();
        }
        List<ChordShape> result = new ArrayList<>();
        for (ChordShape shape : shapes) {
            if (canonicalRoot.equals(shape.root)) {
                result.add(shape);
            }
        }
        return result;
    }

    public List<ChordShape> getShapesByQuality(String qualityId) {
        String normalized = normalizeTypeFilter(qualityId);
        if (normalized.isEmpty()) {
            return getAllShapes();
        }
        List<ChordShape> result = new ArrayList<>();
        for (ChordShape shape : shapes) {
            if (matchesType(shape, normalized)) {
                result.add(shape);
            }
        }
        return result;
    }

    public List<ChordShape> getShapes(String root, String qualityId) {
        String canonicalRoot = canonicalRootFilter(root);
        String normalizedQuality = normalizeTypeFilter(qualityId);
        List<ChordShape> result = new ArrayList<>();
        for (ChordShape shape : shapes) {
            boolean rootMatches = canonicalRoot.isEmpty() || canonicalRoot.equals(shape.root);
            boolean qualityMatches = normalizedQuality.isEmpty() || matchesType(shape, normalizedQuality);
            if (rootMatches && qualityMatches) {
                result.add(shape);
            }
        }
        return result;
    }

    public List<ChordShape> getBeginnerShapes() {
        List<ChordShape> result = new ArrayList<>();
        for (ChordShape shape : shapes) {
            if (shape.difficulty <= 2) {
                result.add(shape);
            }
        }
        return result;
    }

    public List<ChordShape> search(String keyword) {
        List<ChordShape> result = filterShapes(keyword, "", "", 0);
        if (keyword != null && !keyword.trim().isEmpty()) {
            LookupResult exact = find(keyword);
            if (exact.recognized && exact.chord != null) {
                for (int index = exact.chord.shapes.size() - 1; index >= 0; index--) {
                    ChordShape shape = exact.chord.shapes.get(index);
                    if (!result.contains(shape)) {
                        result.add(0, shape);
                    }
                }
            }
        }
        return result;
    }

    public List<Chord> filteredChords(String keyword, String root, String type, int difficultyBucket) {
        List<ChordShape> filteredShapes = filterShapes(keyword, root, type, difficultyBucket);
        LinkedHashMap<String, Chord> result = new LinkedHashMap<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            LookupResult exact = find(keyword);
            if (exact.recognized && exact.chord != null
                    && matchesChordFilters(exact.chord, root, type, difficultyBucket)) {
                result.put(exact.chord.symbol, exact.chord);
            }
        }
        for (ChordShape shape : filteredShapes) {
            ChordQuality quality = qualitiesById.get(shape.qualityId);
            Chord chord = chordsBySymbol.get(shape.symbol(quality));
            if (chord != null) {
                result.put(chord.symbol, chord);
            }
        }
        return new ArrayList<>(result.values());
    }

    public LookupResult find(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) {
            return LookupResult.error("请输入和弦名称，例如 C、Am、G7、Fmaj7。");
        }
        ChordNameParser.ParseResult parsed = nameParser.parse(input);
        if (!parsed.recognized) {
            return LookupResult.error(parsed.error);
        }
        Chord chord = chordsByAlias.get(parsed.normalizedSymbol);
        if (chord == null) {
            List<ChordShape> generatedShapes = new ArrayList<>();
            if (!parsed.canonicalBassNote.isEmpty()) {
                ChordShape generated = voicingGenerator.generate(
                        parsed.root,
                        parsed.bassNote,
                        parsed.formula
                );
                if (generated != null) {
                    generatedShapes.add(generated);
                }
            }
            chord = theoryEngine.buildChord(
                    parsed.root,
                    parsed.bassNote,
                    parsed.formula,
                    generatedShapes
            );
        } else if (!parsed.displaySymbol.equals(parsed.normalizedSymbol)) {
            chord = theoryEngine.buildChord(
                    parsed.root,
                    parsed.bassNote,
                    parsed.formula,
                    chord.shapes
            );
        }
        if (chord == null) {
            return LookupResult.error("无法根据该名称生成和弦数据。");
        }
        StringBuilder message = new StringBuilder();
        if (!input.equals(chord.symbol)) {
            message.append("已将输入规范化为 ").append(chord.symbol).append("。");
        }
        if (chord.voicings.isEmpty()) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append("该和弦理论数据可用，当前暂无收录指法。");
        }
        return LookupResult.success(chord, message.length() == 0 ? null : message.toString());
    }

    public List<String> examples() {
        return Arrays.asList(EXAMPLES);
    }

    public List<Chord> allChords() {
        return new ArrayList<>(chordsBySymbol.values());
    }

    public ChordFormulaRepository getFormulaRepository() {
        return formulaRepository;
    }

    public GuitarVoicingRepository getGuitarVoicingRepository() {
        return guitarVoicingRepository;
    }

    public ChordTheoryEngine getTheoryEngine() {
        return theoryEngine;
    }

    public ChordNameParser getNameParser() {
        return nameParser;
    }

    public ChordSymbolParser getSymbolParser() {
        return nameParser;
    }

    public boolean isUsingFallbackData() {
        return usingFallbackData;
    }

    public String getDataLoadMessage() {
        return dataLoadMessage;
    }

    ChordQuality qualityForId(String qualityId) {
        return qualitiesById.get(qualityId);
    }

    private void rebuildIndexes() {
        shapesBySymbol.clear();
        chordsBySymbol.clear();
        chordsByAlias.clear();

        for (ChordShape shape : shapes) {
            ChordQuality quality = qualitiesById.get(shape.qualityId);
            String symbol = shape.symbol(quality);
            List<ChordShape> symbolShapes = shapesBySymbol.get(symbol);
            if (symbolShapes == null) {
                symbolShapes = new ArrayList<>();
                shapesBySymbol.put(symbol, symbolShapes);
            }
            symbolShapes.add(shape);
        }

        for (String root : CHROMATIC_ROOTS) {
            for (ChordFormula formula : formulaRepository.getAll()) {
                String symbol = root + formula.suffix;
                List<ChordShape> symbolShapes = shapesBySymbol.get(symbol);
                if (symbolShapes == null) {
                    symbolShapes = new ArrayList<>();
                }
                Chord chord = theoryEngine.buildChord(root, "", formula, symbolShapes);
                chordsBySymbol.put(chord.symbol, chord);
            }
        }

        for (Map.Entry<String, List<ChordShape>> entry : shapesBySymbol.entrySet()) {
            if (chordsBySymbol.containsKey(entry.getKey())) {
                continue;
            }
            Chord chord = buildChord(entry.getKey(), entry.getValue());
            chordsBySymbol.put(chord.symbol, chord);
        }

        for (Chord chord : chordsBySymbol.values()) {
            registerAlias(chord.symbol, chord);
            for (String alias : chord.aliases) {
                registerAlias(alias, chord);
            }
        }
    }

    private Chord buildChord(String symbol, List<ChordShape> chordShapes) {
        ChordShape first = chordShapes.get(0);
        ChordFormula formula = formulaRepository.findById(first.qualityId);
        return theoryEngine.buildChord(first.root, first.bassNote, formula, chordShapes);
    }

    private List<ChordShape> filterShapes(String keyword, String root, String type, int difficultyBucket) {
        String canonicalRoot = canonicalRootFilter(root);
        String normalizedType = normalizeTypeFilter(type);
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        List<ChordShape> result = new ArrayList<>();
        for (ChordShape shape : shapes) {
            if (!canonicalRoot.isEmpty() && !canonicalRoot.equals(shape.root)) {
                continue;
            }
            if (!normalizedType.isEmpty() && !matchesType(shape, normalizedType)) {
                continue;
            }
            if (difficultyBucket > 0 && !matchesDifficulty(shape, difficultyBucket)) {
                continue;
            }
            if (!normalizedKeyword.isEmpty() && !matchesKeyword(shape, normalizedKeyword)) {
                continue;
            }
            result.add(shape);
        }
        return result;
    }

    private boolean matchesType(ChordShape shape, String type) {
        if ("slash".equals(type)) {
            return shape.isSlash();
        }
        ChordQuality quality = qualitiesById.get(shape.qualityId);
        if ("sus".equals(type)) {
            return quality != null && "suspended".equals(quality.category);
        }
        if ("add".equals(type)) {
            return quality != null && quality.category.startsWith("added");
        }
        return type.equals(shape.qualityId);
    }

    private boolean matchesChordFilters(Chord chord, String root, String type, int difficultyBucket) {
        String canonicalRoot = canonicalRootFilter(root);
        if (!canonicalRoot.isEmpty() && !canonicalRoot.equals(NoteUtils.canonicalPitchClass(chord.root))) {
            return false;
        }
        String normalizedType = normalizeTypeFilter(type);
        if (!normalizedType.isEmpty()) {
            if ("slash".equals(normalizedType)) {
                if (chord.bassNote.isEmpty()) {
                    return false;
                }
            } else if ("sus".equals(normalizedType)) {
                ChordFormula formula = formulaRepository.findById(chord.qualityId);
                if (formula == null || !"suspended".equals(formula.category)) {
                    return false;
                }
            } else if ("add".equals(normalizedType)) {
                ChordFormula formula = formulaRepository.findById(chord.qualityId);
                if (formula == null || !formula.category.startsWith("added")) {
                    return false;
                }
            } else if (!normalizedType.equals(chord.qualityId)) {
                return false;
            }
        }
        if (difficultyBucket > 0) {
            if (chord.shapes.isEmpty() || !matchesDifficulty(chord.shapes.get(0), difficultyBucket)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesDifficulty(ChordShape shape, int bucket) {
        if (bucket == 1) {
            return shape.difficulty <= 2;
        }
        if (bucket == 2) {
            return shape.difficulty == 3;
        }
        return shape.difficulty >= 4;
    }

    private boolean matchesKeyword(ChordShape shape, String keyword) {
        ChordQuality quality = qualitiesById.get(shape.qualityId);
        String symbol = shape.symbol(quality);
        Set<String> values = new LinkedHashSet<>();
        values.add(symbol);
        values.add(shape.name);
        values.add(shape.root);
        values.add(shape.bassNote);
        values.add(shape.qualityId);
        values.add(shape.shapeType);
        values.add(shape.note);
        values.add(shape.fretPattern());
        values.add(shape.difficultyLabel());
        if (quality != null) {
            values.add(quality.displayName);
            values.add(quality.chineseName);
            values.add(quality.category);
            values.add(quality.description);
            for (String label : quality.intervalLabels) {
                values.add(label);
            }
        }
        Chord chord = chordsBySymbol.get(symbol);
        if (chord != null) {
            values.add(chord.chineseName);
            values.add(chord.quality);
            values.addAll(chord.notes);
            values.addAll(chord.aliases);
        }
        String normalizedSymbol = canonicalLookupKey(keyword);
        for (String value : values) {
            if (contains(value, keyword) || (!normalizedSymbol.isEmpty() && canonicalLookupKey(value).contains(normalizedSymbol))) {
                return true;
            }
        }
        return false;
    }

    private void registerAlias(String alias, Chord chord) {
        ChordNameParser.ParseResult parsed = nameParser.parse(alias);
        String key = parsed.recognized ? parsed.normalizedSymbol : canonicalLookupKey(alias);
        if (!key.isEmpty()) {
            chordsByAlias.put(key, chord);
        }
    }

    private void applyLoadedData(ChordDataLoader.LoadedData loaded) {
        formulaRepository = loaded.formulas;
        guitarVoicingRepository = loaded.voicings;
        for (ChordFormula formula : formulaRepository.getAll()) {
            registerQuality(formula.toChordQuality());
        }
        for (ChordShape shape : guitarVoicingRepository.getAllShapes()) {
            registerShape(shape);
        }
        initializeEngines();
        generateMissingRootPositionShapes();
        usingFallbackData = false;
        dataLoadMessage = "Loaded chord JSON from " + loaded.sourceDescription + ".";
    }

    private void loadSafeFallback(String reason) {
        qualitiesById.clear();
        shapes.clear();
        ChordLibraryData.populate(this);
        List<ChordFormula> formulas = new ArrayList<>();
        for (ChordQuality quality : qualitiesById.values()) {
            formulas.add(ChordFormula.fromQuality(quality));
        }
        formulaRepository = new ChordFormulaRepository(formulas);
        List<GuitarVoicingDefinition> voicings = new ArrayList<>();
        int index = 1;
        for (ChordShape shape : shapes) {
            voicings.add(GuitarVoicingDefinition.fromChordShape(
                    shape,
                    qualitiesById.get(shape.qualityId),
                    index++
            ));
        }
        guitarVoicingRepository = new GuitarVoicingRepository(formulaRepository, voicings);
        initializeEngines();
        generateMissingRootPositionShapes();
        usingFallbackData = true;
        dataLoadMessage = "Chord JSON unavailable; using safe built-in fallback. " + (reason == null ? "" : reason);
    }

    private void initializeEngines() {
        theoryEngine = new ChordTheoryEngine();
        nameParser = new ChordNameParser(formulaRepository);
        voicingGenerator = new ChordVoicingGenerator();
    }

    private void generateMissingRootPositionShapes() {
        Set<String> covered = new LinkedHashSet<>();
        for (ChordShape shape : shapes) {
            if (shape.bassNote.isEmpty()) {
                covered.add(shape.root + ":" + shape.qualityId);
            }
        }
        for (String root : CHROMATIC_ROOTS) {
            for (ChordFormula formula : formulaRepository.getAll()) {
                String key = root + ":" + formula.id;
                if (covered.contains(key)) {
                    continue;
                }
                ChordShape generated = voicingGenerator.generate(root, "", formula);
                if (generated != null) {
                    registerShape(generated);
                    covered.add(key);
                }
            }
        }
    }

    private String canonicalLookupKey(String symbol) {
        ChordNameParser.ParseResult parsed = nameParser.parse(symbol);
        if (parsed.recognized) {
            return parsed.normalizedSymbol;
        }
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }

    private static String normalizeSearchKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.CHINA);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && keyword != null
                && value.toLowerCase(Locale.CHINA).contains(keyword.toLowerCase(Locale.CHINA));
    }

    private String canonicalRootFilter(String root) {
        if (root == null || root.trim().isEmpty() || "全部".equals(root)) {
            return "";
        }
        String cleaned = root.replace(" ", "").replace("♯", "#").replace("♭", "b");
        int slash = cleaned.indexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(0, slash);
        }
        return NoteUtils.canonicalPitchClass(cleaned);
    }

    private static String normalizeTypeFilter(String type) {
        if (type == null || type.trim().isEmpty() || "全部".equals(type)) {
            return "";
        }
        String value = type.trim().toLowerCase(Locale.CHINA);
        if (value.contains("大三") || value.equals("major") || value.equals("maj")) {
            return "maj";
        }
        if (value.contains("小三") || value.equals("minor") || value.equals("min") || value.equals("m")) {
            return "m";
        }
        if (value.contains("属七") || value.equals("dominant") || value.equals("dom7") || value.equals("7")) {
            return "7";
        }
        if (value.contains("大七") || value.equals("major7") || value.equals("maj7") || value.equals("m7+")) {
            return "maj7";
        }
        if (value.contains("小七") || value.equals("minor7") || value.equals("min7")) {
            return "m7";
        }
        if (value.startsWith("sus") || value.contains("挂")) {
            return "sus";
        }
        if (value.startsWith("add") || value.contains("加")) {
            return "add";
        }
        if (value.contains("dim") || value.contains("减")) {
            return "dim";
        }
        if (value.contains("aug") || value.contains("增")) {
            return "aug";
        }
        if (value.contains("slash") || value.contains("分数")) {
            return "slash";
        }
        return value;
    }

    public static final class LookupResult {
        public final boolean recognized;
        public final Chord chord;
        public final String message;

        private LookupResult(boolean recognized, Chord chord, String message) {
            this.recognized = recognized;
            this.chord = chord;
            this.message = message;
        }

        public static LookupResult success(Chord chord, String message) {
            return new LookupResult(true, chord, message);
        }

        public static LookupResult error(String message) {
            return new LookupResult(false, null, message);
        }
    }

}
