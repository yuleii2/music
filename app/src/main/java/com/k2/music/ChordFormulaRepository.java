package com.k2.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable, queryable collection of chord formulas and their common aliases. */
public final class ChordFormulaRepository {
    private final Map<String, ChordFormula> byId = new LinkedHashMap<>();
    private final Map<String, ChordFormula> exactAliases = new LinkedHashMap<>();
    private final Map<String, ChordFormula> foldedAliases = new LinkedHashMap<>();

    public ChordFormulaRepository(List<ChordFormula> formulas) {
        if (formulas == null || formulas.isEmpty()) {
            throw new IllegalArgumentException("At least one chord formula is required.");
        }
        for (ChordFormula formula : formulas) {
            if (formula == null) {
                continue;
            }
            if (byId.put(formula.id, formula) != null) {
                throw new IllegalArgumentException("Duplicate chord formula id: " + formula.id);
            }
        }
        for (ChordFormula formula : byId.values()) {
            registerAlias(formula.id, formula);
            registerAlias(formula.suffix, formula);
            for (String alias : formula.aliases) {
                registerAlias(alias, formula);
            }
            registerAlias(formula.chineseName, formula);
            registerAlias(formula.chineseName.replace("和弦", ""), formula);
            registerAlias(formula.englishName, formula);
            registerAlias(formula.englishName.replace(" chord", ""), formula);
            registerConventionalAliases(formula);
        }
        if (!byId.containsKey("maj")) {
            throw new IllegalArgumentException("A formula with id 'maj' is required.");
        }
        exactAliases.put("", byId.get("maj"));
        foldedAliases.put("", byId.get("maj"));
    }

    public List<ChordFormula> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public ChordFormula findById(String id) {
        return id == null ? null : byId.get(id);
    }

    public ChordFormula find(String idOrAlias) {
        ChordFormula byIdentifier = findById(idOrAlias);
        return byIdentifier != null ? byIdentifier : findBySuffixOrAlias(idOrAlias);
    }

    public ChordFormula findBySuffixOrAlias(String suffixOrAlias) {
        String token = suffixOrAlias == null ? "" : suffixOrAlias.trim();
        ChordFormula exact = exactAliases.get(token);
        if (exact != null) {
            return exact;
        }
        return foldedAliases.get(token.toLowerCase(Locale.US));
    }

    public boolean supports(String suffixOrAlias) {
        return findBySuffixOrAlias(suffixOrAlias) != null;
    }

    private void registerAlias(String alias, ChordFormula formula) {
        if (alias == null) {
            return;
        }
        String trimmed = alias.trim();
        registerAliasForm(trimmed, formula);
        String compact = trimmed.replaceAll("\\s+", "");
        if (!compact.equals(trimmed)) {
            registerAliasForm(compact, formula);
        }
    }

    private void registerAliasForm(String alias, ChordFormula formula) {
        if (!exactAliases.containsKey(alias)) {
            exactAliases.put(alias, formula);
        }
        String folded = alias.toLowerCase(Locale.US);
        if (!foldedAliases.containsKey(folded)) {
            foldedAliases.put(folded, formula);
        }
    }

    private void registerConventionalAliases(ChordFormula formula) {
        switch (formula.id) {
            case "maj":
                registerAlias("major", formula);
                registerAlias("M", formula);
                break;
            case "m":
                registerAlias("min", formula);
                registerAlias("minor", formula);
                registerAlias("-", formula);
                break;
            case "7":
                registerAlias("dom7", formula);
                registerAlias("dominant7", formula);
                break;
            case "maj7":
                registerAlias("M7", formula);
                registerAlias("major7", formula);
                registerAlias("Δ", formula);
                registerAlias("Δ7", formula);
                registerAlias("△", formula);
                registerAlias("△7", formula);
                break;
            case "m7":
                registerAlias("min7", formula);
                registerAlias("minor7", formula);
                registerAlias("-7", formula);
                break;
            case "dim":
                registerAlias("°", formula);
                break;
            case "dim7":
                registerAlias("°7", formula);
                break;
            case "m7b5":
                registerAlias("ø", formula);
                registerAlias("ø7", formula);
                registerAlias("half-diminished", formula);
                break;
            case "aug":
                registerAlias("+", formula);
                break;
            case "sus4":
                registerAlias("sus", formula);
                break;
            default:
                break;
        }
    }
}
