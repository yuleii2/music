package com.k2.music;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_FOLDER = 3101;
    private static final String EXPORT_PREFS = "export_preferences";
    private static final String KEY_EXPORT_FOLDER = "export_folder";
    private static final String KEY_EXPORT_NAME = "export_name";
    private static final String KEY_EXPORT_FORMAT = "export_format";

    private static final int COLOR_BG = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF101412;
    private static final int COLOR_SUBTLE = 0xFF5E6761;
    private static final int COLOR_SURFACE = 0xFFF2F3F5;
    private static final int COLOR_LINE = 0xFFE2E5E8;
    private static final int COLOR_ACCENT = 0xFF4F7DFF;

    private final ChordRepository repository = new ChordRepository();
    private final ChordAudioPlayer audioPlayer = new ChordAudioPlayer();
    private final DecelerateInterpolator easeOut = new DecelerateInterpolator();
    private UserChordStore userStore;
    private SharedPreferences exportPreferences;

    private ScrollView scrollView;
    private EditText input;
    private TextView message;
    private TextView favoritesTitle;
    private TextView historyTitle;
    private TextView title;
    private TextView summary;
    private TextView notes;
    private TextView description;
    private TextView glossary;
    private TextView voicingInfo;
    private TextView audioStatus;
    private TextView exportPathText;
    private TextView exportStatus;
    private EditText exportNameInput;
    private LinearLayout favoritesRow;
    private LinearLayout historyRow;
    private LinearLayout voicingButtons;
    private LinearLayout resultSurface;
    private LinearLayout voicingSurface;
    private LinearLayout exportSurface;
    private FretboardView fretboardView;
    private Button favoriteButton;
    private Button playVoicingButton;
    private Button playNotesButton;
    private Button jpgButton;
    private Button pngButton;

    private Chord selectedChord;
    private Voicing selectedVoicing;
    private Uri exportFolderUri;
    private String exportFormat = VoicingImageExporter.FORMAT_JPG;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userStore = new UserChordStore(this);
        exportPreferences = getSharedPreferences(EXPORT_PREFS, MODE_PRIVATE);
        String savedFolder = exportPreferences.getString(KEY_EXPORT_FOLDER, "");
        if (!savedFolder.isEmpty()) {
            exportFolderUri = Uri.parse(savedFolder);
        }
        exportFormat = exportPreferences.getString(KEY_EXPORT_FORMAT, VoicingImageExporter.FORMAT_JPG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(COLOR_BG);
            getWindow().setNavigationBarColor(COLOR_BG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(buildContent());
        query("C", false);
    }

    @Override
    protected void onDestroy() {
        audioPlayer.stop();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        exportFolderUri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(exportFolderUri, flags);
        } catch (SecurityException ignored) {
            // Some document providers grant temporary access only. Export still works in the current session.
        }
        exportPreferences.edit().putString(KEY_EXPORT_FOLDER, exportFolderUri.toString()).apply();
        updateExportControls();
        Toast.makeText(this, "已选择导出路径", Toast.LENGTH_SHORT).show();
    }

    private ScrollView buildContent() {
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(30));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(header());
        root.addView(searchPanel());
        root.addView(featureGrid());
        root.addView(exampleChips());
        root.addView(libraryPanel());

        message = smallText("");
        message.setTextColor(COLOR_ACCENT);
        message.setPadding(0, dp(8), 0, 0);
        root.addView(message);

        resultSurface = surface();
        title = text("", 32, true);
        resultSurface.addView(title);
        summary = smallText("");
        resultSurface.addView(summary);
        notes = smallText("");
        resultSurface.addView(notes);
        description = smallText("");
        resultSurface.addView(description);

        LinearLayout resultActions = new LinearLayout(this);
        resultActions.setOrientation(LinearLayout.VERTICAL);
        resultActions.setPadding(0, dp(8), 0, 0);
        favoriteButton = chipButton("收藏当前和弦");
        favoriteButton.setOnClickListener(view -> toggleCurrentFavorite());
        resultActions.addView(favoriteButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        ));
        LinearLayout playActions = horizontalRow();
        playActions.setPadding(0, dp(10), 0, 0);
        playVoicingButton = chipButton("试听按法");
        playVoicingButton.setOnClickListener(view -> playCurrentVoicing());
        playActions.addView(playVoicingButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        playNotesButton = chipButton("组成音试听");
        playNotesButton.setOnClickListener(view -> playChordNotes());
        LinearLayout.LayoutParams playNotesParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        playNotesParams.setMargins(dp(10), 0, 0, 0);
        playActions.addView(playNotesButton, playNotesParams);
        resultActions.addView(playActions);
        resultSurface.addView(resultActions);
        root.addView(resultSurface, sectionParams());

        voicingSurface = surface();
        voicingSurface.addView(sectionTitle("常见指法"));
        voicingButtons = horizontalRow();
        voicingButtons.setPadding(0, dp(8), 0, dp(6));
        HorizontalScrollView voicingScroll = new HorizontalScrollView(this);
        voicingScroll.setHorizontalScrollBarEnabled(false);
        voicingScroll.addView(voicingButtons);
        voicingSurface.addView(voicingScroll);

        fretboardView = new FretboardView(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
        );
        boardParams.setMargins(0, dp(12), 0, dp(8));
        voicingSurface.addView(fretboardView, boardParams);

        voicingInfo = smallText("");
        voicingSurface.addView(voicingInfo);
        glossary = smallText("术语：根音是和弦名称的基础音；三度音决定大/小色彩；五度音提供稳定支撑；O 表示空弦，X 表示闷弦。");
        voicingSurface.addView(glossary);
        audioStatus = smallText("试听状态：等待操作");
        voicingSurface.addView(audioStatus);
        root.addView(voicingSurface, sectionParams());

        exportSurface = exportPanel();
        root.addView(exportSurface, sectionParams());

        refreshLibrarySections();
        updateExportControls();
        return scrollView;
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, dp(18));

        TextView appTitle = text("吉他和弦字典", 34, true);
        header.addView(appTitle);

        TextView subtitle = smallText("查和弦、看指法、听声音，也能批量导出指法图");
        subtitle.setTextColor(COLOR_SUBTLE);
        header.addView(subtitle);
        return header;
    }

    private View searchPanel() {
        LinearLayout panel = surface();
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout searchRow = horizontalRow();
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(21);
        input.setHint("例如 C、Am、G7、Fmaj7");
        input.setSelectAllOnFocus(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundedStroke(0xFFFFFFFF, COLOR_ACCENT, 16, 1));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                query(input.getText().toString());
                return true;
            }
            return false;
        });
        searchRow.addView(input, new LinearLayout.LayoutParams(0, dp(58), 1f));

        Button queryButton = primaryButton("查询");
        queryButton.setOnClickListener(view -> query(input.getText().toString()));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(94), dp(58));
        buttonParams.setMargins(dp(10), 0, 0, 0);
        searchRow.addView(queryButton, buttonParams);
        panel.addView(searchRow);
        return panel;
    }

    private View featureGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(16), 0, 0);

        LinearLayout firstRow = horizontalRow();
        firstRow.addView(actionTile("和弦查询", "组成音 / 指板图", view -> input.requestFocus()), gridCellParams(false));
        firstRow.addView(actionTile("批量导出", "JPG / PNG", view -> scrollTo(exportSurface)), gridCellParams(true));
        grid.addView(firstRow);

        LinearLayout secondRow = horizontalRow();
        secondRow.setPadding(0, dp(10), 0, 0);
        secondRow.addView(actionTile("收藏", "常用和弦", view -> scrollTo(favoritesTitle)), gridCellParams(false));
        secondRow.addView(actionTile("声音试听", "按法 / 组成音", view -> playCurrentVoicing()), gridCellParams(true));
        grid.addView(secondRow);
        return grid;
    }

    private View exampleChips() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(18), 0, 0);
        wrap.addView(sectionTitle("推荐和弦"));

        HorizontalScrollView examplesScroll = new HorizontalScrollView(this);
        examplesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout examplesRow = horizontalRow();
        examplesScroll.addView(examplesRow);
        for (String example : repository.examples()) {
            Button exampleButton = chipButton(example);
            exampleButton.setOnClickListener(view -> {
                input.setText(example);
                query(example);
            });
            examplesRow.addView(exampleButton, chipParams());
        }
        wrap.addView(examplesScroll);
        return wrap;
    }

    private View libraryPanel() {
        LinearLayout library = new LinearLayout(this);
        library.setOrientation(LinearLayout.VERTICAL);
        library.setPadding(0, dp(16), 0, 0);

        favoritesTitle = sectionTitle("收藏：暂无");
        library.addView(favoritesTitle);
        favoritesRow = horizontalRow();
        HorizontalScrollView favoritesScroll = new HorizontalScrollView(this);
        favoritesScroll.setHorizontalScrollBarEnabled(false);
        favoritesScroll.addView(favoritesRow);
        library.addView(favoritesScroll);

        historyTitle = sectionTitle("最近查询：暂无");
        historyTitle.setPadding(0, dp(14), 0, dp(8));
        library.addView(historyTitle);
        historyRow = horizontalRow();
        HorizontalScrollView historyScroll = new HorizontalScrollView(this);
        historyScroll.setHorizontalScrollBarEnabled(false);
        historyScroll.addView(historyRow);
        library.addView(historyScroll);
        return library;
    }

    private LinearLayout exportPanel() {
        LinearLayout panel = surface();
        panel.addView(sectionTitle("批量导出指法图"));
        panel.addView(smallText("选择导出路径、图片格式和文件名前缀，可导出当前和弦或收藏和弦的全部指法。"));

        exportNameInput = new EditText(this);
        exportNameInput.setSingleLine(true);
        exportNameInput.setTextSize(16);
        exportNameInput.setHint("自定义文件名前缀");
        exportNameInput.setText(exportPreferences.getString(KEY_EXPORT_NAME, "chord-fingering"));
        exportNameInput.setPadding(dp(14), 0, dp(14), 0);
        exportNameInput.setBackground(roundedStroke(0xFFFFFFFF, COLOR_LINE, 16, 1));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        nameParams.setMargins(0, dp(14), 0, dp(10));
        panel.addView(exportNameInput, nameParams);

        LinearLayout formatRow = horizontalRow();
        jpgButton = chipButton("JPG");
        jpgButton.setOnClickListener(view -> setExportFormat(VoicingImageExporter.FORMAT_JPG));
        pngButton = chipButton("PNG");
        pngButton.setOnClickListener(view -> setExportFormat(VoicingImageExporter.FORMAT_PNG));
        formatRow.addView(jpgButton, chipParams());
        formatRow.addView(pngButton, chipParams());

        Button folderButton = chipButton("选择路径");
        folderButton.setOnClickListener(view -> chooseExportFolder());
        formatRow.addView(folderButton, chipParams());
        panel.addView(formatRow);

        exportPathText = smallText("");
        panel.addView(exportPathText);

        LinearLayout exportActions = horizontalRow();
        exportActions.setPadding(0, dp(12), 0, 0);
        Button exportCurrentButton = primaryButton("导出当前和弦");
        exportCurrentButton.setOnClickListener(view -> exportCurrentChord());
        exportActions.addView(exportCurrentButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button exportFavoritesButton = secondaryButton("导出收藏");
        exportFavoritesButton.setOnClickListener(view -> exportFavoriteChords());
        LinearLayout.LayoutParams favoriteExportParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        favoriteExportParams.setMargins(dp(10), 0, 0, 0);
        exportActions.addView(exportFavoritesButton, favoriteExportParams);
        panel.addView(exportActions);

        exportStatus = smallText("导出状态：等待选择路径");
        panel.addView(exportStatus);
        return panel;
    }

    private void query(String rawInput) {
        query(rawInput, true);
    }

    private void query(String rawInput, boolean recordHistory) {
        ChordRepository.LookupResult result = repository.find(rawInput);
        if (!result.recognized) {
            showError(result.message);
            return;
        }
        selectedChord = result.chord;
        selectedVoicing = selectedChord.voicings.isEmpty() ? null : selectedChord.voicings.get(0);
        input.setText(selectedChord.symbol);
        message.setText(result.message == null ? "" : result.message);
        title.setText(selectedChord.chineseName + "  " + selectedChord.symbol);
        summary.setText(summaryText(selectedChord));
        notes.setText("组成音：" + join(selectedChord.notes) + "\n音程结构：" + join(selectedChord.intervals));
        description.setText(selectedChord.description);
        audioStatus.setText("试听状态：等待操作");
        if (recordHistory) {
            userStore.addHistory(selectedChord.symbol);
        }
        updateFavoriteButton();
        refreshLibrarySections();
        buildVoicingButtons();
        updateVoicing(selectedVoicing);
        animateRefresh(resultSurface);
    }

    private void showError(String text) {
        selectedChord = null;
        selectedVoicing = null;
        message.setText(text + "\n示例：C、Am、G7、Fmaj7");
        title.setText("未找到和弦");
        summary.setText("");
        notes.setText("");
        description.setText("");
        audioStatus.setText("试听状态：等待操作");
        voicingInfo.setText("当前没有可显示的指法。");
        voicingButtons.removeAllViews();
        fretboardView.setVoicing(null);
        updateFavoriteButton();
        animateRefresh(resultSurface);
    }

    private void buildVoicingButtons() {
        voicingButtons.removeAllViews();
        if (selectedChord == null || selectedChord.voicings.isEmpty()) {
            return;
        }
        for (Voicing voicing : selectedChord.voicings) {
            Button button = chipButton(voicing.name);
            button.setOnClickListener(view -> updateVoicing(voicing));
            voicingButtons.addView(button, chipParams());
        }
    }

    private void updateVoicing(Voicing voicing) {
        selectedVoicing = voicing;
        fretboardView.setVoicing(voicing);
        animateBoard();
        if (voicing == null) {
            voicingInfo.setText("当前和弦暂无可用吉他按法。");
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("当前按法：").append(voicing.name).append('\n');
        builder.append("按法：").append(voicing.fretPattern()).append("    难度：").append(voicing.difficulty).append('\n');
        builder.append("起始品位：").append(voicing.startFret).append("    显示品位：").append(voicing.displayFrets).append('\n');
        builder.append("标记：")
                .append(voicing.recommended ? "推荐" : "候选")
                .append(voicing.simplified ? " / 简化" : "")
                .append(voicing.barre ? " / 需要横按" : "")
                .append(hasOpenString(voicing) ? " / 含空弦" : "")
                .append(hasMutedString(voicing) ? " / 含闷弦" : "")
                .append('\n');
        builder.append("实际弦音：").append(joinStringArray(voicing.stringNotes)).append('\n');
        builder.append(voicing.description);
        if (voicing.barre) {
            builder.append("\n提示：该按法可能需要横按，初学者会觉得较难。");
        }
        voicingInfo.setText(builder.toString());
    }

    private void toggleCurrentFavorite() {
        if (selectedChord == null) {
            Toast.makeText(this, "请先查询一个和弦", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean favorite = userStore.toggleFavorite(selectedChord.symbol);
        updateFavoriteButton();
        refreshLibrarySections();
        Toast.makeText(this, favorite ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    private void updateFavoriteButton() {
        if (favoriteButton == null) {
            return;
        }
        if (selectedChord == null) {
            favoriteButton.setEnabled(false);
            favoriteButton.setText("收藏当前和弦");
            return;
        }
        favoriteButton.setEnabled(true);
        favoriteButton.setText(userStore.isFavorite(selectedChord.symbol) ? "取消收藏" : "收藏当前和弦");
    }

    private void refreshLibrarySections() {
        if (favoritesRow == null || historyRow == null) {
            return;
        }
        fillSymbolRow(favoritesTitle, favoritesRow, "收藏", userStore.favorites());
        fillSymbolRow(historyTitle, historyRow, "最近查询", userStore.history());
    }

    private void fillSymbolRow(TextView titleView, LinearLayout row, String title, List<String> symbols) {
        row.removeAllViews();
        if (symbols.isEmpty()) {
            titleView.setText(title + "：暂无");
            return;
        }
        titleView.setText(title + "：" + symbols.size() + " 个");
        for (String symbol : symbols) {
            Button symbolButton = chipButton(symbol);
            symbolButton.setOnClickListener(view -> query(symbol));
            row.addView(symbolButton, chipParams());
        }
    }

    private void playCurrentVoicing() {
        if (selectedVoicing != null) {
            playNotes(selectedVoicing.playableMidiNotes(), "当前按法试听");
        } else if (selectedChord != null) {
            playNotes(selectedChord.fallbackMidiNotes(), "组成音试听");
            Toast.makeText(this, "按法缺失，改为试听组成音", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "请先查询一个和弦", Toast.LENGTH_SHORT).show();
        }
    }

    private void playChordNotes() {
        if (selectedChord == null) {
            Toast.makeText(this, "请先查询一个和弦", Toast.LENGTH_SHORT).show();
            return;
        }
        playNotes(selectedChord.fallbackMidiNotes(), "组成音试听");
    }

    private void playNotes(int[] midiNotes, String label) {
        boolean started = audioPlayer.play(midiNotes);
        if (started) {
            audioStatus.setText("试听状态：正在播放 " + label);
            Toast.makeText(this, "正在播放 " + label, Toast.LENGTH_SHORT).show();
            audioStatus.postDelayed(() -> audioStatus.setText("试听状态：" + label + "已结束"), 2100);
        } else {
            audioStatus.setText("试听状态：试听暂不可用");
            Toast.makeText(this, "试听暂不可用", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseExportFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_EXPORT_FOLDER);
    }

    private void setExportFormat(String format) {
        exportFormat = format;
        exportPreferences.edit().putString(KEY_EXPORT_FORMAT, exportFormat).apply();
        updateExportControls();
    }

    private void updateExportControls() {
        if (exportPathText != null) {
            exportPathText.setText(exportFolderUri == null
                    ? "导出路径：未选择"
                    : "导出路径：" + readableUri(exportFolderUri));
        }
        if (exportStatus != null) {
            exportStatus.setText(exportFolderUri == null ? "导出状态：等待选择路径" : "导出状态：等待导出");
        }
        if (jpgButton != null) {
            styleFormatButton(jpgButton, VoicingImageExporter.FORMAT_JPG.equalsIgnoreCase(exportFormat));
        }
        if (pngButton != null) {
            styleFormatButton(pngButton, VoicingImageExporter.FORMAT_PNG.equalsIgnoreCase(exportFormat));
        }
    }

    private void exportCurrentChord() {
        if (selectedChord == null) {
            Toast.makeText(this, "请先查询一个和弦", Toast.LENGTH_SHORT).show();
            return;
        }
        exportChordBatch(Collections.singletonList(selectedChord), "当前和弦");
    }

    private void exportFavoriteChords() {
        List<Chord> chords = new ArrayList<>();
        for (String symbol : userStore.favorites()) {
            ChordRepository.LookupResult result = repository.find(symbol);
            if (result.recognized) {
                chords.add(result.chord);
            }
        }
        if (chords.isEmpty()) {
            Toast.makeText(this, "收藏为空，暂无可导出的和弦", Toast.LENGTH_SHORT).show();
            return;
        }
        exportChordBatch(chords, "收藏和弦");
    }

    private void exportChordBatch(List<Chord> chords, String label) {
        if (exportFolderUri == null) {
            Toast.makeText(this, "请先选择导出路径", Toast.LENGTH_SHORT).show();
            chooseExportFolder();
            return;
        }
        String baseName = exportNameInput.getText().toString().trim();
        if (baseName.isEmpty()) {
            baseName = "chord-fingering";
            exportNameInput.setText(baseName);
        }
        exportPreferences.edit()
                .putString(KEY_EXPORT_NAME, baseName)
                .putString(KEY_EXPORT_FORMAT, exportFormat)
                .apply();

        List<VoicingImageExporter.ExportItem> items = new ArrayList<>();
        for (Chord chord : chords) {
            for (int i = 0; i < chord.voicings.size(); i++) {
                items.add(new VoicingImageExporter.ExportItem(chord, chord.voicings.get(i), i));
            }
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "当前没有可导出的指法", Toast.LENGTH_SHORT).show();
            return;
        }

        exportStatus.setText("导出状态：正在导出 " + items.size() + " 张图片...");
        VoicingImageExporter.ExportSummary result = VoicingImageExporter.export(
                this,
                exportFolderUri,
                baseName,
                exportFormat,
                items
        );
        String status = String.format(Locale.CHINA,
                "导出状态：%s完成，成功 %d 张，失败 %d 张",
                label,
                result.exported,
                result.failed);
        if (!result.fileNames.isEmpty()) {
            status += "\n首个文件：" + result.fileNames.get(0);
        }
        exportStatus.setText(status);
        Toast.makeText(this, "导出完成：" + result.exported + " 张", Toast.LENGTH_SHORT).show();
    }

    private String summaryText(Chord chord) {
        if (chord.bassNote != null && !chord.bassNote.isEmpty()) {
            return String.format(Locale.CHINA, "根音：%s    低音：%s    类型：%s",
                    chord.root, chord.bassNote, chord.quality);
        }
        return String.format(Locale.CHINA, "根音：%s    类型：%s", chord.root, chord.quality);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(COLOR_TEXT);
        view.setLineSpacing(dp(2), 1.05f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private TextView smallText(String value) {
        TextView view = text(value, 15, false);
        view.setTextColor(COLOR_SUBTLE);
        view.setPadding(0, dp(6), 0, dp(2));
        return view;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 22, true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private LinearLayout surface() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(rounded(COLOR_SURFACE, 18));
        return layout;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private View actionTile(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.setBackground(ripple(COLOR_SURFACE, 0xFFE7E9ED, 14));
        tile.setOnClickListener(listener);
        tile.setClickable(true);

        TextView titleView = text(title, 18, true);
        tile.addView(titleView);
        TextView subtitleView = smallText(subtitle);
        subtitleView.setTextSize(13);
        tile.addView(subtitleView);
        return tile;
    }

    private Button primaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(ripple(COLOR_TEXT, 0xFF2C3330, 16));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_TEXT);
        button.setBackground(ripple(0xFFFFFFFF, 0xFFE9ECF1, 16));
        return button;
    }

    private Button chipButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(14);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setMinWidth(dp(64));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(COLOR_TEXT);
        button.setBackground(ripple(0xFFFFFFFF, 0xFFE8EBF0, 14));
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private void styleFormatButton(Button button, boolean selected) {
        button.setTextColor(selected ? 0xFFFFFFFF : COLOR_TEXT);
        button.setBackground(ripple(selected ? COLOR_ACCENT : 0xFFFFFFFF, selected ? 0xFF3F68D9 : 0xFFE8EBF0, 14));
    }

    private Drawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private Drawable roundedStroke(int color, int strokeColor, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private Drawable ripple(int color, int pressedColor, int radiusDp) {
        Drawable content = rounded(color, radiusDp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(ColorStateList.valueOf(pressedColor), content, null);
        }
        return content;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(18), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams gridCellParams(boolean right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(78), 1f);
        if (right) {
            params.setMargins(dp(10), 0, 0, 0);
        }
        return params;
    }

    private void animateRefresh(View view) {
        view.setAlpha(0.65f);
        view.setTranslationY(dp(8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(easeOut)
                .start();
    }

    private void animateBoard() {
        fretboardView.setAlpha(0.72f);
        fretboardView.setScaleX(0.985f);
        fretboardView.setScaleY(0.985f);
        fretboardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .setInterpolator(easeOut)
                .start();
    }

    private void scrollTo(View view) {
        if (view == null) {
            return;
        }
        scrollView.post(() -> scrollView.smoothScrollTo(0, Math.max(0, view.getTop() - dp(14))));
    }

    private String readableUri(Uri uri) {
        String last = uri.getLastPathSegment();
        return last == null || last.isEmpty() ? "已选择自定义路径" : last;
    }

    private String join(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private String joinStringArray(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(value == null ? "X" : value);
        }
        return builder.toString();
    }

    private boolean hasOpenString(Voicing voicing) {
        for (int fret : voicing.frets) {
            if (fret == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMutedString(Voicing voicing) {
        for (int fret : voicing.frets) {
            if (fret == Voicing.MUTED) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
