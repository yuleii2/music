package com.k2.music;

/**
 * 第一阶段内置和弦库。这里是数据层种子，Activity 不直接硬编码任何和弦。
 *
 * 后续扩展到 1000+ 理论和弦时，可以继续增加 ChordQuality；
 * 扩展到 10000+ 指法时，可以追加更多 ChordShape、从 JSON/SQLite 加载，或由规则生成器补齐。
 */
final class ChordLibraryData {
    private static final String[] CHROMATIC_ROOTS = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private ChordLibraryData() {
    }

    static void populate(ChordRepository repository) {
        registerQualities(repository);
        registerCommonOpenAndSlashShapes(repository);
        registerMovableBarreShapes(repository);
        registerMovableDiminishedShapes(repository);
    }

    private static void registerQualities(ChordRepository repository) {
        q(repository, "maj", "Major", "大三和弦", a(0, 4, 7), labels("1", "3", "5"),
                "triad", 1, "由根音、大三度和纯五度构成，听感明亮稳定。");
        q(repository, "m", "Minor", "小三和弦", a(0, 3, 7), labels("1", "b3", "5"),
                "triad", 1, "由根音、小三度和纯五度构成，听感柔和偏暗。");
        q(repository, "7", "Dominant 7", "属七和弦", a(0, 4, 7, 10), labels("1", "3", "5", "b7"),
                "seventh", 2, "在大三和弦上加入小七度，常用于制造解决倾向。");
        q(repository, "maj7", "Major 7", "大七和弦", a(0, 4, 7, 11), labels("1", "3", "5", "7"),
                "seventh", 2, "在大三和弦上加入大七度，声音明亮、柔和且有延展感。");
        q(repository, "m7", "Minor 7", "小七和弦", a(0, 3, 7, 10), labels("1", "b3", "5", "b7"),
                "seventh", 2, "在小三和弦上加入小七度，常见于流行、民谣与爵士语境。");
        q(repository, "sus2", "Suspended 2", "挂二和弦", a(0, 2, 7), labels("1", "2", "5"),
                "suspended", 1, "用二度音替代三度音，听感开放、悬而未决。");
        q(repository, "sus4", "Suspended 4", "挂四和弦", a(0, 5, 7), labels("1", "4", "5"),
                "suspended", 1, "用四度音替代三度音，常与大三和弦交替使用。");
        q(repository, "add9", "Add 9", "加九和弦", a(0, 4, 7, 14), labels("1", "3", "5", "9"),
                "added", 2, "在三和弦上加入九度音，不包含七度，流行感较强。");
        q(repository, "dim", "Diminished", "减和弦", a(0, 3, 6), labels("1", "b3", "b5"),
                "altered", 3, "小三度叠置形成的紧张色彩，常用于经过或转调。");
        q(repository, "aug", "Augmented", "增和弦", a(0, 4, 8), labels("1", "3", "#5"),
                "altered", 3, "大三和弦升高五度，听感向外扩张并带不稳定性。");
        q(repository, "9", "Dominant 9", "九和弦", a(0, 4, 7, 10, 14), labels("1", "3", "5", "b7", "9"),
                "extended", 3, "在属七和弦上加入九度音，常见于布鲁斯、放克和爵士。");
    }

    private static void registerCommonOpenAndSlashShapes(ChordRepository r) {
        // 大三和弦
        s(r, "开放 C 和弦", "C", "maj", "", a(-1, 3, 2, 0, 1, 0), a(0, 3, 2, 0, 1, 0), 1, 1, "open",
                "第 6 弦不弹，第 5 弦 3 品、第 4 弦 2 品、第 2 弦 1 品，其余为有效空弦。");
        s(r, "C 大横按", "C", "maj", "", a(8, 10, 10, 9, 8, 8), a(1, 3, 4, 2, 1, 1), 8, 4, "barre",
                "第 8 品横按，第 5、4 弦 10 品，第 3 弦 9 品。适合学习封闭和弦和高把位声音。");
        s(r, "开放 D 和弦", "D", "maj", "", a(-1, -1, 0, 2, 3, 2), a(0, 0, 0, 1, 3, 2), 1, 1, "open",
                "第 6、5 弦不弹，第 4 弦空弦，第 3 弦 2 品、第 2 弦 3 品、第 1 弦 2 品。");
        s(r, "开放 E 和弦", "E", "maj", "", a(0, 2, 2, 1, 0, 0), a(0, 2, 3, 1, 0, 0), 1, 1, "open",
                "第 6、2、1 弦为空弦，第 5、4 弦 2 品，第 3 弦 1 品。");
        s(r, "F 简化按法", "F", "maj", "", a(-1, -1, 3, 2, 1, 1), a(0, 0, 3, 2, 1, 1), 1, 2, "simplified",
                "第 6、5 弦不弹，比完整大横按更适合先练习。");
        s(r, "F 大横按", "F", "maj", "", a(1, 3, 3, 2, 1, 1), a(1, 3, 4, 2, 1, 1), 1, 4, "barre",
                "第 1 品横按，第 5、4 弦 3 品，第 3 弦 2 品。");
        s(r, "开放 G 和弦", "G", "maj", "", a(3, 2, 0, 0, 0, 3), a(3, 2, 0, 0, 0, 4), 1, 1, "open",
                "第 6 弦 3 品、第 5 弦 2 品、第 1 弦 3 品，其余为空弦。");
        s(r, "开放 A 和弦", "A", "maj", "", a(-1, 0, 2, 2, 2, 0), a(0, 0, 1, 2, 3, 0), 1, 1, "open",
                "第 6 弦不弹，第 5、1 弦为空弦，第 4、3、2 弦 2 品。");
        s(r, "B 封闭和弦", "B", "maj", "", a(-1, 2, 4, 4, 4, 2), a(0, 1, 3, 3, 3, 1), 2, 4, "barre",
                "第 6 弦不弹，第 2 品横按，第 4、3、2 弦 4 品。");

        // 小三和弦
        s(r, "Cm 横按", "C", "m", "", a(-1, 3, 5, 5, 4, 3), a(0, 1, 3, 4, 2, 1), 3, 4, "barre",
                "A 型小三横按，适合与同把位 C 大三和弦对比练习。");
        s(r, "开放 Dm 和弦", "D", "m", "", a(-1, -1, 0, 2, 3, 1), a(0, 0, 0, 2, 3, 1), 1, 1, "open",
                "第 6、5 弦不弹，第 4 弦空弦，第 3 弦 2 品、第 2 弦 3 品、第 1 弦 1 品。");
        s(r, "开放 Em 和弦", "E", "m", "", a(0, 2, 2, 0, 0, 0), a(0, 2, 3, 0, 0, 0), 1, 1, "open",
                "第 5、4 弦 2 品，其余为空弦，是最常见的入门小三和弦。");
        s(r, "Fm 横按", "F", "m", "", a(1, 3, 3, 1, 1, 1), a(1, 3, 4, 1, 1, 1), 1, 4, "barre",
                "第 1 品横按，第 5、4 弦 3 品。");
        s(r, "Gm 横按", "G", "m", "", a(3, 5, 5, 3, 3, 3), a(1, 3, 4, 1, 1, 1), 3, 4, "barre",
                "第 3 品横按，第 5、4 弦 5 品。");
        s(r, "开放 Am 和弦", "A", "m", "", a(-1, 0, 2, 2, 1, 0), a(0, 0, 2, 3, 1, 0), 1, 1, "open",
                "第 6 弦不弹，第 5、1 弦为空弦，第 4、3 弦 2 品，第 2 弦 1 品。");
        s(r, "Am 五品横按", "A", "m", "", a(5, 7, 7, 5, 5, 5), a(1, 3, 4, 1, 1, 1), 5, 4, "barre",
                "第 5 品横按，第 5、4 弦 7 品。适合比较开放 Am 与高把位 Am 的声音差异。");
        s(r, "Bm 小横按", "B", "m", "", a(-1, 2, 4, 4, 3, 2), a(0, 1, 3, 4, 2, 1), 2, 4, "barre",
                "第 6 弦不弹，第 2 品横按，第 4、3 弦 4 品，第 2 弦 3 品。");
        s(r, "Bm 简化按法", "B", "m", "", a(-1, -1, 4, 4, 3, 2), a(0, 0, 3, 4, 2, 1), 2, 2, "simplified",
                "只弹高四根弦，适合先练习 Bm 的核心声音。");

        // 七和弦
        s(r, "开放 C7 和弦", "C", "7", "", a(-1, 3, 2, 3, 1, 0), a(0, 3, 2, 4, 1, 0), 1, 2, "open",
                "在开放 C 的基础上加入第 3 弦 3 品，形成小七度。");
        s(r, "开放 D7 和弦", "D", "7", "", a(-1, -1, 0, 2, 1, 2), a(0, 0, 0, 2, 1, 3), 1, 1, "open",
                "常用于解决到 G 或 Gm。");
        s(r, "开放 E7 和弦", "E", "7", "", a(0, 2, 0, 1, 0, 0), a(0, 2, 0, 1, 0, 0), 1, 1, "open",
                "第 5 弦 2 品、第 3 弦 1 品，其余为空弦。");
        s(r, "F7 横按", "F", "7", "", a(1, 3, 1, 2, 1, 1), a(1, 3, 1, 2, 1, 1), 1, 4, "barre",
                "E7 型横按，包含完整属七声音。");
        s(r, "开放 G7 和弦", "G", "7", "", a(3, 2, 0, 0, 0, 1), a(3, 2, 0, 0, 0, 1), 1, 1, "open",
                "第 1 弦 1 品带来属七的解决倾向，常连接到 C。");
        s(r, "开放 A7 和弦", "A", "7", "", a(-1, 0, 2, 0, 2, 0), a(0, 0, 2, 0, 3, 0), 1, 1, "open",
                "第 4、2 弦 2 品，其余有效弦为空弦。");
        s(r, "开放 B7 和弦", "B", "7", "", a(-1, 2, 1, 2, 0, 2), a(0, 2, 1, 3, 0, 4), 1, 2, "open",
                "第 6 弦不弹，开放 B7 是 E 调常用属和弦。");

        // 大七和小七
        s(r, "开放 Cmaj7 和弦", "C", "maj7", "", a(-1, 3, 2, 0, 0, 0), a(0, 3, 2, 0, 0, 0), 1, 1, "open",
                "保留开放 C 的低音结构，高音开放弦形成大七色彩。");
        s(r, "开放 Dmaj7 和弦", "D", "maj7", "", a(-1, -1, 0, 2, 2, 2), a(0, 0, 0, 1, 1, 1), 1, 2, "open",
                "高三根弦 2 品可用一指小横按。");
        s(r, "开放 Emaj7 和弦", "E", "maj7", "", a(0, 2, 1, 1, 0, 0), a(0, 3, 1, 2, 0, 0), 1, 2, "open",
                "开放 E 的柔和大七版本。");
        s(r, "开放 Fmaj7 和弦", "F", "maj7", "", a(-1, -1, 3, 2, 1, 0), a(0, 0, 3, 2, 1, 0), 1, 1, "open",
                "比 F 大横按更友好，适合初学者先掌握。");
        s(r, "开放 Gmaj7 和弦", "G", "maj7", "", a(3, 2, 0, 0, 0, 2), a(3, 2, 0, 0, 0, 1), 1, 2, "open",
                "第 1 弦 2 品提供大七度 F#。");
        s(r, "开放 Amaj7 和弦", "A", "maj7", "", a(-1, 0, 2, 1, 2, 0), a(0, 0, 2, 1, 3, 0), 1, 2, "open",
                "A 大三和弦加入大七度 G#。");
        s(r, "Bmaj7 横按", "B", "maj7", "", a(-1, 2, 4, 3, 4, 2), a(0, 1, 3, 2, 4, 1), 2, 4, "barre",
                "Amaj7 型横按，适合爵士和流行慢歌。");
        s(r, "Cm7 横按", "C", "m7", "", a(-1, 3, 5, 3, 4, 3), a(0, 1, 3, 1, 2, 1), 3, 4, "barre",
                "A 型小七横按，可与 C7、Cmaj7 对比听辨。");
        s(r, "开放 Dm7 和弦", "D", "m7", "", a(-1, -1, 0, 2, 1, 1), a(0, 0, 0, 2, 1, 1), 1, 1, "open",
                "第 2、1 弦 1 品常用一指小横按。");
        s(r, "开放 Em7 和弦", "E", "m7", "", a(0, 2, 0, 0, 0, 0), a(0, 2, 0, 0, 0, 0), 1, 1, "open",
                "只需按第 5 弦 2 品，声音开放宽松。");
        s(r, "Fm7 横按", "F", "m7", "", a(1, 3, 1, 1, 1, 1), a(1, 3, 1, 1, 1, 1), 1, 4, "barre",
                "E 型小七横按。");
        s(r, "Gm7 横按", "G", "m7", "", a(3, 5, 3, 3, 3, 3), a(1, 3, 1, 1, 1, 1), 3, 4, "barre",
                "第 3 品横按，第 5 弦 5 品。");
        s(r, "开放 Am7 和弦", "A", "m7", "", a(-1, 0, 2, 0, 1, 0), a(0, 0, 2, 0, 1, 0), 1, 1, "open",
                "比 Am 更松弛，适合流行与民谣。");
        s(r, "Bm7 封闭和弦", "B", "m7", "", a(-1, 2, 4, 2, 3, 2), a(0, 1, 3, 1, 2, 1), 2, 4, "barre",
                "第 2 品横按，第 4 弦 4 品、第 2 弦 3 品。");

        // sus、add、dim、aug、9 与 slash chord
        addSuspendedAndAddedShapes(r);
        addAlteredAndExtendedShapes(r);
        addSlashShapes(r);
    }

    private static void addSuspendedAndAddedShapes(ChordRepository r) {
        s(r, "Csus2 开放按法", "C", "sus2", "", a(-1, 3, 0, 0, 3, 3), a(0, 1, 0, 0, 3, 4), 1, 1, "open", "开放、明亮，适合与 C 和 Cadd9 互换。");
        s(r, "Dsus2 开放按法", "D", "sus2", "", a(-1, -1, 0, 2, 3, 0), a(0, 0, 0, 1, 3, 0), 1, 1, "open", "第 1 弦空弦形成二度色彩。");
        s(r, "Esus2 开放按法", "E", "sus2", "", a(0, 2, 4, 4, 0, 0), a(0, 1, 3, 4, 0, 0), 1, 3, "open", "保留开放 E 低音，高把位加入 F#。");
        s(r, "Gsus2 常见按法", "G", "sus2", "", a(3, -1, 0, 2, 3, 3), a(2, 0, 0, 1, 3, 4), 1, 2, "open", "第 5 弦不弹，突出 G、A、D。");
        s(r, "Asus2 开放按法", "A", "sus2", "", a(-1, 0, 2, 2, 0, 0), a(0, 0, 1, 2, 0, 0), 1, 1, "open", "A 调民谣中非常常见。");

        s(r, "Csus4 开放按法", "C", "sus4", "", a(-1, 3, 3, 0, 1, 1), a(0, 3, 4, 0, 1, 1), 1, 2, "open", "把三度音替换为四度音 F。");
        s(r, "Dsus4 开放按法", "D", "sus4", "", a(-1, -1, 0, 2, 3, 3), a(0, 0, 0, 1, 3, 4), 1, 1, "open", "常与 D 和 Dsus2 交替。");
        s(r, "Esus4 开放按法", "E", "sus4", "", a(0, 2, 2, 2, 0, 0), a(0, 1, 2, 3, 0, 0), 1, 1, "open", "E 调歌曲常用的挂四解决。");
        s(r, "Gsus4 开放按法", "G", "sus4", "", a(3, 3, 0, 0, 1, 3), a(3, 4, 0, 0, 1, 2), 1, 2, "open", "第 5、2 弦提供 C 音。");
        s(r, "Asus4 开放按法", "A", "sus4", "", a(-1, 0, 2, 2, 3, 0), a(0, 0, 1, 2, 3, 0), 1, 1, "open", "常与 A、Asus2 组成民谣装饰。");

        s(r, "Cadd9 开放按法", "C", "add9", "", a(-1, 3, 2, 0, 3, 0), a(0, 3, 2, 0, 4, 0), 1, 1, "open", "在 C 大三和弦上加入 D 音。");
        s(r, "Dadd9 常见按法", "D", "add9", "", a(-1, -1, 4, 2, 3, 0), a(0, 0, 3, 1, 2, 0), 1, 2, "open", "包含 D、F#、A、E，开放一弦提供九度。");
        s(r, "Eadd9 开放按法", "E", "add9", "", a(0, 2, 4, 1, 0, 0), a(0, 2, 4, 1, 0, 0), 1, 3, "open", "开放 E 上加入 F#，适合慢速分解。");
        s(r, "Gadd9 开放按法", "G", "add9", "", a(3, 0, 0, 2, 0, 3), a(2, 0, 0, 1, 0, 3), 1, 2, "open", "在 G 大三和弦上加入 A 音。");
        s(r, "Aadd9 开放按法", "A", "add9", "", a(-1, 0, 2, 4, 2, 0), a(0, 0, 1, 4, 2, 0), 1, 2, "open", "第 3 弦 4 品提供九度 B。");
    }

    private static void addAlteredAndExtendedShapes(ChordRepository r) {
        s(r, "Cdim 常见按法", "C", "dim", "", a(-1, 3, 4, 5, 4, -1), a(0, 1, 2, 4, 3, 0), 3, 3, "jazz", "四根中高音弦形成紧张的减和弦色彩。");
        s(r, "Ddim 常见按法", "D", "dim", "", a(-1, 5, 6, 7, 6, -1), a(0, 1, 2, 4, 3, 0), 5, 3, "jazz", "同一形状上移两品得到 Ddim。");
        s(r, "Edim 常见按法", "E", "dim", "", a(-1, 7, 8, 9, 8, -1), a(0, 1, 2, 4, 3, 0), 7, 4, "jazz", "适合作为 F 或 Em 附近的经过和弦。");
        s(r, "Fdim 常见按法", "F", "dim", "", a(-1, 8, 9, 10, 9, -1), a(0, 1, 2, 4, 3, 0), 8, 4, "jazz", "高把位减和弦形状。");
        s(r, "Gdim 常见按法", "G", "dim", "", a(-1, 10, 11, 12, 11, -1), a(0, 1, 2, 4, 3, 0), 10, 5, "jazz", "适合练习半音上行经过。");
        s(r, "Adim 开放按法", "A", "dim", "", a(-1, 0, 1, 2, 1, -1), a(0, 0, 1, 3, 2, 0), 1, 2, "open", "开放 A 低音配合 C 与 Eb。");
        s(r, "Bdim 常见按法", "B", "dim", "", a(-1, 2, 3, 4, 3, -1), a(0, 1, 2, 4, 3, 0), 2, 3, "jazz", "第 6、1 弦不弹，中间四弦完成减和弦。");

        s(r, "Caug 常见按法", "C", "aug", "", a(-1, 3, 2, 1, 1, 0), a(0, 4, 3, 1, 2, 0), 1, 3, "jazz", "C、E、G# 形成向外扩张的增和弦。");
        s(r, "Daug 开放按法", "D", "aug", "", a(-1, -1, 0, 3, 3, 2), a(0, 0, 0, 3, 4, 2), 1, 2, "open", "保留开放 D，加入 A#。");
        s(r, "Eaug 常见按法", "E", "aug", "", a(0, 3, 2, 1, 1, 0), a(0, 4, 3, 1, 2, 0), 1, 3, "jazz", "与 Caug/E 同音集合，低音为 E。");
        s(r, "Faug 常见按法", "F", "aug", "", a(-1, -1, 3, 2, 2, 1), a(0, 0, 3, 1, 2, 1), 1, 3, "jazz", "高四弦完成 F、A、C#。");
        s(r, "Gaug 开放按法", "G", "aug", "", a(3, 2, 1, 0, 0, 3), a(4, 3, 1, 0, 0, 2), 1, 3, "open", "第 4 弦 1 品提供 D#。");
        s(r, "Aaug 开放按法", "A", "aug", "", a(-1, 0, 3, 2, 2, 1), a(0, 0, 4, 2, 3, 1), 1, 3, "open", "A、C#、F 构成增三和弦。");
        s(r, "Baug 常见按法", "B", "aug", "", a(-1, 2, 1, 0, 0, 3), a(0, 2, 1, 0, 0, 4), 1, 3, "open", "开放弦提供 G 与 B，形成 Baug 色彩。");

        s(r, "C9 常见按法", "C", "9", "", a(-1, 3, 2, 3, 3, 3), a(0, 2, 1, 3, 3, 3), 1, 3, "jazz", "C7 基础上加入九度 D，适合布鲁斯和爵士。");
        s(r, "D9 常见按法", "D", "9", "", a(-1, 5, 4, 5, 5, -1), a(0, 2, 1, 3, 4, 0), 4, 3, "jazz", "中间四弦形成紧凑的 D9。");
        s(r, "G9 开放按法", "G", "9", "", a(3, 2, 0, 2, 0, 1), a(3, 2, 0, 4, 0, 1), 1, 2, "open", "开放弦较多，声音明亮。");
        s(r, "A9 开放按法", "A", "9", "", a(-1, 0, 2, 4, 2, 3), a(0, 0, 1, 4, 2, 3), 1, 2, "open", "A7 加入九度 B，常见于 blues 和 funk。");
    }

    private static void addSlashShapes(ChordRepository r) {
        s(r, "开放 C/E 和弦", "C", "maj", "E", a(0, 3, 2, 0, 1, 0), a(0, 3, 2, 0, 1, 0), 1, 1, "slash",
                "以 E 作为低音的 C 和弦，常用于低音线平滑连接。");
        s(r, "开放 C/G 和弦", "C", "maj", "G", a(3, 3, 2, 0, 1, 0), a(3, 4, 2, 0, 1, 0), 1, 2, "slash",
                "以 G 作为低音的 C 和弦，声音更饱满。");
        s(r, "开放 D/F# 和弦", "D", "maj", "F#", a(2, 0, 0, 2, 3, 2), a(1, 0, 0, 2, 4, 3), 1, 2, "slash",
                "以 F# 作为低音的 D 和弦，常用于 G、D、Em 之间的过渡。");
        s(r, "开放 G/B 和弦", "G", "maj", "B", a(-1, 2, 0, 0, 3, 3), a(0, 1, 0, 0, 3, 4), 1, 1, "slash",
                "以 B 作为低音的 G 和弦，适合 C、G、Am 之间的低音连接。");
        s(r, "开放 Am/C 和弦", "A", "m", "C", a(-1, 3, 2, 2, 1, 0), a(0, 3, 2, 4, 1, 0), 1, 2, "slash",
                "以 C 作为低音的 Am 和弦，是常见小三和弦第一转位。");
        s(r, "开放 F/A 和弦", "F", "maj", "A", a(-1, 0, 3, 2, 1, 1), a(0, 0, 3, 2, 1, 1), 1, 2, "slash",
                "以 A 作为低音的 F 和弦，比完整 F 横按更容易进入。");
    }

    private static void registerMovableBarreShapes(ChordRepository r) {
        for (String root : CHROMATIC_ROOTS) {
            addEBarre(r, root, "maj", a(0, 2, 2, 1, 0, 0), a(1, 3, 4, 2, 1, 1), 3, "E 型大横按");
            addEBarre(r, root, "m", a(0, 2, 2, 0, 0, 0), a(1, 3, 4, 1, 1, 1), 4, "E 型小横按");
            addEBarre(r, root, "7", a(0, 2, 0, 1, 0, 0), a(1, 3, 1, 2, 1, 1), 4, "E 型属七横按");
            addEBarre(r, root, "maj7", a(0, 2, 1, 1, 0, 0), a(1, 4, 2, 3, 1, 1), 4, "E 型大七横按");
            addEBarre(r, root, "m7", a(0, 2, 0, 0, 0, 0), a(1, 3, 1, 1, 1, 1), 4, "E 型小七横按");

            addABarre(r, root, "maj", a(-1, 0, 2, 2, 2, 0), a(0, 1, 3, 3, 3, 1), 3, "A 型大横按");
            addABarre(r, root, "m", a(-1, 0, 2, 2, 1, 0), a(0, 1, 3, 4, 2, 1), 4, "A 型小横按");
            addABarre(r, root, "7", a(-1, 0, 2, 0, 2, 0), a(0, 1, 3, 1, 4, 1), 4, "A 型属七横按");
            addABarre(r, root, "maj7", a(-1, 0, 2, 1, 2, 0), a(0, 1, 3, 2, 4, 1), 4, "A 型大七横按");
            addABarre(r, root, "m7", a(-1, 0, 2, 0, 1, 0), a(0, 1, 3, 1, 2, 1), 4, "A 型小七横按");
        }
    }

    private static void registerMovableDiminishedShapes(ChordRepository r) {
        for (String root : CHROMATIC_ROOTS) {
            int rootFret = aStringRootFret(root);
            s(r, root + "dim 移动减和弦", root, "dim", "",
                    apply(rootFret, a(-1, 0, 1, 2, 1, -1)),
                    a(0, 1, 2, 4, 3, 0),
                    rootFret,
                    4,
                    "jazz",
                    "可移动减和弦形状，根音在第 5 弦，适合练习经过和弦。");
        }
    }

    private static void addEBarre(ChordRepository r, String root, String qualityId, int[] relativeFrets, int[] fingers, int difficulty, String label) {
        int rootFret = eStringRootFret(root);
        s(r, root + suffix(qualityId) + " " + label, root, qualityId, "", apply(rootFret, relativeFrets), fingers,
                rootFret, difficulty, "barre", "可移动横按形状，根音在第 6 弦。");
    }

    private static void addABarre(ChordRepository r, String root, String qualityId, int[] relativeFrets, int[] fingers, int difficulty, String label) {
        int rootFret = aStringRootFret(root);
        s(r, root + suffix(qualityId) + " " + label, root, qualityId, "", apply(rootFret, relativeFrets), fingers,
                rootFret, difficulty, "barre", "可移动横按形状，根音在第 5 弦。");
    }

    private static int eStringRootFret(String root) {
        int semitoneFromE = Math.floorMod(semitone(root) - semitone("E"), 12);
        return semitoneFromE == 0 ? 12 : semitoneFromE;
    }

    private static int aStringRootFret(String root) {
        int semitoneFromA = Math.floorMod(semitone(root) - semitone("A"), 12);
        return semitoneFromA == 0 ? 12 : semitoneFromA;
    }

    private static int semitone(String root) {
        if ("C".equals(root)) return 0;
        if ("C#".equals(root) || "Db".equals(root)) return 1;
        if ("D".equals(root)) return 2;
        if ("D#".equals(root) || "Eb".equals(root)) return 3;
        if ("E".equals(root)) return 4;
        if ("F".equals(root)) return 5;
        if ("F#".equals(root) || "Gb".equals(root)) return 6;
        if ("G".equals(root)) return 7;
        if ("G#".equals(root) || "Ab".equals(root)) return 8;
        if ("A".equals(root)) return 9;
        if ("A#".equals(root) || "Bb".equals(root)) return 10;
        return 11;
    }

    private static int[] apply(int baseFret, int[] relativeFrets) {
        int[] result = new int[relativeFrets.length];
        for (int i = 0; i < relativeFrets.length; i++) {
            result[i] = relativeFrets[i] < 0 ? -1 : baseFret + relativeFrets[i];
        }
        return result;
    }

    private static void q(ChordRepository r, String id, String displayName, String chineseName, int[] intervals, String[] labels,
                          String category, int difficulty, String description) {
        r.registerQuality(new ChordQuality(id, displayName, chineseName, intervals, labels, category, difficulty, description));
    }

    private static void s(ChordRepository r, String name, String root, String qualityId, String bassNote, int[] frets, int[] fingers,
                          int baseFret, int difficulty, String shapeType, String note) {
        r.registerShape(new ChordShape(name, root, qualityId, bassNote, frets, fingers, baseFret, difficulty, shapeType, note));
    }

    private static int[] a(int... values) {
        return values;
    }

    private static String[] labels(String... values) {
        return values;
    }

    private static String suffix(String qualityId) {
        return "maj".equals(qualityId) ? "" : qualityId;
    }
}
