package com.k2.music;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class VoicingSvgExporter {
    private static final String MIME_TYPE = "image/svg+xml";
    private static final int STRING_COUNT = 6;

    private VoicingSvgExporter() {
    }

    public static VoicingImageExporter.ExportSummary export(
            Context context,
            Uri folderUri,
            String baseName,
            List<VoicingImageExporter.ExportItem> items
    ) {
        VoicingImageExporter.ExportSummary summary = new VoicingImageExporter.ExportSummary();
        if (context == null || folderUri == null || items == null || items.isEmpty()) {
            return summary;
        }

        ContentResolver resolver = context.getContentResolver();
        String safeBase = VoicingImageExporter.sanitizeFileName(baseName);
        Uri parentDocumentUri;
        try {
            parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                    folderUri,
                    DocumentsContract.getTreeDocumentId(folderUri)
            );
        } catch (RuntimeException exception) {
            summary.failed = items.size();
            return summary;
        }

        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            if (Thread.currentThread().isInterrupted()) {
                summary.failed += items.size() - itemIndex;
                break;
            }
            VoicingImageExporter.ExportItem item = items.get(itemIndex);
            if (item == null || item.chord == null || item.voicing == null) {
                summary.failed++;
                continue;
            }
            String fileName = VoicingImageExporter.buildFileName(safeBase, item, VoicingImageExporter.FORMAT_SVG);
            Uri documentUri = null;
            boolean completed = false;
            try {
                documentUri = DocumentsContract.createDocument(resolver, parentDocumentUri, MIME_TYPE, fileName);
                if (documentUri == null) {
                    summary.failed++;
                    continue;
                }
                String svg = buildSvg(context, item.chord, item.voicing);
                try (OutputStream outputStream = resolver.openOutputStream(documentUri)) {
                    if (outputStream == null) {
                        summary.failed++;
                        continue;
                    } else {
                        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                        writer.write(svg);
                        writer.flush();
                    }
                }
                summary.exported++;
                summary.fileNames.add(fileName);
                completed = true;
            } catch (RuntimeException | IOException exception) {
                summary.failed++;
            } finally {
                if (!completed && documentUri != null) deleteQuietly(resolver, documentUri);
            }
        }
        return summary;
    }

    static String buildSvg(Context context, Chord chord, Voicing voicing) {
        SvgExportOptions options = SvgExportOptions.builder()
                .width(400)
                .height(800)
                .showFingerNumbers(true)
                .showNoteNames(true)
                .transparentBackground(false)
                .build();
        return SvgVoicingRenderer.render(chord, voicing, options);
    }

    public static String escapeXml(String value) {
        return SvgVoicingRenderer.escapeXml(value);
    }

    private static void deleteQuietly(ContentResolver resolver, Uri documentUri) {
        try {
            DocumentsContract.deleteDocument(resolver, documentUri);
        } catch (RuntimeException | IOException ignored) {
            // The provider may not support deletion; preserve the original failure count.
        }
    }

    private static void drawFretboard(StringBuilder svg, Voicing voicing, float left, float top, float right, float bottom) {
        float boardLeft = left + 38;
        float boardRight = right - 20;
        float boardTop = top + 42;
        float boardBottom = bottom - 16;
        int visibleFrets = Math.max(5, voicing.displayFrets);
        float stringGap = (boardRight - boardLeft) / (STRING_COUNT - 1);
        float fretGap = (boardBottom - boardTop) / visibleFrets;

        append(svg, "<rect x=\"" + n(left) + "\" y=\"" + n(top) + "\" width=\"" + n(right - left)
                + "\" height=\"" + n(bottom - top) + "\" rx=\"18\" fill=\"#F8F9F8\" stroke=\"#E1E4E2\"/>");

        if (voicing.startFret > 1) {
            text(svg, voicing.startFret + "fr", boardLeft - 24, boardTop - 16, "fret-number", "end", null);
        }

        for (int fret = 0; fret <= visibleFrets; fret++) {
            float y = boardTop + fret * fretGap;
            boolean nut = fret == 0 && voicing.startFret == 1;
            line(svg, boardLeft, y, boardRight, y, nut ? "#101412" : "#B9C0BC", nut ? 3f : 1.2f);
        }

        for (int fret = 1; fret <= visibleFrets; fret++) {
            float y = boardTop + (fret - 0.5f) * fretGap + 4;
            text(svg, String.valueOf(voicing.startFret + fret - 1), boardLeft - 14, y, "fret-number", "end", null);
        }

        for (int string = 0; string < STRING_COUNT; string++) {
            float x = boardLeft + string * stringGap;
            line(svg, x, boardTop, x, boardBottom, "#56615B", 1.2f);
        }

        for (int string = 0; string < STRING_COUNT; string++) {
            float x = boardLeft + string * stringGap;
            int fret = voicing.frets[string];
            if (fret == Voicing.MUTED) {
                text(svg, "x", x, boardTop - 20, "mark", "middle", null);
            } else if (fret == 0) {
                text(svg, "o", x, boardTop - 20, "mark", "middle", null);
            }
        }

        drawBarres(svg, voicing, boardLeft, boardTop, fretGap, stringGap, visibleFrets);
        drawFingerMarkers(svg, voicing, boardLeft, boardTop, fretGap, stringGap, visibleFrets);
    }

    private static void drawBarres(
            StringBuilder svg,
            Voicing voicing,
            float left,
            float top,
            float fretGap,
            float stringGap,
            int visibleFrets
    ) {
        for (int fret = voicing.startFret; fret < voicing.startFret + visibleFrets; fret++) {
            if (voicing.barre) {
                int barreStart = -1;
                int barreEnd = -1;
                for (int string = 0; string < STRING_COUNT; string++) {
                    if (voicing.frets[string] == fret && voicing.fingers[string] == 1) {
                        if (barreStart < 0) {
                            barreStart = string;
                        }
                        barreEnd = string;
                    }
                }
                if (barreStart >= 0 && barreEnd > barreStart) {
                    drawBarreRun(svg, voicing, barreStart, barreEnd, fret, left, top, fretGap, stringGap);
                    continue;
                }
            }
            int start = -1;
            int end = -1;
            for (int string = 0; string < STRING_COUNT; string++) {
                if (voicing.frets[string] == fret && voicing.fingers[string] == 1) {
                    if (start < 0) {
                        start = string;
                    }
                    end = string;
                } else {
                    drawBarreRun(svg, voicing, start, end, fret, left, top, fretGap, stringGap);
                    start = -1;
                    end = -1;
                }
            }
            drawBarreRun(svg, voicing, start, end, fret, left, top, fretGap, stringGap);
        }
    }

    private static void drawBarreRun(
            StringBuilder svg,
            Voicing voicing,
            int start,
            int end,
            int fret,
            float left,
            float top,
            float fretGap,
            float stringGap
    ) {
        if (start < 0 || end <= start) {
            return;
        }
        float visibleFret = fret - voicing.startFret + 1;
        float y = top + (visibleFret - 0.5f) * fretGap;
        float x1 = left + start * stringGap;
        float x2 = left + end * stringGap;
        float height = Math.min(28, fretGap * 0.48f);
        append(svg, "<rect x=\"" + n(x1 - height * 0.5f)
                + "\" y=\"" + n(y - height * 0.5f)
                + "\" width=\"" + n((x2 - x1) + height)
                + "\" height=\"" + n(height)
                + "\" rx=\"" + n(height * 0.5f)
                + "\" fill=\"#101412\"/>");
        text(svg, "1", x1, y + 5, "finger", "middle", null);
    }

    private static void drawFingerMarkers(
            StringBuilder svg,
            Voicing voicing,
            float left,
            float top,
            float fretGap,
            float stringGap,
            int visibleFrets
    ) {
        for (int string = 0; string < STRING_COUNT; string++) {
            int fret = voicing.frets[string];
            if (fret <= 0) {
                continue;
            }
            float visibleFret = fret - voicing.startFret + 1;
            if (visibleFret < 1 || visibleFret > visibleFrets || isCoveredByBarre(voicing, string)) {
                continue;
            }
            float x = left + string * stringGap;
            float y = top + (visibleFret - 0.5f) * fretGap;
            append(svg, "<circle cx=\"" + n(x) + "\" cy=\"" + n(y) + "\" r=\"15\" fill=\"#101412\"/>");
            int finger = voicing.fingers[string];
            if (finger > 0) {
                text(svg, String.valueOf(finger), x, y + 5, "finger", "middle", null);
            }
        }
    }

    private static boolean isCoveredByBarre(Voicing voicing, int stringIndex) {
        int fret = voicing.frets[stringIndex];
        if (fret <= 0 || voicing.fingers[stringIndex] != 1) {
            return false;
        }
        if (voicing.barre) {
            int count = 0;
            for (int i = 0; i < STRING_COUNT; i++) {
                if (voicing.frets[i] == fret && voicing.fingers[i] == 1) {
                    count++;
                }
            }
            return count > 1;
        }
        int runLength = 1;
        for (int i = stringIndex - 1; i >= 0; i--) {
            if (voicing.frets[i] == fret && voicing.fingers[i] == 1) {
                runLength++;
            } else {
                break;
            }
        }
        for (int i = stringIndex + 1; i < STRING_COUNT; i++) {
            if (voicing.frets[i] == fret && voicing.fingers[i] == 1) {
                runLength++;
            } else {
                break;
            }
        }
        return runLength > 1;
    }

    private static void drawInfoPanel(StringBuilder svg, Context context, Chord chord, Voicing voicing) {
        append(svg, "<rect x=\"24\" y=\"540\" width=\"352\" height=\"220\" rx=\"18\" fill=\"#F3F4F6\"/>");
        text(svg, "和弦信息", 42, 574, "subtitle", "start", null);

        int y = 604;
        y = infoRow(svg, "根音", chord.root, y);
        if (chord.bassNote != null && !chord.bassNote.isEmpty()) {
            y = infoRow(svg, "低音", chord.bassNote, y);
        }
        y = infoRow(svg, "类型", chord.quality, y);
        y = infoRow(svg, "组成音", StringUtils.joinWithSpace(chord.notes), y);
        y = infoRow(svg, "音程结构", StringUtils.joinWithSpace(chord.intervals), y);
        y = infoRow(svg, "实际弦音", StringUtils.joinStringArray(voicing.stringNotes), y);

        text(svg, "指法说明", 42, y + 10, "badge", "start", null);
        for (String line : wrapText(nullToEmpty(voicing.description), 24, 3)) {
            y += 22;
            text(svg, line, 42, y + 10, "body", "start", null);
        }

        String appName = context == null ? "吉他和弦工作室" : context.getString(R.string.app_name);
        text(svg, appName + " / " + chord.symbol, 28, 786, "muted", "start", null);
    }

    private static int infoRow(StringBuilder svg, String label, String value, int y) {
        text(svg, label, 42, y, "badge", "start", null);
        text(svg, value, 112, y, "body", "start", null);
        return y + 24;
    }

    private static void drawBadge(StringBuilder svg, String text, float x, float y) {
        float width = Math.max(74, Math.min(128, 18 + text.length() * 8));
        append(svg, "<rect x=\"" + n(x) + "\" y=\"" + n(y)
                + "\" width=\"" + n(width) + "\" height=\"28\" rx=\"10\" fill=\"#F1F2F4\"/>");
        text(svg, text, x + 10, y + 19, "badge", "start", null);
    }

    private static void line(StringBuilder svg, float x1, float y1, float x2, float y2, String color, float strokeWidth) {
        append(svg, "<line x1=\"" + n(x1) + "\" y1=\"" + n(y1)
                + "\" x2=\"" + n(x2) + "\" y2=\"" + n(y2)
                + "\" stroke=\"" + color + "\" stroke-width=\"" + n(strokeWidth)
                + "\" stroke-linecap=\"round\"/>");
    }

    private static void text(StringBuilder svg, String value, float x, float y, String cssClass, String anchor, String extraAttributes) {
        append(svg, "<text x=\"" + n(x)
                + "\" y=\"" + n(y)
                + "\" class=\"" + cssClass
                + "\" text-anchor=\"" + anchor
                + "\""
                + (extraAttributes == null ? "" : " " + extraAttributes)
                + ">"
                + escapeXml(value)
                + "</text>");
    }

    private static String summaryText(Chord chord) {
        if (chord.bassNote != null && !chord.bassNote.isEmpty()) {
            return "根音：" + chord.root + "    低音：" + chord.bassNote + "    类型：" + chord.quality;
        }
        return "根音：" + chord.root + "    类型：" + chord.quality;
    }

    private static List<String> wrapText(String text, int maxChars, int maxLines) {
        List<String> lines = new ArrayList<>();
        String source = nullToEmpty(text).replace('\n', ' ').trim();
        int index = 0;
        while (index < source.length() && lines.size() < maxLines) {
            int end = Math.min(source.length(), index + maxChars);
            lines.add(source.substring(index, end));
            index = end;
        }
        if (lines.isEmpty()) {
            lines.add("暂无指法说明。");
        }
        return lines;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String n(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static void append(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }
}
