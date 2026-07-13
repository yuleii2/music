package com.k2.music;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VoicingImageExporter {
    public static final String FORMAT_JPG = "jpg";
    public static final String FORMAT_PNG = "png";
    public static final String FORMAT_SVG = "svg";

    private VoicingImageExporter() {
    }

    public static ExportSummary export(
            Context context,
            Uri folderUri,
            String baseName,
            String format,
            List<ExportItem> items
    ) {
        if (FORMAT_SVG.equalsIgnoreCase(format)) {
            return VoicingSvgExporter.export(context, folderUri, baseName, items);
        }
        ExportSummary summary = new ExportSummary();
        if (context == null || folderUri == null || items == null || items.isEmpty()) {
            return summary;
        }
        ContentResolver resolver = context.getContentResolver();
        String extension = extension(format);
        String mimeType = mimeType(format);
        Bitmap.CompressFormat compressFormat = compressFormat(format);
        String safeBase = sanitizeFileName(baseName);
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
            ExportItem item = items.get(itemIndex);
            if (item == null || item.chord == null || item.voicing == null) {
                summary.failed++;
                continue;
            }
            String fileName = buildFileName(safeBase, item, extension);
            Uri documentUri = null;
            boolean completed = false;
            try {
                documentUri = DocumentsContract.createDocument(resolver, parentDocumentUri, mimeType, fileName);
                if (documentUri == null) {
                    summary.failed++;
                    continue;
                }
                Bitmap bitmap = render(context, item.chord, item.voicing);
                try (OutputStream outputStream = resolver.openOutputStream(documentUri)) {
                    if (outputStream == null || !bitmap.compress(compressFormat, 94, outputStream)) {
                        summary.failed++;
                    } else {
                        summary.exported++;
                        summary.fileNames.add(fileName);
                        completed = true;
                    }
                } finally {
                    bitmap.recycle();
                }
            } catch (RuntimeException | IOException exception) {
                summary.failed++;
            } finally {
                if (!completed && documentUri != null) deleteQuietly(resolver, documentUri);
            }
        }
        return summary;
    }

    static Bitmap render(Context context, Chord chord, Voicing voicing) {
        int width = 1200;
        int height = 2400;
        float scale = width / 400f;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        canvas.drawColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF101412);
        paint.setFakeBoldText(true);
        paint.setTextSize(44 * scale);
        canvas.drawText("吉他和弦字典", 30 * scale, 50 * scale, paint);

        paint.setTextSize(30 * scale);
        canvas.drawText(chord.symbol + "  " + voicing.name, 30 * scale, 92 * scale, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(15 * scale);
        paint.setColor(0xFF58605B);
        drawWrappedText(canvas, paint, summaryText(chord), 30 * scale, 120 * scale, 340 * scale, 22 * scale, 2);
        drawWrappedText(canvas, paint, "组成音：" + join(chord.notes) + "    音程结构：" + join(chord.intervals),
                30 * scale, 170 * scale, 340 * scale, 22 * scale, 3);

        drawBadge(canvas, paint, "按法 " + voicing.fretPattern(), 30 * scale, 218 * scale, scale);
        drawBadge(canvas, paint, "难度 " + voicing.difficulty, 172 * scale, 218 * scale, scale);
        drawBadge(canvas, paint, voicing.recommended ? "推荐" : "候选", 270 * scale, 218 * scale, scale);

        RectF board = new RectF(22 * scale, 260 * scale, 378 * scale, 520 * scale);
        FretboardDiagramRenderer.draw(canvas, board, voicing, scale, true, paint);
        // The shared fretboard renderer uses centered text for fret/string labels.
        // Restore left alignment before drawing the document's prose section.
        paint.setTextAlign(Paint.Align.LEFT);

        RectF info = new RectF(22 * scale, 542 * scale, 378 * scale, 760 * scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF3F4F6);
        canvas.drawRoundRect(info, 18 * scale, 18 * scale, paint);

        paint.setColor(0xFF101412);
        paint.setFakeBoldText(true);
        paint.setTextSize(20 * scale);
        canvas.drawText("指法说明", info.left + 18 * scale, info.top + 34 * scale, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(15 * scale);
        paint.setColor(0xFF303833);
        float y = info.top + 68 * scale;
        y = drawWrappedText(canvas, paint, "实际弦音：" + joinStringArray(voicing.stringNotes),
                info.left + 18 * scale, y, 320 * scale, 22 * scale, 2);
        drawWrappedText(canvas, paint, voicing.description,
                info.left + 18 * scale, y + 10 * scale, 320 * scale, 22 * scale, 4);

        paint.setColor(0xFF8A928D);
        paint.setTextSize(12 * scale);
        canvas.drawText(context.getString(R.string.app_name) + " / " + chord.symbol,
                30 * scale, height - 24 * scale, paint);
        return bitmap;
    }

    private static void drawBadge(Canvas canvas, Paint paint, String text, float x, float y, float scale) {
        paint.setTextSize(13 * scale);
        paint.setFakeBoldText(true);
        float width = paint.measureText(text) + 22 * scale;
        RectF rect = new RectF(x, y, x + width, y + 30 * scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF1F2F4);
        canvas.drawRoundRect(rect, 10 * scale, 10 * scale, paint);
        paint.setColor(0xFF111513);
        canvas.drawText(text, x + 11 * scale, y + 20 * scale, paint);
        paint.setFakeBoldText(false);
    }

    private static float drawWrappedText(
            Canvas canvas,
            Paint paint,
            String text,
            float x,
            float y,
            float maxWidth,
            float lineHeight,
            int maxLines
    ) {
        int index = 0;
        int line = 0;
        while (index < text.length() && line < maxLines) {
            int count = paint.breakText(text, index, text.length(), true, maxWidth, null);
            if (count <= 0) {
                break;
            }
            int next = index + count;
            canvas.drawText(text, index, next, x, y, paint);
            y += lineHeight;
            index = next;
            line++;
        }
        return y;
    }

    static String buildFileName(String safeBase, ExportItem item, String extension) {
        return safeBase
                + "-"
                + sanitizeFileName(item.chord.symbol)
                + "-"
                + String.format(Locale.US, "%02d", item.index + 1)
                + "-"
                + sanitizeFileName(item.voicing.name)
                + "."
                + extension;
    }

    static String sanitizeFileName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            trimmed = "guitar-chords";
        }
        String cleaned = trimmed.replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        if (cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? "guitar-chords" : cleaned;
    }

    private static String extension(String format) {
        return FORMAT_PNG.equalsIgnoreCase(format) ? FORMAT_PNG : FORMAT_JPG;
    }

    private static String mimeType(String format) {
        return FORMAT_PNG.equalsIgnoreCase(format) ? "image/png" : "image/jpeg";
    }

    private static Bitmap.CompressFormat compressFormat(String format) {
        return FORMAT_PNG.equalsIgnoreCase(format) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
    }

    private static void deleteQuietly(ContentResolver resolver, Uri documentUri) {
        try {
            DocumentsContract.deleteDocument(resolver, documentUri);
        } catch (RuntimeException | IOException ignored) {
            // The provider may not support deletion; preserve the original failure count.
        }
    }

    private static String summaryText(Chord chord) {
        if (chord.bassNote != null && !chord.bassNote.isEmpty()) {
            return "根音：" + chord.root + "    低音：" + chord.bassNote + "    类型：" + chord.quality;
        }
        return "根音：" + chord.root + "    类型：" + chord.quality;
    }

    private static String join(Iterable<String> values) {
        return StringUtils.joinChinese(values);
    }

    private static String joinStringArray(String[] values) {
        return StringUtils.joinStringArray(values);
    }

    public static final class ExportItem {
        public final Chord chord;
        public final Voicing voicing;
        public final int index;

        public ExportItem(Chord chord, Voicing voicing, int index) {
            this.chord = chord;
            this.voicing = voicing;
            this.index = index;
        }
    }

    public static final class ExportSummary {
        public int exported;
        public int failed;
        public final List<String> fileNames = new ArrayList<>();
    }
}
