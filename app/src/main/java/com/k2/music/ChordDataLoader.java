package com.k2.music;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads and validates the two offline chord JSON assets. */
public final class ChordDataLoader {
    public static final String FORMULAS_ASSET = "chords/chord_formulas.json";
    public static final String VOICINGS_ASSET = "chords/guitar_voicings.json";

    private static volatile AssetSource defaultAssetSource;
    private static volatile LoadedData defaultCache;
    private static volatile Thread defaultPreloadThread;
    private static volatile ChordDataException defaultPreloadFailure;

    public static synchronized void setDefaultAssetSource(AssetSource source) {
        defaultAssetSource = source;
        defaultCache = null;
        defaultPreloadFailure = null;
    }

    /** Starts the one-time JSON parse on a worker before the first repository is requested. */
    public static synchronized void preloadDefaultAsync() {
        if (defaultCache != null || (defaultPreloadThread != null && defaultPreloadThread.isAlive())) {
            return;
        }
        defaultPreloadFailure = null;
        Thread worker = new Thread(() -> {
            LoadedData loaded = null;
            ChordDataException failure = null;
            try {
                loaded = new ChordDataLoader().loadDefaultUncached();
            } catch (ChordDataException exception) {
                failure = exception;
            }
            synchronized (ChordDataLoader.class) {
                if (failure == null) {
                    defaultCache = loaded;
                } else {
                    defaultPreloadFailure = failure;
                }
                defaultPreloadThread = null;
            }
        }, "chord-data-preload");
        worker.setDaemon(true);
        defaultPreloadThread = worker;
        worker.start();
    }

    public LoadedData loadDefault() throws ChordDataException {
        LoadedData cached = defaultCache;
        if (cached != null) {
            return cached;
        }
        Thread preload = defaultPreloadThread;
        if (preload != null && preload != Thread.currentThread()) {
            try {
                preload.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ChordDataException("Interrupted while waiting for chord data preload.", exception);
            }
            cached = defaultCache;
            if (cached != null) {
                return cached;
            }
            if (defaultPreloadFailure != null) {
                throw defaultPreloadFailure;
            }
        }
        synchronized (ChordDataLoader.class) {
            cached = defaultCache;
            if (cached == null) {
                if (defaultPreloadFailure != null) {
                    throw defaultPreloadFailure;
                }
                cached = loadDefaultUncached();
                defaultCache = cached;
            }
            return cached;
        }
    }

    private LoadedData loadDefaultUncached() throws ChordDataException {
        List<String> errors = new ArrayList<>();
        AssetSource registered = defaultAssetSource;
        if (registered != null) {
            try {
                return load(registered, "Android assets");
            } catch (ChordDataException exception) {
                errors.add(exception.getMessage());
            }
        }

        AssetSource classpath = classpathSource();
        try {
            return load(classpath, "classpath assets");
        } catch (ChordDataException exception) {
            errors.add(exception.getMessage());
        }

        for (File root : developmentAssetRoots()) {
            if (!new File(root, FORMULAS_ASSET).isFile()
                    || !new File(root, VOICINGS_ASSET).isFile()) {
                continue;
            }
            try {
                return load(relativePath -> new FileInputStream(new File(root, relativePath)), root.toString());
            } catch (ChordDataException exception) {
                errors.add(exception.getMessage());
            }
        }
        throw new ChordDataException("Unable to load bundled chord JSON. " + joinErrors(errors));
    }

    public LoadedData load(AssetSource source) throws ChordDataException {
        return load(source, "custom asset source");
    }

    public LoadedData load(Reader formulaReader, Reader voicingReader) throws ChordDataException {
        if (formulaReader == null || voicingReader == null) {
            throw new ChordDataException("Formula and voicing readers must not be null.");
        }
        return load(formulaReader, voicingReader, "readers");
    }

    private LoadedData load(AssetSource source, String description) throws ChordDataException {
        if (source == null) {
            throw new ChordDataException("Asset source must not be null.");
        }
        try (InputStream formulaStream = source.open(FORMULAS_ASSET);
             InputStream voicingStream = source.open(VOICINGS_ASSET)) {
            if (formulaStream == null || voicingStream == null) {
                throw new IOException("One or both chord asset streams were null.");
            }
            return load(
                    new InputStreamReader(formulaStream, StandardCharsets.UTF_8),
                    new InputStreamReader(voicingStream, StandardCharsets.UTF_8),
                    description
            );
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ChordDataRuntimeException) {
                throw new ChordDataException(exception.getMessage(), exception);
            }
            throw new ChordDataException("Failed to load chord data from " + description + ": "
                    + safeMessage(exception), exception);
        }
    }

    private LoadedData load(Reader formulaReader, Reader voicingReader, String description)
            throws ChordDataException {
        try {
            Map<String, Object> formulaRoot = object(SimpleJsonParser.parse(formulaReader), "formula root");
            Map<String, Object> voicingRoot = object(SimpleJsonParser.parse(voicingReader), "voicing root");
            int formulaSchema = integer(formulaRoot, 1, "schemaVersion");
            int voicingSchema = integer(voicingRoot, 1, "schemaVersion");
            List<ChordFormula> formulas = parseFormulas(array(formulaRoot.get("formulas"), "formulas"));
            ChordFormulaRepository formulaRepository = new ChordFormulaRepository(formulas);
            List<GuitarVoicingDefinition> voicings = parseVoicings(
                    array(voicingRoot.get("voicings"), "voicings"),
                    formulaRepository
            );
            ChordVoicingValidator.requireValid(formulaRepository, voicings);
            GuitarVoicingRepository voicingRepository = new GuitarVoicingRepository(formulaRepository, voicings);
            return new LoadedData(
                    formulaSchema,
                    voicingSchema,
                    description,
                    formulaRepository,
                    voicingRepository
            );
        } catch (IOException | RuntimeException exception) {
            throw new ChordDataException("Invalid chord JSON from " + description + ": "
                    + safeMessage(exception), exception);
        }
    }

    private static List<ChordFormula> parseFormulas(List<Object> entries) {
        if (entries.isEmpty()) {
            throw fail("The formulas array is empty.");
        }
        List<ChordFormula> formulas = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> item = object(entries.get(index), "formulas[" + index + "]");
            String id = requiredString(item, "id");
            if (!ids.add(id)) {
                throw fail("Duplicate formula id: " + id);
            }
            String suffix = string(item, "suffix", "standardSuffix");
            if (suffix == null) {
                suffix = "maj".equals(id) ? "" : id;
            }
            List<String> intervals = stringList(first(item, "intervals", "intervalFormula"));
            List<String> aliases = stringList(first(item, "aliases", "commonAliases"));
            formulas.add(new ChordFormula(
                    id,
                    suffix,
                    orEmpty(string(item, "chineseName", "nameZh")),
                    orEmpty(string(item, "englishName", "nameEn", "displayName")),
                    intervals,
                    aliases,
                    orEmpty(string(item, "description", "summary")),
                    orEmpty(string(item, "category", "tag")),
                    difficulty(item, 2),
                    nullableStringList(item, "requiredIntervals"),
                    nullableStringList(item, "optionalIntervals"),
                    nullableStringList(item, "omittableIntervals"),
                    stringGroups(item.get("requiredAnyOf"))
            ));
        }
        return formulas;
    }

    private static List<GuitarVoicingDefinition> parseVoicings(
            List<Object> entries,
            ChordFormulaRepository formulas
    ) {
        List<GuitarVoicingDefinition> voicings = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        ChordSymbolParser parser = new ChordSymbolParser(formulas);
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> item = object(entries.get(index), "voicings[" + index + "]");
            String symbol = requiredString(item, "chordSymbol", "symbol");
            ChordSymbolParser.ParseResult parsed = parser.parse(symbol);
            if (!parsed.recognized) {
                throw fail("Voicing " + symbol + " has an invalid chord symbol: " + parsed.error);
            }
            String id = orEmpty(string(item, "id"));
            if (id.isEmpty()) {
                id = parsed.normalizedSymbol.toLowerCase(Locale.US).replace("#", "s").replace("/", "-")
                        + "-" + (index + 1);
            }
            if (!ids.add(id)) {
                throw fail("Duplicate voicing id: " + id);
            }
            String root = orEmpty(string(item, "root"));
            if (root.isEmpty()) {
                root = parsed.canonicalRoot;
            }
            String canonicalRoot = NoteUtils.canonicalPitchClass(root);
            if (canonicalRoot.isEmpty() || !canonicalRoot.equals(parsed.canonicalRoot)) {
                throw fail("Voicing " + id + " root does not match chordSymbol " + symbol + ".");
            }
            root = canonicalRoot;
            String formulaId = orEmpty(string(item, "formulaId", "qualityId"));
            if (formulaId.isEmpty()) {
                formulaId = parsed.formulaId;
            }
            if (!formulaId.equals(parsed.formulaId)) {
                throw fail("Voicing " + id + " formulaId does not match chordSymbol " + symbol + ".");
            }
            String soundingBass = orEmpty(string(item, "bassNote"));
            if (!soundingBass.isEmpty()) {
                String writtenSoundingBass = NoteUtils.normalizeNoteName(soundingBass);
                if (writtenSoundingBass.isEmpty() || NoteUtils.trySemitone(writtenSoundingBass) == null) {
                    throw fail("Voicing " + id + " has an invalid bassNote.");
                }
                soundingBass = writtenSoundingBass;
            }
            String chordBass = parsed.canonicalBassNote;
            int[] frets = intArray(item.get("frets"), true, "frets");
            int[] fingers = item.get("fingers") == null
                    ? new int[6]
                    : intArray(item.get("fingers"), false, "fingers");
            int startFret = integer(item, inferStartFret(frets), "startFret", "baseFret");
            int visibleFrets = integer(item, 5, "visibleFretCount", "displayFrets");
            int difficulty = difficulty(item, 2);
            boolean simplified = bool(item, false, "isSimplified", "simplified");
            boolean barre = bool(item, detectBarre(frets, fingers), "hasBarre", "barre");
            boolean common = bool(item, true, "isCommon", "recommended", "common");
            voicings.add(new GuitarVoicingDefinition(
                    id,
                    parsed.normalizedSymbol,
                    orDefault(string(item, "name"), parsed.normalizedSymbol + " 常见按法"),
                    root,
                    formulaId,
                    soundingBass,
                    chordBass,
                    frets,
                    fingers,
                    startFret,
                    visibleFrets,
                    difficulty,
                    common,
                    simplified,
                    barre,
                    orEmpty(string(item, "description", "note")),
                    stringList(item.get("tags")),
                    stringList(item.get("omittedIntervals"))
            ));
        }
        return voicings;
    }

    private static int inferStartFret(int[] frets) {
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;
        for (int fret : frets) {
            if (fret > 0) {
                minimum = Math.min(minimum, fret);
                maximum = Math.max(maximum, fret);
            }
        }
        return maximum <= 5 || minimum == Integer.MAX_VALUE ? 1 : minimum;
    }

    private static boolean detectBarre(int[] frets, int[] fingers) {
        for (int first = 0; first < frets.length; first++) {
            if (frets[first] <= 0 || fingers[first] != 1) {
                continue;
            }
            for (int second = first + 1; second < frets.length; second++) {
                if (frets[second] == frets[first] && fingers[second] == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int difficulty(Map<String, Object> item, int fallback) {
        Object raw = first(item, "difficultyLevel", "difficulty");
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.US);
        if (value.matches("[1-5]")) {
            return Integer.parseInt(value);
        }
        if (value.contains("beginner") || value.contains("easy") || value.contains("入门")) {
            return 1;
        }
        if (value.contains("common") || value.contains("常见")) {
            return 2;
        }
        if (value.contains("medium") || value.contains("intermediate") || value.contains("中级")) {
            return 3;
        }
        if (value.contains("advanced") || value.contains("进阶")) {
            return 4;
        }
        if (value.contains("expert") || value.contains("高级")) {
            return 5;
        }
        return fallback;
    }

    private static int[] intArray(Object raw, boolean allowMuted, String field) {
        List<Object> values = array(raw, field);
        if (values.size() != 6) {
            throw fail(field + " must contain exactly six entries.");
        }
        int[] result = new int[6];
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (allowMuted && value instanceof String
                    && ("x".equalsIgnoreCase((String) value) || "-".equals(value))) {
                result[index] = -1;
            } else if (value instanceof Number) {
                result[index] = ((Number) value).intValue();
            } else {
                try {
                    result[index] = Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException exception) {
                    throw fail(field + " contains a non-integer entry at index " + index + ".");
                }
            }
            if ((!allowMuted && result[index] < 0) || (allowMuted && result[index] < -1)) {
                throw fail(field + " contains an invalid negative entry at index " + index + ".");
            }
        }
        return result;
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<Object> values = array(raw, "string array");
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                result.add(String.valueOf(value).trim());
            }
        }
        return result;
    }

    private static List<String> nullableStringList(Map<String, Object> item, String name) {
        return item.containsKey(name) ? stringList(item.get(name)) : null;
    }

    private static List<List<String>> stringGroups(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<List<String>> result = new ArrayList<>();
        for (Object value : array(raw, "requiredAnyOf")) {
            result.add(stringList(value));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object raw, String field) {
        if (!(raw instanceof Map)) {
            throw fail(field + " must be a JSON object.");
        }
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object raw, String field) {
        if (!(raw instanceof List)) {
            throw fail(field + " must be a JSON array.");
        }
        return (List<Object>) raw;
    }

    private static String requiredString(Map<String, Object> object, String... names) {
        String value = string(object, names);
        if (value == null || value.trim().isEmpty()) {
            throw fail("Missing required string field " + names[0] + ".");
        }
        return value.trim();
    }

    private static String string(Map<String, Object> object, String... names) {
        Object value = first(object, names);
        return value == null ? null : String.valueOf(value);
    }

    private static int integer(Map<String, Object> object, int fallback, String... names) {
        Object value = first(object, names);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, Object> object, boolean fallback, String... names) {
        Object value = first(object, names);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return "true".equalsIgnoreCase(text) ? true : "false".equalsIgnoreCase(text) ? false : fallback;
    }

    private static Object first(Map<String, Object> object, String... names) {
        for (String name : names) {
            if (object.containsKey(name)) {
                return object.get(name);
            }
        }
        return null;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static AssetSource classpathSource() {
        return relativePath -> {
            ClassLoader loader = ChordDataLoader.class.getClassLoader();
            InputStream stream = loader == null ? null : loader.getResourceAsStream(relativePath);
            if (stream == null && loader != null) {
                stream = loader.getResourceAsStream("assets/" + relativePath);
            }
            if (stream == null) {
                throw new IOException("Classpath resource not found: " + relativePath);
            }
            return stream;
        };
    }

    private static List<File> developmentAssetRoots() {
        List<File> roots = new ArrayList<>();
        roots.add(new File("app/src/main/assets"));
        roots.add(new File("src/main/assets"));
        return roots;
    }

    private static ChordDataRuntimeException fail(String message) {
        return new ChordDataRuntimeException(message);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private static String joinErrors(List<String> errors) {
        StringBuilder message = new StringBuilder();
        for (String error : errors) {
            if (message.length() > 0) {
                message.append(" | ");
            }
            message.append(error);
        }
        return message.toString();
    }

    public interface AssetSource {
        InputStream open(String relativePath) throws IOException;
    }

    public static final class LoadedData {
        public final int formulaSchemaVersion;
        public final int voicingSchemaVersion;
        public final String sourceDescription;
        public final ChordFormulaRepository formulas;
        public final GuitarVoicingRepository voicings;

        LoadedData(
                int formulaSchemaVersion,
                int voicingSchemaVersion,
                String sourceDescription,
                ChordFormulaRepository formulas,
                GuitarVoicingRepository voicings
        ) {
            this.formulaSchemaVersion = formulaSchemaVersion;
            this.voicingSchemaVersion = voicingSchemaVersion;
            this.sourceDescription = sourceDescription;
            this.formulas = formulas;
            this.voicings = voicings;
        }
    }

    public static final class ChordDataException extends Exception {
        public ChordDataException(String message) {
            super(message);
        }

        public ChordDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ChordDataRuntimeException extends RuntimeException {
        ChordDataRuntimeException(String message) {
            super(message);
        }
    }
}
