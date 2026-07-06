package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChordRepository {
    private final Map<String, Chord> chordsBySymbol = new LinkedHashMap<>();
    private final Map<String, Chord> chordsByAlias = new LinkedHashMap<>();

    public ChordRepository() {
        registerAll();
    }

    public LookupResult find(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) {
            return LookupResult.error("请输入和弦名称，例如 C、Am、G7、Fmaj7。");
        }
        NormalizedInput normalized = normalize(input);
        if (normalized.error != null) {
            return LookupResult.error(normalized.error);
        }
        Chord chord = chordsByAlias.get(normalized.symbol);
        if (chord == null) {
            return LookupResult.error("当前版本暂不支持该和弦类型，请先尝试 C、Am、G7、Fmaj7 等常见和弦。");
        }
        return LookupResult.success(chord, normalized.changed ? "已将输入规范化为 " + chord.symbol + "。" : null);
    }

    public List<String> examples() {
        return Arrays.asList("C", "Am", "G7", "Fmaj7", "C9", "G/B");
    }

    public List<Chord> allChords() {
        return new ArrayList<>(chordsBySymbol.values());
    }

    private void registerAll() {
        register(new Chord(
                "C", "C 大三和弦", "C", "大三和弦",
                list("1", "3", "5"), list("C", "E", "G"), list("Cmaj", "Cmajor"),
                "C 大三和弦由根音 C、大三度 E 和纯五度 G 构成，听感明亮、稳定，是吉他学习中最常见的基础和弦之一。",
                list(
                        voicing("开放 C 和弦", a(-1, 3, 2, 0, 1, 0), a(0, 3, 2, 0, 1, 0), 1, 4, "入门", true, false, false,
                                "第 6 弦不弹，第 5 弦 3 品、第 4 弦 2 品、第 2 弦 1 品，其余为有效空弦。"),
                        voicing("C 大横按", a(8, 10, 10, 9, 8, 8), a(1, 3, 4, 2, 1, 1), 8, 4, "进阶", true, false, true,
                                "第 8 品横按，第 5、4 弦 10 品，第 3 弦 9 品。适合学习封闭和弦和高把位声音。")
                )
        ));
        register(new Chord(
                "D", "D 大三和弦", "D", "大三和弦",
                list("1", "3", "5"), list("D", "F#", "A"), list("Dmaj", "Dmajor"),
                "D 大三和弦由 D、F#、A 构成，开放按法明亮清晰，常用于入门歌曲伴奏。",
                list(voicing("开放 D 和弦", a(-1, -1, 0, 2, 3, 2), a(0, 0, 0, 1, 3, 2), 1, 4, "入门", true, false, false,
                        "第 6、5 弦不弹，第 4 弦空弦，第 3 弦 2 品、第 2 弦 3 品、第 1 弦 2 品。"))
        ));
        register(new Chord(
                "E", "E 大三和弦", "E", "大三和弦",
                list("1", "3", "5"), list("E", "G#", "B"), list("Emaj", "Emajor"),
                "E 大三和弦由 E、G#、B 构成，开放按法饱满稳定，是常见基础和弦。",
                list(voicing("开放 E 和弦", a(0, 2, 2, 1, 0, 0), a(0, 2, 3, 1, 0, 0), 1, 4, "入门", true, false, false,
                        "第 6、2、1 弦为空弦，第 5、4 弦 2 品，第 3 弦 1 品。"))
        ));
        register(new Chord(
                "F", "F 大三和弦", "F", "大三和弦",
                list("1", "3", "5"), list("F", "A", "C"), list("Fmaj", "Fmajor"),
                "F 大三和弦由 F、A、C 构成，常见按法需要横按，初学者通常会觉得难度较高。",
                list(
                        voicing("F 简化按法", a(-1, -1, 3, 2, 1, 1), a(0, 0, 3, 2, 1, 1), 1, 4, "常见", true, true, true,
                                "第 6、5 弦不弹，第 4 弦 3 品、第 3 弦 2 品，第 2、1 弦 1 品。比完整大横按更适合先练。"),
                        voicing("F 大横按", a(1, 3, 3, 2, 1, 1), a(1, 3, 4, 2, 1, 1), 1, 4, "进阶", true, false, true,
                                "第 1 品横按，第 5、4 弦 3 品，第 3 弦 2 品。该按法需要横按。")
                )
        ));
        register(new Chord(
                "G", "G 大三和弦", "G", "大三和弦",
                list("1", "3", "5"), list("G", "B", "D"), list("Gmaj", "Gmajor"),
                "G 大三和弦由 G、B、D 构成，开放按法声音开阔，是吉他伴奏中非常常见的和弦。",
                list(
                        voicing("开放 G 和弦", a(3, 2, 0, 0, 0, 3), a(3, 2, 0, 0, 0, 4), 1, 4, "入门", true, false, false,
                                "第 6 弦 3 品、第 5 弦 2 品、第 1 弦 3 品，其余为空弦。"),
                        voicing("G 大横按", a(3, 5, 5, 4, 3, 3), a(1, 3, 4, 2, 1, 1), 3, 4, "进阶", true, false, true,
                                "第 3 品横按，第 5、4 弦 5 品，第 3 弦 4 品。适合比较开放和弦与封闭和弦听感。")
                )
        ));
        register(new Chord(
                "A", "A 大三和弦", "A", "大三和弦",
                list("1", "3", "5"), list("A", "C#", "E"), list("Amaj", "Amajor"),
                "A 大三和弦由 A、C#、E 构成，开放按法紧凑明亮。",
                list(voicing("开放 A 和弦", a(-1, 0, 2, 2, 2, 0), a(0, 0, 1, 2, 3, 0), 1, 4, "入门", true, false, false,
                        "第 6 弦不弹，第 5、1 弦为空弦，第 4、3、2 弦 2 品。"))
        ));
        register(new Chord(
                "B", "B 大三和弦", "B", "大三和弦",
                list("1", "3", "5"), list("B", "D#", "F#"), list("Bmaj", "Bmajor"),
                "B 大三和弦由 B、D#、F# 构成，常见吉他按法多为封闭和弦。",
                list(voicing("B 封闭和弦", a(-1, 2, 4, 4, 4, 2), a(0, 1, 3, 3, 3, 1), 2, 4, "进阶", true, false, true,
                        "第 6 弦不弹，第 2 品横按，第 4、3、2 弦 4 品。"))
        ));
        register(new Chord(
                "Am", "A 小三和弦", "A", "小三和弦",
                list("1", "b3", "5"), list("A", "C", "E"), list("Amin", "A-"),
                "A 小三和弦由 A、C、E 构成，听感较柔和、带有轻微忧郁色彩。",
                list(
                        voicing("开放 Am 和弦", a(-1, 0, 2, 2, 1, 0), a(0, 0, 2, 3, 1, 0), 1, 4, "入门", true, false, false,
                                "第 6 弦不弹，第 5、1 弦为空弦，第 4、3 弦 2 品，第 2 弦 1 品。"),
                        voicing("Am 五品横按", a(5, 7, 7, 5, 5, 5), a(1, 3, 4, 1, 1, 1), 5, 4, "进阶", true, false, true,
                                "第 5 品横按，第 5、4 弦 7 品。适合比较开放 Am 和高把位 Am 的声音差异。")
                )
        ));
        register(new Chord(
                "Dm", "D 小三和弦", "D", "小三和弦",
                list("1", "b3", "5"), list("D", "F", "A"), list("Dmin", "D-"),
                "D 小三和弦由 D、F、A 构成，开放按法适合入门阶段练习。",
                list(voicing("开放 Dm 和弦", a(-1, -1, 0, 2, 3, 1), a(0, 0, 0, 2, 3, 1), 1, 4, "入门", true, false, false,
                        "第 6、5 弦不弹，第 4 弦空弦，第 3 弦 2 品、第 2 弦 3 品、第 1 弦 1 品。"))
        ));
        register(new Chord(
                "Em", "E 小三和弦", "E", "小三和弦",
                list("1", "b3", "5"), list("E", "G", "B"), list("Emin", "E-"),
                "E 小三和弦由 E、G、B 构成，开放按法简单饱满，是最早学习的和弦之一。",
                list(voicing("开放 Em 和弦", a(0, 2, 2, 0, 0, 0), a(0, 2, 3, 0, 0, 0), 1, 4, "入门", true, false, false,
                        "第 5、4 弦 2 品，其余为空弦。"))
        ));
        register(new Chord(
                "G7", "G 属七和弦", "G", "属七和弦",
                list("1", "3", "5", "b7"), list("G", "B", "D", "F"), list(),
                "G7 由 G、B、D、F 构成，具有较强的解决倾向，常连接到 C 和弦。",
                list(voicing("开放 G7 和弦", a(3, 2, 0, 0, 0, 1), a(3, 2, 0, 0, 0, 1), 1, 4, "入门", true, false, false,
                        "第 6 弦 3 品、第 5 弦 2 品、第 1 弦 1 品，其余为空弦。"))
        ));
        register(new Chord(
                "A7", "A 属七和弦", "A", "属七和弦",
                list("1", "3", "5", "b7"), list("A", "C#", "E", "G"), list(),
                "A7 由 A、C#、E、G 构成，常用于推动到 D 或 Dm 一类和弦。",
                list(voicing("开放 A7 和弦", a(-1, 0, 2, 0, 2, 0), a(0, 0, 2, 0, 3, 0), 1, 4, "入门", true, false, false,
                        "第 6 弦不弹，第 4、2 弦 2 品，其余有效弦为空弦。"))
        ));
        register(new Chord(
                "E7", "E 属七和弦", "E", "属七和弦",
                list("1", "3", "5", "b7"), list("E", "G#", "B", "D"), list(),
                "E7 由 E、G#、B、D 构成，开放按法常用于连接到 A 或 Am。",
                list(voicing("开放 E7 和弦", a(0, 2, 0, 1, 0, 0), a(0, 2, 0, 1, 0, 0), 1, 4, "入门", true, false, false,
                        "第 5 弦 2 品、第 3 弦 1 品，其余为空弦。"))
        ));
        register(new Chord(
                "Cmaj7", "C 大七和弦", "C", "大七和弦",
                list("1", "3", "5", "7"), list("C", "E", "G", "B"), list("CM7", "CΔ7"),
                "Cmaj7 由 C、E、G、B 构成，听感明亮、柔和，比 C 大三和弦更有延展感。",
                list(voicing("开放 Cmaj7 和弦", a(-1, 3, 2, 0, 0, 0), a(0, 3, 2, 0, 0, 0), 1, 4, "入门", true, false, false,
                        "第 6 弦不弹，第 5 弦 3 品、第 4 弦 2 品，其余有效弦为空弦。"))
        ));
        register(new Chord(
                "Fmaj7", "F 大七和弦", "F", "大七和弦",
                list("1", "3", "5", "7"), list("F", "A", "C", "E"), list("FM7", "FΔ7"),
                "Fmaj7 由 F、A、C、E 构成，常见开放按法比 F 大横按更适合初学者入门。",
                list(voicing("开放 Fmaj7 和弦", a(-1, -1, 3, 2, 1, 0), a(0, 0, 3, 2, 1, 0), 1, 4, "入门", true, false, false,
                        "第 6、5 弦不弹，第 4 弦 3 品、第 3 弦 2 品、第 2 弦 1 品、第 1 弦空弦。"))
        ));
        register(new Chord(
                "Am7", "A 小七和弦", "A", "小七和弦",
                list("1", "b3", "5", "b7"), list("A", "C", "E", "G"), list("Amin7", "A-7"),
                "Am7 由 A、C、E、G 构成，听感比 Am 更松弛、柔和。",
                list(voicing("开放 Am7 和弦", a(-1, 0, 2, 0, 1, 0), a(0, 0, 2, 0, 1, 0), 1, 4, "入门", true, false, false,
                        "第 6 弦不弹，第 4 弦 2 品、第 2 弦 1 品，其余有效弦为空弦。"))
        ));
        register(new Chord(
                "Dm7", "D 小七和弦", "D", "小七和弦",
                list("1", "b3", "5", "b7"), list("D", "F", "A", "C"), list("Dmin7", "D-7"),
                "Dm7 由 D、F、A、C 构成，常用于流行、民谣和爵士语境。",
                list(voicing("开放 Dm7 和弦", a(-1, -1, 0, 2, 1, 1), a(0, 0, 0, 2, 1, 1), 1, 4, "入门", true, false, true,
                        "第 6、5 弦不弹，第 4 弦空弦，第 3 弦 2 品，第 2、1 弦 1 品。"))
        ));
        register(new Chord(
                "Bm7", "B 小七和弦", "B", "小七和弦",
                list("1", "b3", "5", "b7"), list("B", "D", "F#", "A"), list("Bmin7", "B-7"),
                "Bm7 由 B、D、F#、A 构成，常见吉他按法通常需要二品横按。",
                list(voicing("Bm7 封闭和弦", a(-1, 2, 4, 2, 3, 2), a(0, 1, 3, 1, 2, 1), 2, 4, "进阶", true, false, true,
                        "第 6 弦不弹，第 2 品横按，第 4 弦 4 品、第 2 弦 3 品。"))
        ));
        register(new Chord(
                "Csus2", "C 挂二和弦", "C", "挂留和弦",
                list("1", "2", "5"), list("C", "D", "G"), list(),
                "Csus2 用二度音 D 替代三度音，听感开放、悬而未决。",
                list(voicing("开放 Csus2 和弦", a(-1, 3, 0, 0, 3, 3), a(0, 1, 0, 0, 3, 4), 1, 4, "入门", true, false, false,
                        "第 6 弦不弹，第 5 弦 3 品，第 2、1 弦 3 品，第 4、3 弦为空弦。"))
        ));
        register(new Chord(
                "Dsus4", "D 挂四和弦", "D", "挂留和弦",
                list("1", "4", "5"), list("D", "G", "A"), list(),
                "Dsus4 用四度音 G 替代三度音，常与 D 大三和弦交替使用。",
                list(voicing("开放 Dsus4 和弦", a(-1, -1, 0, 2, 3, 3), a(0, 0, 0, 1, 3, 4), 1, 4, "入门", true, false, false,
                        "第 6、5 弦不弹，第 4 弦空弦，第 3 弦 2 品，第 2、1 弦 3 品。"))
        ));
        register(new Chord(
                "Bdim", "B 减和弦", "B", "减和弦",
                list("1", "b3", "b5"), list("B", "D", "F"), list("B°"),
                "Bdim 由 B、D、F 构成，听感紧张，常用于制造过渡和不稳定感。",
                list(voicing("Bdim 常见按法", a(-1, 2, 3, 4, 3, -1), a(0, 1, 2, 4, 3, 0), 2, 4, "进阶", true, false, false,
                        "第 6、1 弦不弹，第 5 弦 2 品、第 4 弦 3 品、第 3 弦 4 品、第 2 弦 3 品。"))
        ));
        register(new Chord(
                "Caug", "C 增和弦", "C", "增和弦",
                list("1", "3", "#5"), list("C", "E", "G#"), list("C+"),
                "Caug 由 C、E、G# 构成，听感带有向外扩张的不稳定色彩。",
                list(voicing("Caug 常见按法", a(-1, 3, 2, 1, 1, 0), a(0, 4, 3, 1, 2, 0), 1, 4, "常见", true, false, false,
                        "第 6 弦不弹，第 5 弦 3 品、第 4 弦 2 品、第 3、2 弦 1 品，第 1 弦空弦。"))
        ));
        register(new Chord(
                "Cadd9", "C 加九和弦", "C", "加音和弦",
                list("1", "3", "5", "9"), list("C", "E", "G", "D"), list(),
                "Cadd9 在 C 大三和弦基础上加入九度音 D，听感明亮、流行感较强。",
                list(voicing("开放 Cadd9 和弦", a(-1, 3, 2, 0, 3, 0), a(0, 3, 2, 0, 4, 0), 1, 4, "入门", true, false, false,
                        "第 6 弦不弹，第 5 弦 3 品、第 4 弦 2 品、第 2 弦 3 品，第 3、1 弦为空弦。"))
        ));
        register(new Chord(
                "C7", "C 属七和弦", "C", "属七和弦",
                list("1", "3", "5", "b7"), list("C", "E", "G", "Bb"), list(),
                "C7 由 C、E、G、Bb 构成，带有明显的属功能色彩，常用于连接到 F。",
                list(voicing("开放 C7 和弦", a(-1, 3, 2, 3, 1, 0), a(0, 3, 2, 4, 1, 0), 1, 4, "常见", true, false, false,
                        "第 6 弦不弹，第 5 弦 3 品、第 4 弦 2 品、第 3 弦 3 品、第 2 弦 1 品，第 1 弦空弦。"))
        ));
        register(new Chord(
                "D7", "D 属七和弦", "D", "属七和弦",
                list("1", "3", "5", "b7"), list("D", "F#", "A", "C"), list(),
                "D7 由 D、F#、A、C 构成，常用于推动到 G 或 Gm。",
                list(voicing("开放 D7 和弦", a(-1, -1, 0, 2, 1, 2), a(0, 0, 0, 2, 1, 3), 1, 4, "入门", true, false, false,
                        "第 6、5 弦不弹，第 4 弦空弦，第 3 弦 2 品、第 2 弦 1 品、第 1 弦 2 品。"))
        ));
        register(new Chord(
                "Bm", "B 小三和弦", "B", "小三和弦",
                list("1", "b3", "5"), list("B", "D", "F#"), list("Bmin", "B-"),
                "Bm 由 B、D、F# 构成，常见吉他按法需要二品横按，是从开放和弦过渡到封闭和弦的重要练习对象。",
                list(
                        voicing("Bm 小横按", a(-1, 2, 4, 4, 3, 2), a(0, 1, 3, 4, 2, 1), 2, 4, "进阶", true, false, true,
                                "第 6 弦不弹，第 2 品横按，第 4、3 弦 4 品，第 2 弦 3 品。"),
                        voicing("Bm 简化按法", a(-1, -1, 4, 4, 3, 2), a(0, 0, 3, 4, 2, 1), 2, 4, "常见", true, true, false,
                                "第 6、5 弦不弹，只弹高四根弦，适合先练习 Bm 的核心声音。")
                )
        ));
        register(new Chord(
                "F#m", "F# 小三和弦", "F#", "小三和弦",
                list("1", "b3", "5"), list("F#", "A", "C#"), list("F#min", "F#-"),
                "F#m 由 F#、A、C# 构成，常见于 E、A、D 调歌曲中。",
                list(voicing("F#m 大横按", a(2, 4, 4, 2, 2, 2), a(1, 3, 4, 1, 1, 1), 2, 4, "进阶", true, false, true,
                        "第 2 品横按，第 5、4 弦 4 品。该按法需要较稳定的横按力量。"))
        ));
        register(new Chord(
                "C9", "C 九和弦", "C", "九和弦",
                list("1", "3", "5", "b7", "9"), list("C", "E", "G", "Bb", "D"), list(),
                "C9 在 C7 基础上加入九度音 D，声音比属七和弦更丰富，常用于布鲁斯、放克和爵士语境。",
                list(voicing("C9 常见按法", a(-1, 3, 2, 3, 3, 3), a(0, 2, 1, 3, 3, 3), 1, 4, "进阶", true, false, true,
                        "第 6 弦不弹，第 5 弦 3 品、第 4 弦 2 品，第 3、2、1 弦 3 品。"))
        ));
        register(new Chord(
                "G9", "G 九和弦", "G", "九和弦",
                list("1", "3", "5", "b7", "9"), list("G", "B", "D", "F", "A"), list(),
                "G9 在 G7 基础上加入九度音 A，听感更明亮、延展。",
                list(voicing("开放 G9 和弦", a(3, 2, 0, 2, 0, 1), a(3, 2, 0, 4, 0, 1), 1, 4, "常见", true, false, false,
                        "第 6 弦 3 品、第 5 弦 2 品、第 3 弦 2 品、第 1 弦 1 品，其余为空弦。"))
        ));
        register(new Chord(
                "D9", "D 九和弦", "D", "九和弦",
                list("1", "3", "5", "b7", "9"), list("D", "F#", "A", "C", "E"), list(),
                "D9 在 D7 基础上加入九度音 E，常用于需要更丰富属功能色彩的伴奏。",
                list(voicing("D9 常见按法", a(-1, 5, 4, 5, 5, -1), a(0, 2, 1, 3, 4, 0), 4, 4, "进阶", true, false, false,
                        "第 6、1 弦不弹，第 5 弦 5 品、第 4 弦 4 品、第 3、2 弦 5 品。"))
        ));
        register(new Chord(
                "A9", "A 九和弦", "A", "九和弦",
                list("1", "3", "5", "b7", "9"), list("A", "C#", "E", "G", "B"), list(),
                "A9 在 A7 基础上加入九度音 B，常用于 blues、funk 和流行伴奏。",
                list(voicing("开放 A9 和弦", a(-1, 0, 2, 4, 2, 3), a(0, 0, 1, 4, 2, 3), 1, 4, "常见", true, false, false,
                        "第 6 弦不弹，第 5 弦空弦，第 4 弦 2 品、第 3 弦 4 品、第 2 弦 2 品、第 1 弦 3 品。"))
        ));
        register(new Chord(
                "C/E", "C/E 分数和弦", "C", "分数和弦", "E",
                list("1", "3", "5"), list("C", "E", "G"), list(),
                "C/E 表示以 E 作为低音的 C 和弦，常用于低音线平滑连接。",
                list(voicing("开放 C/E 和弦", a(0, 3, 2, 0, 1, 0), a(0, 3, 2, 0, 1, 0), 1, 4, "入门", true, false, false,
                        "第 6 弦空弦作为低音 E，其余基本保持开放 C 和弦按法。"))
        ));
        register(new Chord(
                "G/B", "G/B 分数和弦", "G", "分数和弦", "B",
                list("1", "3", "5"), list("G", "B", "D"), list(),
                "G/B 表示以 B 作为低音的 G 和弦，常用于 C、G、Am 等和弦之间的低音连接。",
                list(voicing("开放 G/B 和弦", a(-1, 2, 0, 0, 3, 3), a(0, 1, 0, 0, 3, 4), 1, 4, "入门", true, false, false,
                        "第 6 弦不弹，第 5 弦 2 品作为低音 B，第 2、1 弦 3 品，其余为空弦。"))
        ));
        register(new Chord(
                "D/F#", "D/F# 分数和弦", "D", "分数和弦", "F#",
                list("1", "3", "5"), list("D", "F#", "A"), list(),
                "D/F# 表示以 F# 作为低音的 D 和弦，常用于 G、D、Em 等和弦之间的过渡。",
                list(voicing("开放 D/F# 和弦", a(2, 0, 0, 2, 3, 2), a(1, 0, 0, 2, 4, 3), 1, 4, "常见", true, false, false,
                        "第 6 弦 2 品作为低音 F#，第 5、4 弦为空弦，第 3 弦 2 品、第 2 弦 3 品、第 1 弦 2 品。"))
        ));
    }

    private void register(Chord chord) {
        chordsBySymbol.put(chord.symbol, chord);
        chordsByAlias.put(canonicalLookupKey(chord.symbol), chord);
        for (String alias : chord.aliases) {
            chordsByAlias.put(canonicalLookupKey(alias), chord);
        }
    }

    private NormalizedInput normalize(String input) {
        String cleaned = input.replace(" ", "")
                .replace("♯", "#")
                .replace("♭", "b")
                .replace("Δ", "maj")
                .replace("°", "dim")
                .replace("+", "aug");
        if (cleaned.isEmpty()) {
            return NormalizedInput.error("请输入和弦名称，例如 C、Am、G7、Fmaj7。");
        }
        char first = Character.toUpperCase(cleaned.charAt(0));
        if (first == 'H') {
            return NormalizedInput.error("无法识别 H 作为根音。请使用 C、D、E、F、G、A、B 及升降号写法。");
        }
        if ("ABCDEFG".indexOf(first) < 0) {
            return NormalizedInput.error("无法识别该和弦名称，请输入类似 C、Am、G7、Fmaj7 的格式。");
        }
        StringBuilder root = new StringBuilder();
        root.append(first);
        int index = 1;
        if (cleaned.length() > 1) {
            char accidental = cleaned.charAt(1);
            if (accidental == '#') {
                root.append('#');
                index = 2;
            } else if (accidental == 'b' || accidental == 'B') {
                root.append('b');
                index = 2;
            }
        }
        String rootText = root.toString();
        String uncommonHint = uncommonRootHint(rootText);
        if (uncommonHint != null) {
            return NormalizedInput.error(uncommonHint);
        }
        String quality = cleaned.substring(index);
        String normalizedQuality = normalizeQuality(quality);
        String symbol = root + normalizedQuality;
        boolean changed = !symbol.equals(input);
        return NormalizedInput.success(symbol, changed);
    }

    private static String canonicalLookupKey(String symbol) {
        String cleaned = symbol.replace(" ", "")
                .replace("♯", "#")
                .replace("♭", "b")
                .replace("°", "dim")
                .replace("Δ", "maj")
                .replace("+", "aug")
                .trim();
        if (cleaned.isEmpty()) {
            return cleaned;
        }
        char first = Character.toUpperCase(cleaned.charAt(0));
        StringBuilder root = new StringBuilder();
        root.append(first);
        int index = 1;
        if (cleaned.length() > 1) {
            char accidental = cleaned.charAt(1);
            if (accidental == '#') {
                root.append('#');
                index = 2;
            } else if (accidental == 'b' || accidental == 'B') {
                root.append('b');
                index = 2;
            }
        }
        return root + normalizeQuality(cleaned.substring(index));
    }

    private static String uncommonRootHint(String root) {
        if ("Cb".equals(root)) {
            return "Cb 是非常见等音写法，当前版本请尝试输入 B。";
        }
        if ("B#".equals(root)) {
            return "B# 是非常见等音写法，当前版本请尝试输入 C。";
        }
        if ("E#".equals(root)) {
            return "E# 是非常见等音写法，当前版本请尝试输入 F。";
        }
        if ("Fb".equals(root)) {
            return "Fb 是非常见等音写法，当前版本请尝试输入 E。";
        }
        return null;
    }

    private static String normalizeQuality(String quality) {
        if (quality.isEmpty()) {
            return "";
        }
        if (quality.startsWith("/")) {
            return "/" + normalizeBassNote(quality.substring(1));
        }
        String lower = quality.toLowerCase(Locale.US);
        if (lower.equals("maj") || lower.equals("major")) {
            return "";
        }
        if (lower.equals("m") || lower.equals("min") || lower.equals("minor") || lower.equals("-")) {
            return "m";
        }
        if (lower.equals("maj7") || quality.equals("M7") || lower.equals("major7")) {
            return "maj7";
        }
        if (lower.equals("m7") || lower.equals("min7") || lower.equals("minor7") || lower.equals("-7")) {
            return "m7";
        }
        if (lower.equals("dim")) {
            return "dim";
        }
        if (lower.equals("aug")) {
            return "aug";
        }
        if (lower.equals("sus2")) {
            return "sus2";
        }
        if (lower.equals("sus4")) {
            return "sus4";
        }
        if (lower.equals("add9")) {
            return "add9";
        }
        return quality;
    }

    private static String normalizeBassNote(String bass) {
        if (bass.isEmpty()) {
            return bass;
        }
        String cleaned = bass.replace("♯", "#").replace("♭", "b");
        StringBuilder builder = new StringBuilder();
        builder.append(Character.toUpperCase(cleaned.charAt(0)));
        if (cleaned.length() > 1) {
            char accidental = cleaned.charAt(1);
            if (accidental == '#') {
                builder.append('#');
            } else if (accidental == 'b' || accidental == 'B') {
                builder.append('b');
            }
        }
        return builder.toString();
    }

    private static int[] a(int... values) {
        return values;
    }

    private static <T> List<T> list(T... values) {
        return Arrays.asList(values);
    }

    private static Voicing voicing(
            String name,
            int[] frets,
            int[] fingers,
            int startFret,
            int displayFrets,
            String difficulty,
            boolean recommended,
            boolean simplified,
            boolean barre,
            String description
    ) {
        return new Voicing(name, frets, fingers, startFret, displayFrets, difficulty, recommended, simplified, barre, description);
    }

    public static final class LookupResult {
        public final boolean recognized;
        public final Chord chord;
        public final String message;

        private LookupResult(boolean recognized, Chord chord, String message) {
            this.recognized = recognized;
            this.chord = chord;
            this.message = message;
        }

        public static LookupResult success(Chord chord, String message) {
            return new LookupResult(true, chord, message);
        }

        public static LookupResult error(String message) {
            return new LookupResult(false, null, message);
        }
    }

    private static final class NormalizedInput {
        final String symbol;
        final String error;
        final boolean changed;

        private NormalizedInput(String symbol, String error, boolean changed) {
            this.symbol = symbol == null ? null : canonicalLookupKey(symbol);
            this.error = error;
            this.changed = changed;
        }

        static NormalizedInput success(String symbol, boolean changed) {
            return new NormalizedInput(symbol, null, changed);
        }

        static NormalizedInput error(String error) {
            return new NormalizedInput(null, error, false);
        }
    }
}
