package com.k2.music;

public final class StringUtils {

    private StringUtils() {}

    public static String joinChinese(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    public static String joinWithSpace(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    public static String joinStringArray(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(value == null ? "X" : value);
        }
        return builder.toString();
    }

    public static String displayName(Chord chord) {
        if (chord == null) {
            return "";
        }
        if (("大三和弦".equals(chord.quality) || "小三和弦".equals(chord.quality))
                && (chord.bassNote == null || chord.bassNote.isEmpty())) {
            return chord.symbol + " " + chord.quality;
        }
        return chord.symbol;
    }

    public static String learningHint(Voicing voicing) {
        if (voicing == null) {
            return "当前和弦暂无可用吉他按法。";
        }
        String detail = voicing.description == null || voicing.description.trim().isEmpty()
                ? ""
                : "\n" + voicing.description;
        if (voicing.barre) {
            return "适合进阶练习，注意食指横按的受力均匀。" + detail;
        }
        if (voicing.simplified) {
            return "适合先建立和弦声音记忆，再过渡到完整指法。" + detail;
        }
        return "适合初学者练习，注意手指垂直按弦，避免碰到相邻琴弦。" + detail;
    }
}
