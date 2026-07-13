package com.k2.music;

import java.util.Arrays;
import java.util.Collections;

public final class SvgVoicingRendererTest {
    public static void main(String[] args) {
        run();
        System.out.println("SVG voicing renderer tests passed.");
    }

    static void run() {
        Chord chord = new Chord(
                "C<&",
                "中文 C 和弦",
                "C",
                "maj",
                "大三和弦",
                "",
                Arrays.asList("1", "3", "5"),
                Arrays.asList("C", "E", "G"),
                Collections.emptyList(),
                "测试",
                Collections.emptyList(),
                Collections.emptyList()
        );
        Voicing open = new Voicing(
                "中文 & <按法>",
                new int[]{-1, 3, 2, 0, 1, 0},
                new int[]{0, 3, 2, 0, 1, 0},
                1,
                5,
                "入门",
                true,
                false,
                false,
                "测试"
        );
        SvgExportOptions white = SvgExportOptions.builder()
                .width(640)
                .height(900)
                .showFingerNumbers(true)
                .showNoteNames(true)
                .transparentBackground(false)
                .build();
        String svg = SvgVoicingRenderer.render(chord, open, white);
        require(svg.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                "SVG should declare UTF-8.");
        require(svg.contains("width=\"640\" height=\"900\""), "Custom dimensions should be emitted.");
        require(count(svg, "data-role=\"string\"") == 6, "SVG should draw all six strings.");
        require(count(svg, "data-role=\"fret\"") == 5, "SVG should draw the requested fret grid.");
        require(svg.contains("data-role=\"nut\""), "Open-position voicings should draw a nut.");
        require(svg.contains(">X</text>") && svg.contains(">O</text>"), "Muted and open strings need X/O marks.");
        require(svg.contains("data-role=\"finger-number\""), "Finger numbers should be optional vector text.");
        require(svg.contains("data-role=\"note-name\""), "Note-name option should emit string note labels.");
        require(svg.contains("中文 &amp; &lt;按法&gt;"), "UTF-8 text should be XML escaped without loss.");
        require(svg.contains("data-role=\"background\""), "White background mode should emit a background rect.");
        require(!svg.contains("<image") && !svg.contains("base64"), "SVG must not contain a bitmap payload.");

        SvgExportOptions transparent = white.toBuilder()
                .showFingerNumbers(false)
                .showNoteNames(false)
                .transparentBackground(true)
                .build();
        String minimal = SvgVoicingRenderer.render(chord, open, transparent);
        require(!minimal.contains("data-role=\"background\""), "Transparent mode should omit the white rect.");
        require(!minimal.contains("data-role=\"finger-number\""), "Finger numbers should be suppressible.");
        require(!minimal.contains("data-role=\"note-name\""), "Note names should be suppressible.");

        Voicing high = new Voicing(
                "C 大横按",
                new int[]{8, 10, 10, 9, 8, 8},
                new int[]{1, 3, 4, 2, 1, 1},
                8,
                4,
                "进阶",
                true,
                false,
                true,
                "测试"
        );
        String highSvg = SvgVoicingRenderer.render(chord, high, SvgExportOptions.defaults());
        require(highSvg.contains("data-role=\"start-fret\"") && highSvg.contains("8品"),
                "High-position voicings should label the starting fret.");
        require(highSvg.contains("data-role=\"barre\""), "Repeated first-finger notes should render a barre.");
    }

    private static int count(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
