package com.k2.music;

/** Parses user-entered chord symbols without consulting guitar voicing data. */
public final class ChordNameParser {
    private final ChordFormulaRepository formulas;

    public ChordNameParser(ChordFormulaRepository formulas) {
        if (formulas == null) {
            throw new IllegalArgumentException("Formula repository must not be null.");
        }
        this.formulas = formulas;
    }

    public ParseResult parse(String rawInput) {
        String original = rawInput == null ? "" : rawInput;
        String input = clean(original);
        if (input.isEmpty()) {
            return ParseResult.error("请输入和弦名称，例如 C、Am、G7、F#maj7。", original);
        }

        ParsedNote root = parseNoteAt(input, 0);
        if (root.error != null) {
            return ParseResult.error(root.error, original);
        }
        String uncommonRootMessage = uncommonSpellingMessage(root.displayName);
        if (uncommonRootMessage != null) {
            return ParseResult.error(uncommonRootMessage, original);
        }

        int slashIndex = input.indexOf('/', root.endIndex);
        if (slashIndex >= 0 && input.indexOf('/', slashIndex + 1) >= 0) {
            return ParseResult.error("分数和弦只能包含一个斜杠，例如 C/G。", original);
        }
        String qualityToken = slashIndex < 0
                ? input.substring(root.endIndex)
                : input.substring(root.endIndex, slashIndex);
        ChordFormula formula = formulas.findBySuffixOrAlias(qualityToken);
        if (formula == null) {
            String printable = qualityToken.isEmpty() ? "(空)" : qualityToken;
            return ParseResult.error("无法识别和弦类型“" + printable + "”。请检查后缀拼写。", original);
        }

        ParsedNote bass = ParsedNote.empty(input.length());
        if (slashIndex >= 0) {
            if (slashIndex == input.length() - 1) {
                return ParseResult.error("分数和弦缺少低音，请使用类似 C/E、D/F#、G/B 的格式。", original);
            }
            bass = parseNoteAt(input, slashIndex + 1);
            if (bass.error != null || bass.endIndex != input.length()) {
                return ParseResult.error("无法识别分数和弦低音，请使用类似 C/E、D/F#、G/B 的格式。", original);
            }
            String uncommonBassMessage = uncommonSpellingMessage(bass.displayName);
            if (uncommonBassMessage != null) {
                return ParseResult.error(uncommonBassMessage, original);
            }
        } else if (root.endIndex + qualityToken.length() != input.length()) {
            return ParseResult.error("无法识别和弦名称，请检查输入。", original);
        }

        String displaySymbol = root.displayName + formula.suffix
                + (bass.displayName.isEmpty() ? "" : "/" + bass.displayName);
        String normalizedSymbol = root.canonicalName + formula.suffix
                + (bass.canonicalName.isEmpty() ? "" : "/" + bass.canonicalName);
        return ParseResult.success(
                original,
                root.displayName,
                root.canonicalName,
                formula,
                bass.displayName,
                bass.canonicalName,
                displaySymbol,
                normalizedSymbol,
                !input.equals(displaySymbol)
        );
    }

    private static ParsedNote parseNoteAt(String value, int start) {
        if (start >= value.length()) {
            return ParsedNote.error("和弦名称缺少根音。", start);
        }
        char letter = Character.toUpperCase(value.charAt(start));
        if (letter == 'H') {
            return ParsedNote.error("无法识别 H 作为根音。请使用 C、D、E、F、G、A、B。", start);
        }
        if ("ABCDEFG".indexOf(letter) < 0) {
            return ParsedNote.error("无法识别根音，请使用 C、D、E、F、G、A、B 及升降号。", start);
        }
        StringBuilder note = new StringBuilder().append(letter);
        int end = start + 1;
        if (end < value.length()) {
            char accidental = value.charAt(end);
            if (accidental == '#') {
                note.append('#');
                end++;
            } else if (accidental == 'b' || accidental == 'B') {
                note.append('b');
                end++;
            }
        }
        String display = note.toString();
        String canonical = NoteUtils.canonicalPitchClass(display);
        if (canonical.isEmpty()) {
            return ParsedNote.error("无法识别音名“" + display + "”。", start);
        }
        return ParsedNote.success(display, canonical, end);
    }

    private static String uncommonSpellingMessage(String note) {
        switch (note) {
            case "Cb":
                return "Cb 是非常见等音写法，当前版本请尝试输入 B。";
            case "B#":
                return "B# 是非常见等音写法，当前版本请尝试输入 C。";
            case "E#":
                return "E# 是非常见等音写法，当前版本请尝试输入 F。";
            case "Fb":
                return "Fb 是非常见等音写法，当前版本请尝试输入 E。";
            default:
                return null;
        }
    }

    private static String clean(String value) {
        StringBuilder cleaned = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || character == '\u3000') {
                continue;
            }
            if (character == '♯' || character == '\uFF03') {
                cleaned.append('#');
            } else if (character == '♭') {
                cleaned.append('b');
            } else {
                cleaned.append(character);
            }
        }
        return cleaned.toString();
    }

    public static final class ParseResult {
        public final boolean recognized;
        public final String input;
        public final String root;
        public final String canonicalRoot;
        public final ChordFormula formula;
        public final String formulaId;
        public final String bassNote;
        public final String canonicalBassNote;
        public final String displaySymbol;
        public final String normalizedSymbol;
        public final boolean changed;
        public final String error;

        private ParseResult(
                boolean recognized,
                String input,
                String root,
                String canonicalRoot,
                ChordFormula formula,
                String bassNote,
                String canonicalBassNote,
                String displaySymbol,
                String normalizedSymbol,
                boolean changed,
                String error
        ) {
            this.recognized = recognized;
            this.input = input;
            this.root = root;
            this.canonicalRoot = canonicalRoot;
            this.formula = formula;
            this.formulaId = formula == null ? "" : formula.id;
            this.bassNote = bassNote;
            this.canonicalBassNote = canonicalBassNote;
            this.displaySymbol = displaySymbol;
            this.normalizedSymbol = normalizedSymbol;
            this.changed = changed;
            this.error = error;
        }

        static ParseResult success(
                String input,
                String root,
                String canonicalRoot,
                ChordFormula formula,
                String bassNote,
                String canonicalBassNote,
                String displaySymbol,
                String normalizedSymbol,
                boolean changed
        ) {
            return new ParseResult(true, input, root, canonicalRoot, formula, bassNote,
                    canonicalBassNote, displaySymbol, normalizedSymbol, changed, null);
        }

        static ParseResult error(String error, String input) {
            return new ParseResult(false, input, "", "", null, "", "", "", "", false, error);
        }
    }

    private static final class ParsedNote {
        final String displayName;
        final String canonicalName;
        final int endIndex;
        final String error;

        private ParsedNote(String displayName, String canonicalName, int endIndex, String error) {
            this.displayName = displayName;
            this.canonicalName = canonicalName;
            this.endIndex = endIndex;
            this.error = error;
        }

        static ParsedNote success(String displayName, String canonicalName, int endIndex) {
            return new ParsedNote(displayName, canonicalName, endIndex, null);
        }

        static ParsedNote empty(int endIndex) {
            return new ParsedNote("", "", endIndex, null);
        }

        static ParsedNote error(String error, int endIndex) {
            return new ParsedNote("", "", endIndex, error);
        }
    }
}
