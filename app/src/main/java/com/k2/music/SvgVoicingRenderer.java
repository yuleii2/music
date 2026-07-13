package com.k2.music;

import java.util.Locale;

/** Builds an SVG from voicing coordinates directly; no bitmap is involved. */
public final class SvgVoicingRenderer {
    private static final int STRING_COUNT = 6;

    private SvgVoicingRenderer() {
    }

    public static String render(Chord chord, Voicing voicing) {
        return render(chord, voicing, SvgExportOptions.defaults());
    }

    public static String render(Chord chord, Voicing voicing, SvgExportOptions options) {
        if (chord == null || voicing == null || options == null) {
            throw new IllegalArgumentException("Chord, voicing, and SVG options are required.");
        }
        Geometry geometry = new Geometry(options, voicing);
        StringBuilder svg = new StringBuilder(8000);
        line(svg, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        line(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + options.width
                + "\" height=\"" + options.height + "\" viewBox=\"0 0 " + options.width + " "
                + options.height + "\" role=\"img\" aria-label=\""
                + escapeXml(chord.symbol + " " + voicing.name) + "\">");
        line(svg, "<title>" + escapeXml(chord.symbol + " " + voicing.name) + "</title>");
        if (!options.transparentBackground) {
            line(svg, "<rect data-role=\"background\" width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>");
        }
        line(svg, "<g data-role=\"voicing-diagram\" font-family=\"sans-serif\" fill=\"#101412\">");
        text(svg, chord.symbol, options.width / 2f, geometry.titleY, geometry.titleSize,
                "middle", "700", "chord-title");
        text(svg, voicing.name, options.width / 2f, geometry.subtitleY, geometry.subtitleSize,
                "middle", "400", "voicing-title");

        drawPositionMarks(svg, voicing, geometry);
        drawFretboard(svg, voicing, geometry);
        drawBarres(svg, voicing, geometry);
        drawDots(svg, voicing, options, geometry);
        if (options.showNoteNames) {
            drawNoteNames(svg, voicing, geometry);
        }
        line(svg, "</g>");
        line(svg, "</svg>");
        return svg.toString();
    }

    public static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                default:
                    escaped.append(character);
                    break;
            }
        }
        return escaped.toString();
    }

    private static void drawPositionMarks(StringBuilder svg, Voicing voicing, Geometry geometry) {
        for (int string = 0; string < STRING_COUNT; string++) {
            float x = geometry.stringX(string);
            int fret = voicing.frets[string];
            if (fret == Voicing.MUTED) {
                text(svg, "X", x, geometry.markY, geometry.markSize, "middle", "700", "muted-string");
            } else if (fret == 0) {
                text(svg, "O", x, geometry.markY, geometry.markSize, "middle", "700", "open-string");
            }
        }
        if (voicing.startFret > 1) {
            text(svg, voicing.startFret + "品", geometry.boardLeft - geometry.labelGap,
                    geometry.boardTop + geometry.fretGap * 0.62f,
                    geometry.labelSize, "end", "400", "start-fret");
        }
    }

    private static void drawFretboard(StringBuilder svg, Voicing voicing, Geometry geometry) {
        for (int fret = 0; fret <= geometry.visibleFrets; fret++) {
            float y = geometry.boardTop + geometry.fretGap * fret;
            boolean nut = fret == 0 && voicing.startFret <= 1;
            lineElement(svg, geometry.boardLeft, y, geometry.boardRight, y,
                    nut ? Math.max(3f, geometry.stroke * 3f) : geometry.stroke,
                    nut ? "nut" : "fret");
        }
        for (int string = 0; string < STRING_COUNT; string++) {
            float x = geometry.stringX(string);
            lineElement(svg, x, geometry.boardTop, x, geometry.boardBottom, geometry.stroke, "string");
        }
    }

    private static void drawBarres(StringBuilder svg, Voicing voicing, Geometry geometry) {
        for (int fret = Math.max(1, voicing.startFret);
             fret < voicing.startFret + geometry.visibleFrets;
             fret++) {
            int first = -1;
            int last = -1;
            for (int string = 0; string < STRING_COUNT; string++) {
                if (voicing.frets[string] == fret && voicing.fingers[string] == 1) {
                    if (first < 0) {
                        first = string;
                    }
                    last = string;
                }
            }
            if (first >= 0 && last > first) {
                float y = geometry.fretCenter(fret);
                float x1 = geometry.stringX(first);
                float x2 = geometry.stringX(last);
                float radius = geometry.dotRadius;
                line(svg, "<rect data-role=\"barre\" x=\"" + number(x1 - radius)
                        + "\" y=\"" + number(y - radius) + "\" width=\""
                        + number((x2 - x1) + radius * 2f) + "\" height=\"" + number(radius * 2f)
                        + "\" rx=\"" + number(radius) + "\" fill=\"#101412\"/>");
            }
        }
    }

    private static void drawDots(
            StringBuilder svg,
            Voicing voicing,
            SvgExportOptions options,
            Geometry geometry
    ) {
        for (int string = 0; string < STRING_COUNT; string++) {
            int fret = voicing.frets[string];
            if (fret <= 0 || !geometry.isVisible(fret)) {
                continue;
            }
            float x = geometry.stringX(string);
            float y = geometry.fretCenter(fret);
            line(svg, "<circle data-role=\"finger-dot\" data-string=\"" + (string + 1)
                    + "\" data-fret=\"" + fret + "\" cx=\"" + number(x) + "\" cy=\""
                    + number(y) + "\" r=\"" + number(geometry.dotRadius) + "\" fill=\"#101412\"/>");
            int finger = voicing.fingers[string];
            if (options.showFingerNumbers && finger > 0) {
                textWithFill(svg, String.valueOf(finger), x, y + geometry.fingerSize * 0.34f,
                        geometry.fingerSize, "middle", "700", "finger-number", "#FFFFFF");
            }
        }
    }

    private static void drawNoteNames(StringBuilder svg, Voicing voicing, Geometry geometry) {
        for (int string = 0; string < STRING_COUNT; string++) {
            String note = voicing.stringNotes[string];
            if (note == null || note.isEmpty()) {
                continue;
            }
            text(svg, note, geometry.stringX(string), geometry.noteY, geometry.noteSize,
                    "middle", "400", "note-name");
        }
    }

    private static void lineElement(
            StringBuilder svg,
            float x1,
            float y1,
            float x2,
            float y2,
            float strokeWidth,
            String role
    ) {
        line(svg, "<line data-role=\"" + role + "\" x1=\"" + number(x1) + "\" y1=\""
                + number(y1) + "\" x2=\"" + number(x2) + "\" y2=\"" + number(y2)
                + "\" stroke=\"#3C4540\" stroke-width=\"" + number(strokeWidth)
                + "\" stroke-linecap=\"round\"/>");
    }

    private static void text(
            StringBuilder svg,
            String value,
            float x,
            float y,
            float size,
            String anchor,
            String weight,
            String role
    ) {
        textWithFill(svg, value, x, y, size, anchor, weight, role, "#101412");
    }

    private static void textWithFill(
            StringBuilder svg,
            String value,
            float x,
            float y,
            float size,
            String anchor,
            String weight,
            String role,
            String fill
    ) {
        line(svg, "<text data-role=\"" + role + "\" x=\"" + number(x) + "\" y=\""
                + number(y) + "\" text-anchor=\"" + anchor + "\" font-size=\"" + number(size)
                + "\" font-weight=\"" + weight + "\" fill=\"" + fill + "\">"
                + escapeXml(value) + "</text>");
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static void line(StringBuilder svg, String line) {
        svg.append(line).append('\n');
    }

    private static final class Geometry {
        final float boardLeft;
        final float boardRight;
        final float boardTop;
        final float boardBottom;
        final float stringGap;
        final float fretGap;
        final float titleY;
        final float subtitleY;
        final float markY;
        final float noteY;
        final float titleSize;
        final float subtitleSize;
        final float markSize;
        final float labelSize;
        final float noteSize;
        final float fingerSize;
        final float dotRadius;
        final float stroke;
        final float labelGap;
        final int visibleFrets;
        final int startFret;

        Geometry(SvgExportOptions options, Voicing voicing) {
            float width = options.width;
            float height = options.height;
            boardLeft = width * 0.18f;
            boardRight = width * 0.86f;
            boardTop = height * 0.24f;
            boardBottom = height * (options.showNoteNames ? 0.80f : 0.86f);
            stringGap = (boardRight - boardLeft) / (STRING_COUNT - 1);
            visibleFrets = Math.max(4, voicing.displayFrets);
            fretGap = (boardBottom - boardTop) / visibleFrets;
            titleY = height * 0.08f;
            subtitleY = height * 0.135f;
            markY = boardTop - Math.max(12f, height * 0.025f);
            noteY = Math.min(height - 10f, boardBottom + Math.max(24f, height * 0.065f));
            titleSize = clamp(width * 0.09f, 18f, 42f);
            subtitleSize = clamp(width * 0.045f, 10f, 22f);
            markSize = clamp(width * 0.05f, 12f, 24f);
            labelSize = clamp(width * 0.035f, 9f, 16f);
            noteSize = clamp(width * 0.035f, 9f, 16f);
            fingerSize = clamp(Math.min(stringGap, fretGap) * 0.32f, 9f, 18f);
            dotRadius = clamp(Math.min(stringGap, fretGap) * 0.30f, 7f, 18f);
            stroke = clamp(width / 400f, 0.8f, 2.5f);
            labelGap = Math.max(8f, width * 0.025f);
            startFret = Math.max(1, voicing.startFret);
        }

        float stringX(int string) {
            return boardLeft + stringGap * string;
        }

        boolean isVisible(int absoluteFret) {
            return absoluteFret >= startFret && absoluteFret < startFret + visibleFrets;
        }

        float fretCenter(int absoluteFret) {
            int relative = absoluteFret - startFret;
            return boardTop + (relative + 0.5f) * fretGap;
        }

        private static float clamp(float value, float minimum, float maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
