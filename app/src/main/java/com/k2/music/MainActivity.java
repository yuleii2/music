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
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
    private enum Page {
        HOME,
        LIBRARY,
        FAVORITES,
        ADD,
        PROFILE,
        DETAIL
    }

    private static final int REQUEST_EXPORT_FOLDER = 3101;
    private static final String EXPORT_PREFS = "export_preferences";
    private static final String KEY_EXPORT_FOLDER = "export_folder";
    private static final String KEY_EXPORT_NAME = "export_name";
    private static final String KEY_EXPORT_FORMAT = "export_format";

    private static final int COLOR_BG = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF101412;
    private static final int COLOR_SUBTLE = 0xFF5E6761;
    private static final int COLOR_SURFACE = 0xFFF2F3F5;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_LINE = 0xFFE2E5E8;
    private static final int COLOR_ACCENT = 0xFF4F7DFF;
    private static final int COLOR_ACCENT_SOFT = 0xFFEAF0FF;

    private final ChordRepository repository = new ChordRepository();
    private final ChordAudioPlayer audioPlayer = new ChordAudioPlayer();
    private final DecelerateInterpolator easeOut = new DecelerateInterpolator();

    private UserChordStore userStore;
    private SharedPreferences exportPreferences;

    private LinearLayout mainRoot;
    private FrameLayout contentContainer;
    private LinearLayout bottomNav;
    private Page currentPage = Page.HOME;
    private Page returnPage = Page.HOME;

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
        setupSystemBars();
        setContentView(buildShell());
        showHomePage();
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
            // Some document providers only grant temporary access. Export still works for this session.
        }
        exportPreferences.edit().putString(KEY_EXPORT_FOLDER, exportFolderUri.toString()).apply();
        updateExportControls();
        Toast.makeText(this, "已选择导出路径", Toast.LENGTH_SHORT).show();
    }

    private void setupSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(COLOR_BG);
            getWindow().setNavigationBarColor(COLOR_BG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private View buildShell() {
        mainRoot = new LinearLayout(this);
        mainRoot.setOrientation(LinearLayout.VERTICAL);
        mainRoot.setBackgroundColor(COLOR_BG);

        contentContainer = new FrameLayout(this);
        mainRoot.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(10), dp(7), dp(10), dp(8));
        bottomNav.setBackground(roundedStroke(COLOR_WHITE, COLOR_LINE, 0, 1));
        mainRoot.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(86)
        ));
        return mainRoot;
    }

    private void showHomePage() {
        currentPage = Page.HOME;
        LinearLayout root = pageRoot(dp(22), dp(22), dp(22), dp(26));
        root.addView(homeTopBar(false));
        root.addView(sectionHeader("为你推荐", "更多 〉", view -> showLibraryPage()), sectionParams(26));
        root.addView(buildChordGrid(recommendedChords(), true));
        setPageContent(scrollPage(root));
    }

    private View homeTopBar(boolean librarySelected) {
        LinearLayout bar = horizontalRow();
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.BOTTOM);

        tabs.addView(homeTab("推荐", !librarySelected, view -> showHomePage()));
        LinearLayout.LayoutParams libraryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        libraryParams.setMargins(dp(24), 0, 0, 0);
        tabs.addView(homeTab("和弦库", librarySelected, view -> showLibraryPage()), libraryParams);

        bar.addView(tabs, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView search = iconText("⌕", 34, false);
        search.setOnClickListener(view -> showAddPage());
        bar.addView(search, squareParams(48));

        TextView heart = iconText("♡", 33, false);
        heart.setOnClickListener(view -> showFavoritesPage());
        LinearLayout.LayoutParams heartParams = squareParams(48);
        heartParams.setMargins(dp(6), 0, 0, 0);
        bar.addView(heart, heartParams);
        return bar;
    }

    private View homeTab(String label, boolean selected, View.OnClickListener listener) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);
        tab.setMinimumWidth(dp("推荐".equals(label) ? 74 : 94));
        tab.setClickable(true);
        tab.setOnClickListener(listener);

        TextView title = text(label, selected ? 28 : 23, selected);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setMinWidth(dp("推荐".equals(label) ? 68 : 90));
        title.setTextColor(COLOR_TEXT);
        tab.addView(title);

        View underline = new View(this);
        underline.setBackgroundColor(selected ? COLOR_ACCENT : 0x00000000);
        LinearLayout.LayoutParams underlineParams = new LinearLayout.LayoutParams(dp(42), dp(3));
        underlineParams.setMargins(0, dp(8), 0, 0);
        tab.addView(underline, underlineParams);
        return tab;
    }

    private void showLibraryPage() {
        currentPage = Page.LIBRARY;
        LinearLayout root = pageRoot(dp(22), dp(22), dp(22), dp(26));
        root.addView(homeTopBar(true));

        root.addView(sectionTitle("全部和弦"), sectionParams(26));

        EditText searchInput = roundedInput("输入 C、Am、G7、Fmaj7 过滤");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        inputParams.setMargins(0, 0, 0, dp(16));
        root.addView(searchInput, inputParams);

        LinearLayout gridHost = new LinearLayout(this);
        gridHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(gridHost);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            String keyword = searchInput.getText().toString().trim().toLowerCase(Locale.US);
            List<Chord> chords = new ArrayList<>();
            for (Chord chord : repository.allChords()) {
                if (keyword.isEmpty()
                        || chord.symbol.toLowerCase(Locale.US).contains(keyword)
                        || chord.chineseName.toLowerCase(Locale.CHINA).contains(keyword)
                        || chord.quality.toLowerCase(Locale.CHINA).contains(keyword)) {
                    chords.add(chord);
                }
            }
            gridHost.removeAllViews();
            if (chords.isEmpty()) {
                gridHost.addView(emptyState("没有找到匹配和弦", "可以试试 C、Am、G7、Fmaj7"));
            } else {
                gridHost.addView(buildChordGrid(chords, false));
            }
        };
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String value = searchInput.getText().toString();
                ChordRepository.LookupResult result = repository.find(value);
                if (result.recognized) {
                    query(value);
                } else {
                    refresh[0].run();
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new SimpleTextWatcher(refresh[0]));
        refresh[0].run();
        setPageContent(scrollPage(root));
    }

    private void showFavoritesPage() {
        currentPage = Page.FAVORITES;
        LinearLayout root = pageRoot(dp(22), dp(24), dp(22), dp(26));
        root.addView(pageTitle("收藏", "本地保存的常用和弦"));

        List<Chord> favorites = chordsForSymbols(userStore.favorites());
        if (favorites.isEmpty()) {
            root.addView(emptyState("暂无收藏和弦", "可以去首页或添加页收藏常用和弦"), sectionParams(22));
            LinearLayout actions = horizontalRow();
            Button home = buildSecondaryActionButton("去首页");
            home.setOnClickListener(view -> showHomePage());
            actions.addView(home, new LinearLayout.LayoutParams(0, dp(52), 1f));
            Button add = buildPrimaryActionButton("添加和弦");
            add.setOnClickListener(view -> showAddPage());
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
            addParams.setMargins(dp(10), 0, 0, 0);
            actions.addView(add, addParams);
            root.addView(actions, sectionParams(16));
        } else {
            root.addView(buildFavoritesList(favorites), sectionParams(22));
        }
        setPageContent(scrollPage(root));
    }

    private void showAddPage() {
        currentPage = Page.ADD;
        LinearLayout root = pageRoot(dp(22), dp(24), dp(22), dp(26));
        root.addView(pageTitle("添加和弦", "输入和弦名称，查看指法并加入收藏"));

        LinearLayout panel = buildSectionCard();
        input = roundedInput("例如 C、Am、G7、Fmaj7");
        input.setSelectAllOnFocus(true);
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                query(input.getText().toString());
                return true;
            }
            return false;
        });
        panel.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));

        Button queryButton = buildPrimaryActionButton("查看和弦");
        queryButton.setOnClickListener(view -> query(input.getText().toString()));
        panel.addView(queryButton, sectionParams(12, dp(56)));

        message = smallText("");
        message.setTextColor(COLOR_ACCENT);
        panel.addView(message);
        root.addView(panel, sectionParams(22));

        root.addView(sectionTitle("示例输入"), sectionParams(22));
        root.addView(exampleChips());

        List<String> history = userStore.history();
        if (!history.isEmpty()) {
            historyTitle = sectionTitle("最近查询");
            root.addView(historyTitle, sectionParams(24));
            historyRow = horizontalRow();
            fillSymbolRow(historyTitle, historyRow, "最近查询", history);
            root.addView(horizontalScroll(historyRow));
        }
        setPageContent(scrollPage(root));
        input.requestFocus();
    }

    private void showProfilePage() {
        currentPage = Page.PROFILE;
        LinearLayout root = pageRoot(dp(22), dp(24), dp(22), dp(26));
        root.addView(pageTitle("我的", "和弦盒子，本地保存收藏和查询记录"));

        LinearLayout appInfo = buildSectionCard();
        appInfo.addView(text("和弦盒子", 24, true));
        appInfo.addView(smallText("原生 Android Java + View 实现。本地保存收藏、最近查询和导出设置。"));
        root.addView(appInfo, sectionParams(22));

        LinearLayout recent = buildSectionCard();
        recent.addView(sectionTitle("最近查询"));
        List<String> history = userStore.history();
        if (history.isEmpty()) {
            recent.addView(smallText("暂无最近查询"));
        } else {
            historyRow = horizontalRow();
            fillSymbolRow(null, historyRow, "最近查询", history);
            recent.addView(horizontalScroll(historyRow));
        }
        root.addView(recent, sectionParams(16));

        exportSurface = exportPanel();
        root.addView(exportSurface, sectionParams(16));

        LinearLayout about = buildSectionCard();
        about.addView(sectionTitle("关于软件"));
        about.addView(smallText("当前版本 1.3。用于查询吉他和弦、查看指法、试听声音，并批量导出指法图。"));
        root.addView(about, sectionParams(16));

        setPageContent(scrollPage(root));
    }

    private void showChordDetail(String symbol) {
        query(symbol);
    }

    private void showChordDetail(Chord chord) {
        if (chord == null) {
            showError("当前和弦不可用。");
            return;
        }
        if (currentPage != Page.DETAIL) {
            returnPage = currentPage;
        }
        currentPage = Page.DETAIL;
        selectedChord = chord;
        if (selectedVoicing == null || !selectedChord.voicings.contains(selectedVoicing)) {
            selectedVoicing = selectedChord.voicings.isEmpty() ? null : selectedChord.voicings.get(0);
        }

        LinearLayout root = pageRoot(dp(22), dp(18), dp(22), dp(26));
        root.addView(detailTopBar());

        title = text(displayName(selectedChord), 34, true);
        root.addView(title, sectionParams(24));
        summary = smallText(selectedChord.symbol + "  " + englishName(selectedChord));
        summary.setTextSize(18);
        summary.setPadding(0, dp(2), 0, 0);
        root.addView(summary);

        LinearLayout chips = horizontalRow();
        chips.setPadding(0, dp(14), 0, 0);
        chips.addView(buildChordInfoChip("根音", selectedChord.root), new LinearLayout.LayoutParams(0, dp(70), 1f));
        LinearLayout.LayoutParams middleChip = new LinearLayout.LayoutParams(0, dp(70), 1f);
        middleChip.setMargins(dp(10), 0, 0, 0);
        chips.addView(buildChordInfoChip("类型", selectedChord.quality), middleChip);
        LinearLayout.LayoutParams rightChip = new LinearLayout.LayoutParams(0, dp(70), 1f);
        rightChip.setMargins(dp(10), 0, 0, 0);
        chips.addView(buildChordInfoChip("组成音", joinWithSpace(selectedChord.notes)), rightChip);
        root.addView(chips);

        resultSurface = buildSectionCard();
        fretboardView = new FretboardView(this);
        resultSurface.addView(fretboardView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)
        ));
        root.addView(resultSurface, sectionParams(18));

        voicingSurface = new LinearLayout(this);
        voicingSurface.setOrientation(LinearLayout.VERTICAL);
        voicingButtons = horizontalRow();
        buildVoicingButtons();
        voicingSurface.addView(horizontalScroll(voicingButtons));

        playNotesButton = chipButton("试听组成音");
        playNotesButton.setOnClickListener(view -> playChordNotes());
        LinearLayout.LayoutParams notesButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        notesButtonParams.setMargins(0, dp(10), 0, 0);
        voicingSurface.addView(playNotesButton, notesButtonParams);
        root.addView(voicingSurface, sectionParams(16));

        LinearLayout infoCard = buildSectionCard();
        voicingInfo = smallText("");
        voicingInfo.setTextColor(COLOR_TEXT);
        voicingInfo.setTextSize(16);
        infoCard.addView(voicingInfo);
        notes = smallText("");
        description = smallText("");
        glossary = smallText("O 表示空弦，X 表示闷弦。");
        infoCard.addView(glossary);
        audioStatus = smallText("试听状态：等待操作");
        infoCard.addView(audioStatus);
        root.addView(infoCard, sectionParams(16));

        LinearLayout actions = horizontalRow();
        playVoicingButton = buildPrimaryActionButton("▶  试听按法");
        playVoicingButton.setOnClickListener(view -> playCurrentVoicing());
        actions.addView(playVoicingButton, new LinearLayout.LayoutParams(0, dp(58), 1f));

        favoriteButton = buildSecondaryActionButton("收藏");
        favoriteButton.setOnClickListener(view -> toggleCurrentFavorite());
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(0, dp(58), 1f);
        favoriteParams.setMargins(dp(12), 0, 0, 0);
        actions.addView(favoriteButton, favoriteParams);
        root.addView(actions, sectionParams(22));

        updateFavoriteButton();
        updateVoicing(selectedVoicing);
        setPageContent(scrollPage(root));
    }

    private View detailTopBar() {
        LinearLayout bar = horizontalRow();
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = iconText("‹", 38, false);
        back.setOnClickListener(view -> returnFromDetail());
        bar.addView(back, squareParams(48));

        TextView label = text("和弦详情", 19, true);
        label.setGravity(Gravity.CENTER);
        bar.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView share = iconText("⌯", 26, false);
        share.setTextColor(COLOR_TEXT);
        bar.addView(share, squareParams(48));
        return bar;
    }

    private void returnFromDetail() {
        Page target = returnPage == null || returnPage == Page.DETAIL ? Page.HOME : returnPage;
        switch (target) {
            case LIBRARY:
                showLibraryPage();
                break;
            case FAVORITES:
                showFavoritesPage();
                break;
            case ADD:
                showAddPage();
                break;
            case PROFILE:
                showProfilePage();
                break;
            case HOME:
            default:
                showHomePage();
                break;
        }
    }

    private LinearLayout exportPanel() {
        LinearLayout panel = buildSectionCard();
        panel.addView(sectionTitle("批量导出指法图"));
        panel.addView(smallText("选择导出路径、图片格式和文件名前缀，导出当前和弦或收藏和弦的全部指法。"));

        exportNameInput = roundedInput("自定义文件名前缀");
        exportNameInput.setText(exportPreferences.getString(KEY_EXPORT_NAME, "chord-fingering"));
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
        Button exportCurrentButton = buildPrimaryActionButton("导出当前和弦");
        exportCurrentButton.setOnClickListener(view -> exportCurrentChord());
        exportActions.addView(exportCurrentButton, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button exportFavoritesButton = buildSecondaryActionButton("导出收藏");
        exportFavoritesButton.setOnClickListener(view -> exportFavoriteChords());
        LinearLayout.LayoutParams favoriteExportParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        favoriteExportParams.setMargins(dp(10), 0, 0, 0);
        exportActions.addView(exportFavoritesButton, favoriteExportParams);
        panel.addView(exportActions);

        exportStatus = smallText("导出状态：等待选择路径");
        panel.addView(exportStatus);
        updateExportControls();
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
        if (input != null) {
            input.setText(selectedChord.symbol);
        }
        if (recordHistory) {
            userStore.addHistory(selectedChord.symbol);
        }
        if (message != null) {
            message.setText(result.message == null ? "" : result.message);
        }
        showChordDetail(selectedChord);
        refreshLibrarySections();
    }

    private void showError(String text) {
        selectedChord = null;
        selectedVoicing = null;
        if (currentPage != Page.ADD) {
            showAddPage();
        }
        if (message != null) {
            message.setText(text + "\n示例：C、Am、G7、Fmaj7");
        }
        if (audioStatus != null) {
            audioStatus.setText("试听状态：等待操作");
        }
        if (voicingInfo != null) {
            voicingInfo.setText("当前没有可显示的指法。");
        }
        if (voicingButtons != null) {
            voicingButtons.removeAllViews();
        }
        if (fretboardView != null) {
            fretboardView.setVoicing(null);
        }
        updateFavoriteButton();
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void buildVoicingButtons() {
        if (voicingButtons == null) {
            return;
        }
        voicingButtons.removeAllViews();
        if (selectedChord == null || selectedChord.voicings.isEmpty()) {
            return;
        }
        for (Voicing voicing : selectedChord.voicings) {
            Button button = chipButton(voicing.name == null || voicing.name.isEmpty() ? "按法" : voicing.name);
            button.setTag(voicing);
            button.setOnClickListener(view -> updateVoicing((Voicing) view.getTag()));
            voicingButtons.addView(button, chipParams());
        }
    }

    private void updateVoicing(Voicing voicing) {
        selectedVoicing = voicing;
        if (fretboardView != null) {
            fretboardView.setVoicing(voicing);
            animateBoard();
        }
        styleVoicingButtons();
        if (voicingInfo == null) {
            return;
        }
        if (voicing == null) {
            voicingInfo.setText("音程结构\n" + joinWithSpace(selectedChord.intervals)
                    + "\n\n学习提示\n当前和弦暂无可用吉他按法。"
                    + "\n\n和弦说明\n" + selectedChord.description);
            return;
        }
        voicingInfo.setText("音程结构\n"
                + joinWithSpace(selectedChord.intervals)
                + "\n\n学习提示\n"
                + learningTip(voicing)
                + "\n\n和弦说明\n"
                + selectedChord.description);
    }

    private void styleVoicingButtons() {
        if (voicingButtons == null) {
            return;
        }
        for (int i = 0; i < voicingButtons.getChildCount(); i++) {
            View child = voicingButtons.getChildAt(i);
            if (!(child instanceof Button)) {
                continue;
            }
            Button button = (Button) child;
            boolean selected = button.getTag() == selectedVoicing;
            button.setTextColor(selected ? COLOR_ACCENT : COLOR_TEXT);
            button.setBackground(ripple(selected ? COLOR_ACCENT_SOFT : COLOR_WHITE,
                    selected ? 0xFFDDE7FF : 0xFFE8EBF0,
                    14,
                    selected ? COLOR_ACCENT : COLOR_LINE,
                    1));
        }
    }

    private void toggleCurrentFavorite() {
        if (selectedChord == null) {
            Toast.makeText(this, "请先打开一个和弦详情", Toast.LENGTH_SHORT).show();
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
            favoriteButton.setText("收藏");
            return;
        }
        favoriteButton.setEnabled(true);
        favoriteButton.setText(userStore.isFavorite(selectedChord.symbol) ? "♡  取消收藏" : "♡  收藏");
    }

    private void refreshLibrarySections() {
        if (favoritesRow != null) {
            fillSymbolRow(favoritesTitle, favoritesRow, "收藏", userStore.favorites());
        }
        if (historyRow != null) {
            fillSymbolRow(historyTitle, historyRow, "最近查询", userStore.history());
        }
        if (currentPage == Page.FAVORITES) {
            showFavoritesPage();
        }
    }

    private void fillSymbolRow(TextView titleView, LinearLayout row, String title, List<String> symbols) {
        if (row == null) {
            return;
        }
        row.removeAllViews();
        if (symbols.isEmpty()) {
            if (titleView != null) {
                titleView.setText(title + "：暂无");
            }
            return;
        }
        if (titleView != null) {
            titleView.setText(title + "：" + symbols.size() + " 个");
        }
        for (String symbol : symbols) {
            Button symbolButton = chipButton(symbol);
            symbolButton.setOnClickListener(view -> showChordDetail(symbol));
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
            Toast.makeText(this, "请先打开一个和弦详情", Toast.LENGTH_SHORT).show();
        }
    }

    private void playChordNotes() {
        if (selectedChord == null) {
            Toast.makeText(this, "请先打开一个和弦详情", Toast.LENGTH_SHORT).show();
            return;
        }
        playNotes(selectedChord.fallbackMidiNotes(), "组成音试听");
    }

    private void playNotes(int[] midiNotes, String label) {
        boolean started = audioPlayer.play(midiNotes);
        if (started) {
            if (audioStatus != null) {
                audioStatus.setText("试听状态：正在播放 " + label);
                audioStatus.postDelayed(() -> audioStatus.setText("试听状态：" + label + "已结束"), 2100);
            }
            Toast.makeText(this, "正在播放 " + label, Toast.LENGTH_SHORT).show();
        } else {
            if (audioStatus != null) {
                audioStatus.setText("试听状态：试听暂不可用");
            }
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
            Toast.makeText(this, "请先打开一个和弦详情", Toast.LENGTH_SHORT).show();
            return;
        }
        exportChordBatch(Collections.singletonList(selectedChord), "当前和弦");
    }

    private void exportFavoriteChords() {
        List<Chord> chords = chordsForSymbols(userStore.favorites());
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
        if (exportNameInput == null) {
            Toast.makeText(this, "请先打开我的页设置导出选项", Toast.LENGTH_SHORT).show();
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

        if (exportStatus != null) {
            exportStatus.setText("导出状态：正在导出 " + items.size() + " 张图片...");
        }
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
        if (exportStatus != null) {
            exportStatus.setText(status);
        }
        Toast.makeText(this, "导出完成：" + result.exported + " 张", Toast.LENGTH_SHORT).show();
    }

    private List<Chord> recommendedChords() {
        String[] preferred = {"C", "Am", "G7", "Fmaj7", "Dm7", "Bm7"};
        List<Chord> result = new ArrayList<>();
        for (String symbol : preferred) {
            ChordRepository.LookupResult lookup = repository.find(symbol);
            if (lookup.recognized) {
                result.add(lookup.chord);
            }
        }
        if (result.size() < 6) {
            for (Chord chord : repository.allChords()) {
                if (!result.contains(chord)) {
                    result.add(chord);
                }
                if (result.size() >= 6) {
                    break;
                }
            }
        }
        return result;
    }

    private List<Chord> chordsForSymbols(List<String> symbols) {
        List<Chord> chords = new ArrayList<>();
        for (String symbol : symbols) {
            ChordRepository.LookupResult result = repository.find(symbol);
            if (result.recognized) {
                chords.add(result.chord);
            }
        }
        return chords;
    }

    private LinearLayout buildChordGrid(List<Chord> chords, boolean homeCards) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < chords.size(); i += 2) {
            LinearLayout row = horizontalRow();
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                rowParams.setMargins(0, dp(12), 0, 0);
            }
            grid.addView(row, rowParams);

            row.addView(buildChordCard(chords.get(i), homeCards), new LinearLayout.LayoutParams(0, dp(homeCards ? 210 : 188), 1f));
            if (i + 1 < chords.size()) {
                LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(homeCards ? 210 : 188), 1f);
                rightParams.setMargins(dp(12), 0, 0, 0);
                row.addView(buildChordCard(chords.get(i + 1), homeCards), rightParams);
            } else {
                View spacer = new View(this);
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
                spacerParams.setMargins(dp(12), 0, 0, 0);
                row.addView(spacer, spacerParams);
            }
        }
        return grid;
    }

    private View buildChordCard(Chord chord, boolean showPreview) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(ripple(COLOR_WHITE, 0xFFF4F6FA, 16, COLOR_LINE, 1));
        card.setClickable(true);
        card.setOnClickListener(view -> showChordDetail(chord.symbol));

        TextView name = text(displayName(chord), 22, true);
        name.setSingleLine(false);
        card.addView(name);

        if (showPreview) {
            ChordPreviewView preview = new ChordPreviewView(this);
            preview.setVoicing(chord.voicings.isEmpty() ? null : chord.voicings.get(0));
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            );
            previewParams.setMargins(0, dp(2), 0, dp(7));
            card.addView(preview, previewParams);
        } else {
            TextView info = smallText(joinWithSpace(chord.notes) + "\n" + chord.quality);
            card.addView(info, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            ));
        }

        LinearLayout tags = horizontalRow();
        tags.addView(tagChip(chord.voicings.isEmpty() ? chord.quality : chord.voicings.get(0).difficulty));
        String secondTag = chord.voicings.isEmpty() ? "常用" : (chord.voicings.get(0).barre ? "横按" : "开放和弦");
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
        );
        tagParams.setMargins(dp(8), 0, 0, 0);
        tags.addView(tagChip(secondTag), tagParams);
        card.addView(tags);
        return card;
    }

    private View buildFavoritesList(List<Chord> favorites) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < favorites.size(); i++) {
            Chord chord = favorites.get(i);
            LinearLayout card = buildSectionCard();
            card.setOnClickListener(view -> showChordDetail(chord.symbol));
            card.setClickable(true);

            LinearLayout row = horizontalRow();
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(text(displayName(chord), 22, true));
            copy.addView(smallText(chord.symbol + " · " + joinWithSpace(chord.notes)));
            row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = chipButton("取消收藏");
            remove.setOnClickListener(view -> {
                if (userStore.isFavorite(chord.symbol)) {
                    userStore.toggleFavorite(chord.symbol);
                    refreshLibrarySections();
                }
            });
            row.addView(remove, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
            ));
            card.addView(row);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                params.setMargins(0, dp(12), 0, 0);
            }
            list.addView(card, params);
        }
        return list;
    }

    private View sectionHeader(String left, String right, View.OnClickListener listener) {
        LinearLayout row = horizontalRow();
        TextView title = sectionTitle(left);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView action = smallText(right);
        action.setTextColor(COLOR_TEXT);
        action.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        action.setOnClickListener(listener);
        row.addView(action);
        return row;
    }

    private LinearLayout buildChordInfoChip(String label, String value) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(8), dp(8), dp(8));
        chip.setBackground(roundedStroke(COLOR_WHITE, COLOR_LINE, 14, 1));
        TextView labelView = smallText(label);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, 0, 0, dp(3));
        chip.addView(labelView);
        TextView valueView = text(value, 16, true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setSingleLine(false);
        chip.addView(valueView);
        return chip;
    }

    private LinearLayout buildSectionCard() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(roundedStroke(COLOR_WHITE, COLOR_LINE, 18, 1));
        return layout;
    }

    private Button buildPrimaryActionButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_WHITE);
        button.setBackground(ripple(COLOR_TEXT, 0xFF2C3330, 18));
        return button;
    }

    private Button buildSecondaryActionButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_TEXT);
        button.setBackground(ripple(COLOR_WHITE, 0xFFE9ECF1, 18, COLOR_TEXT, 1));
        return button;
    }

    private View pageTitle(String value, String subtitle) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(text(value, 30, true));
        TextView subtitleView = smallText(subtitle);
        subtitleView.setPadding(0, dp(6), 0, 0);
        header.addView(subtitleView);
        return header;
    }

    private View emptyState(String title, String subtitle) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(34), dp(18), dp(34));
        empty.setBackground(rounded(COLOR_SURFACE, 18));
        TextView titleView = text(title, 20, true);
        titleView.setGravity(Gravity.CENTER);
        empty.addView(titleView);
        TextView subtitleView = smallText(subtitle);
        subtitleView.setGravity(Gravity.CENTER);
        empty.addView(subtitleView);
        return empty;
    }

    private View exampleChips() {
        LinearLayout examplesRow = horizontalRow();
        for (String example : repository.examples()) {
            Button exampleButton = chipButton(example);
            exampleButton.setOnClickListener(view -> {
                if (input != null) {
                    input.setText(example);
                }
                query(example);
            });
            examplesRow.addView(exampleButton, chipParams());
        }
        return horizontalScroll(examplesRow);
    }

    private EditText roundedInput(String hint) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setTextSize(18);
        view.setHint(hint);
        view.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackground(roundedStroke(COLOR_SURFACE, COLOR_LINE, 16, 1));
        return view;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(COLOR_TEXT);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(2), 1.05f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private TextView iconText(String value, int sp, boolean bold) {
        TextView view = text(value, sp, bold);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(COLOR_TEXT);
        view.setClickable(true);
        view.setBackground(ripple(COLOR_WHITE, 0xFFE9ECF1, 18));
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

    private TextView tagChip(String value) {
        TextView chip = text(value, 12, false);
        chip.setTextColor(COLOR_SUBTLE);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(rounded(COLOR_SURFACE, 8));
        return chip;
    }

    private LinearLayout pageRoot(int left, int top, int right, int bottom) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(left, top, right, bottom);
        return root;
    }

    private ScrollView scrollPage(View child) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.addView(child, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        return scrollView;
    }

    private void setPageContent(View page) {
        contentContainer.removeAllViews();
        contentContainer.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        animateRefresh(page);
        buildBottomNav();
    }

    private void buildBottomNav() {
        bottomNav.removeAllViews();
        Page active = currentPage == Page.DETAIL ? returnPage : currentPage;
        bottomNav.addView(navItem("⌂", "首页", Page.HOME, active == Page.HOME), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bottomNav.addView(navItem("♡", "收藏", Page.FAVORITES, active == Page.FAVORITES), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bottomNav.addView(addNavItem(active == Page.ADD), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bottomNav.addView(navItem("▦", "和弦库", Page.LIBRARY, active == Page.LIBRARY), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bottomNav.addView(navItem("♙", "我", Page.PROFILE, active == Page.PROFILE), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
    }

    private View navItem(String icon, String label, Page target, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setOnClickListener(view -> navigate(target));

        TextView iconView = text(icon, 25, selected);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextColor(selected ? COLOR_TEXT : COLOR_SUBTLE);
        item.addView(iconView);

        TextView labelView = text(label, 13, selected);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextColor(selected ? COLOR_TEXT : COLOR_SUBTLE);
        item.addView(labelView);
        return item;
    }

    private View addNavItem(boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setOnClickListener(view -> showAddPage());

        TextView plus = text("+", 30, false);
        plus.setTextColor(COLOR_WHITE);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(ripple(COLOR_TEXT, 0xFF2C3330, 14));
        item.addView(plus, squareParams(48));

        TextView label = text("添加", 13, selected);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(selected ? COLOR_TEXT : COLOR_SUBTLE);
        label.setPadding(0, dp(3), 0, 0);
        item.addView(label);
        return item;
    }

    private void navigate(Page page) {
        switch (page) {
            case FAVORITES:
                showFavoritesPage();
                break;
            case ADD:
                showAddPage();
                break;
            case LIBRARY:
                showLibraryPage();
                break;
            case PROFILE:
                showProfilePage();
                break;
            case HOME:
            default:
                showHomePage();
                break;
        }
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private HorizontalScrollView horizontalScroll(View child) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(child);
        return scroll;
    }

    private Button chipButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(14);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setMinWidth(dp(64));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(COLOR_TEXT);
        button.setBackground(ripple(COLOR_WHITE, 0xFFE8EBF0, 14, COLOR_LINE, 1));
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
        button.setTextColor(selected ? COLOR_WHITE : COLOR_TEXT);
        if (selected) {
            button.setBackground(ripple(COLOR_ACCENT, 0xFF3F68D9, 14));
        } else {
            button.setBackground(ripple(COLOR_WHITE, 0xFFE8EBF0, 14, COLOR_LINE, 1));
        }
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
        return ripple(color, pressedColor, radiusDp, 0, 0);
    }

    private Drawable ripple(int color, int pressedColor, int radiusDp, int strokeColor, int strokeDp) {
        Drawable content = strokeDp > 0
                ? roundedStroke(color, strokeColor, radiusDp, strokeDp)
                : rounded(color, radiusDp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(ColorStateList.valueOf(pressedColor), content, null);
        }
        return content;
    }

    private LinearLayout.LayoutParams sectionParams() {
        return sectionParams(18);
    }

    private LinearLayout.LayoutParams sectionParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(topMargin), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams sectionParams(int topMargin, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
        );
        params.setMargins(0, dp(topMargin), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams squareParams(int size) {
        return new LinearLayout.LayoutParams(dp(size), dp(size));
    }

    private void animateRefresh(View view) {
        view.setAlpha(0.65f);
        view.setTranslationY(dp(8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(190)
                .setInterpolator(easeOut)
                .start();
    }

    private void animateBoard() {
        if (fretboardView == null) {
            return;
        }
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

    private String readableUri(Uri uri) {
        String last = uri.getLastPathSegment();
        return last == null || last.isEmpty() ? "已选择自定义路径" : last;
    }

    private String summaryText(Chord chord) {
        if (chord.bassNote != null && !chord.bassNote.isEmpty()) {
            return String.format(Locale.CHINA, "根音：%s    低音：%s    类型：%s",
                    chord.root, chord.bassNote, chord.quality);
        }
        return String.format(Locale.CHINA, "根音：%s    类型：%s", chord.root, chord.quality);
    }

    private String displayName(Chord chord) {
        if ("大三和弦".equals(chord.quality) && (chord.bassNote == null || chord.bassNote.isEmpty())) {
            return chord.symbol + " 大调";
        }
        if ("小三和弦".equals(chord.quality) && (chord.bassNote == null || chord.bassNote.isEmpty())) {
            return chord.symbol + " 小调";
        }
        return chord.chineseName;
    }

    private String englishName(Chord chord) {
        if ("大三和弦".equals(chord.quality)) {
            return chord.root + " Major";
        }
        if ("小三和弦".equals(chord.quality)) {
            return chord.root + " Minor";
        }
        if ("属七和弦".equals(chord.quality)) {
            return chord.root + " Dominant 7";
        }
        if ("大七和弦".equals(chord.quality)) {
            return chord.root + " Major 7";
        }
        if ("小七和弦".equals(chord.quality)) {
            return chord.root + " Minor 7";
        }
        return chord.quality;
    }

    private String learningTip(Voicing voicing) {
        if (voicing == null) {
            return "当前和弦暂无可用吉他按法。";
        }
        if (voicing.barre) {
            return "该按法包含横按，初学者可以先降低速度练习，注意食指受力均匀。"
                    + "\n" + voicing.description;
        }
        if (voicing.simplified) {
            return "这是简化按法，适合先建立和弦声音记忆，再逐步过渡到完整指法。"
                    + "\n" + voicing.description;
        }
        if ("入门".equals(voicing.difficulty)) {
            return "适合初学者练习，注意手指垂直按弦，避免碰到相邻琴弦。"
                    + "\n" + voicing.description;
        }
        return voicing.description;
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

    private String joinWithSpace(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(' ');
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
