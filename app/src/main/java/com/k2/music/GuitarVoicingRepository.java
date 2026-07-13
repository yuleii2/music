package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Query layer over the recorded guitar shapes loaded from JSON. */
public final class GuitarVoicingRepository {
    private final List<GuitarVoicingDefinition> definitions;
    private final List<ChordShape> shapes;
    private final Map<String, List<GuitarVoicingDefinition>> bySymbol = new LinkedHashMap<>();
    private final ChordNameParser parser;

    public GuitarVoicingRepository(
            ChordFormulaRepository formulas,
            List<GuitarVoicingDefinition> definitions
    ) {
        if (formulas == null) {
            throw new IllegalArgumentException("Formula repository must not be null.");
        }
        this.parser = new ChordNameParser(formulas);
        List<GuitarVoicingDefinition> definitionCopy = new ArrayList<>();
        List<ChordShape> shapeCopy = new ArrayList<>();
        if (definitions != null) {
            for (GuitarVoicingDefinition definition : definitions) {
                if (definition == null) {
                    continue;
                }
                if (formulas.findById(definition.formulaId) == null) {
                    throw new IllegalArgumentException(
                            "Voicing " + definition.id + " references unknown formula " + definition.formulaId
                    );
                }
                definitionCopy.add(definition);
                shapeCopy.add(definition.toChordShape());
                String key = canonicalKey(definition.chordSymbol);
                List<GuitarVoicingDefinition> symbolVoicings = bySymbol.get(key);
                if (symbolVoicings == null) {
                    symbolVoicings = new ArrayList<>();
                    bySymbol.put(key, symbolVoicings);
                }
                symbolVoicings.add(definition);
            }
        }
        this.definitions = Collections.unmodifiableList(definitionCopy);
        this.shapes = Collections.unmodifiableList(shapeCopy);
    }

    public List<GuitarVoicingDefinition> getAll() {
        return definitions;
    }

    public List<ChordShape> getAllShapes() {
        return shapes;
    }

    public List<GuitarVoicingDefinition> findByChordSymbol(String chordSymbol) {
        List<GuitarVoicingDefinition> matches = bySymbol.get(canonicalKey(chordSymbol));
        if (matches == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(matches));
    }

    public List<GuitarVoicingDefinition> getVoicings(String chordSymbol) {
        return findByChordSymbol(chordSymbol);
    }

    public List<ChordShape> getShapes(String chordSymbol) {
        List<ChordShape> result = new ArrayList<>();
        for (GuitarVoicingDefinition definition : findByChordSymbol(chordSymbol)) {
            result.add(definition.toChordShape());
        }
        return Collections.unmodifiableList(result);
    }

    public boolean hasVoicings(String chordSymbol) {
        return !findByChordSymbol(chordSymbol).isEmpty();
    }

    private String canonicalKey(String symbol) {
        ChordNameParser.ParseResult result = parser.parse(symbol);
        return result.recognized ? result.normalizedSymbol : symbol;
    }
}
