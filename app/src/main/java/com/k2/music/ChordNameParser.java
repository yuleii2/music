package com.k2.music;

/**
 * Backward-compatible name retained for saved integrations and older callers.
 * New code should use {@link ChordSymbolParser}; both routes share one parser.
 */
@Deprecated
public final class ChordNameParser extends ChordSymbolParser {
    public ChordNameParser(ChordFormulaRepository formulas) {
        super(formulas);
    }
}
