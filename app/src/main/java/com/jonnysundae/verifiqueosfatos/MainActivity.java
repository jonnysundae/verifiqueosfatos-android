package com.jonnysundae.verifiqueosfatos;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY_PERMISSION = 501;
    private static final int REQ_POST_NOTIFICATIONS = 502;
    private static final int MIN_CHECKS_THERMO = 3;

    private TextView statusBadgeTv;
    private TextView lastCheckTv;
    private LinearLayout permissionBanner;
    private TextView toggleServiceBtn;
    private TextView pinnedModeBtn;
    private TextView autoUpdateBtn;
    private TextView posSettingBtn;
    private TextView sizeSettingBtn;
    private TextView durationSettingBtn;
    private TextView soundSettingBtn;
    private LinearLayout thermoContainer;
    private LinearLayout cardsContainer;

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUiState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNotificationPermissionIfNeeded();
        setContentView(buildMainView());

        if (AppPrefs.isMonitoringActive(this)) {
            sendServiceAction(FactCheckService.ACTION_START);
        }

        // Verifica silenciosamente se ha versao mais recente no GitHub ao abrir o app
        AppUpdater.checkSilently(this, new AppUpdater.UpdateCheckCallback() {
            @Override
            public void onResult(boolean hasNewVersion, String latestTag, String currentVersion) {
                if (hasNewVersion && autoUpdateBtn != null) {
                    autoUpdateBtn.setText("\uD83D\uDE80 ATUALIZAR PARA v" + latestTag);
                    autoUpdateBtn.setBackground(UiHelper.focusableButtonDrawable(
                            MainActivity.this, 0xFF1D4ED8, 0xFF1E40AF, 0xFF3B82F6, 0xFFFAFAFA, 12f
                    ));
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(FactCheckService.ACTION_UI_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(uiReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(uiReceiver, filter);
        }

        if (AppUpdater.pendingInstallAfterPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getPackageManager().canRequestPackageInstalls()) {
                AppUpdater.triggerInstallIntent(this);
            }
        }

        refreshUiState();
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(uiReceiver);
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    private View buildMainView() {
        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setBackgroundColor(0xFF0A0A0A);
        rootScroll.setFillViewport(true);

        FrameLayout centerWrapper = new FrameLayout(this);
        int horizontalPad = UiHelper.dp(this, UiHelper.isTelevision(this) ? 42 : 16);
        int verticalPad = UiHelper.dp(this, 20);
        centerWrapper.setPadding(horizontalPad, verticalPad, horizontalPad, UiHelper.dp(this, 48));

        LinearLayout mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams mainColLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mainColLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        centerWrapper.addView(mainCol, mainColLp);

        // 1. TOP HEADER
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, UiHelper.dp(this, 16));

        ImageView logoImg = new ImageView(this);
        logoImg.setImageResource(R.drawable.ic_launcher);
        int logoSize = UiHelper.dp(this, 42);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        logoLp.rightMargin = UiHelper.dp(this, 12);
        headerRow.addView(logoImg, logoLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBoxLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView appTitle = new TextView(this);
        appTitle.setText("Verifique os Fatos");
        appTitle.setTextColor(0xFFFAFAFA);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        appTitle.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        titleBox.addView(appTitle);

        TextView appSub = new TextView(this);
        appSub.setText("Checagem em tempo real \u2022 Smart TV & Mobile (v" + AppUpdater.getCurrentVersionName(this) + ")");
        appSub.setTextColor(0xFFA3A3A3);
        appSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleBox.addView(appSub);

        headerRow.addView(titleBox, titleBoxLp);

        // Status Box à direita
        LinearLayout statusCol = new LinearLayout(this);
        statusCol.setOrientation(LinearLayout.VERTICAL);
        statusCol.setGravity(Gravity.END);

        statusBadgeTv = new TextView(this);
        statusBadgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusBadgeTv.setTypeface(Typeface.DEFAULT_BOLD);
        int badgePadH = UiHelper.dp(this, 10);
        int badgePadV = UiHelper.dp(this, 4);
        statusBadgeTv.setPadding(badgePadH, badgePadV, badgePadH, badgePadV);
        statusCol.addView(statusBadgeTv);

        lastCheckTv = new TextView(this);
        lastCheckTv.setTextColor(0xFFA3A3A3);
        lastCheckTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        LinearLayout.LayoutParams lastCheckLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lastCheckLp.topMargin = UiHelper.dp(this, 4);
        statusCol.addView(lastCheckTv, lastCheckLp);

        headerRow.addView(statusCol);
        mainCol.addView(headerRow);

        // Linha divisoria
        View divider1 = new View(this);
        divider1.setBackgroundColor(0xFF2E2E2E);
        mainCol.addView(divider1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiHelper.dp(this, 1)));

        // 2. BANNER DE PERMISSAO DE POPUP (SE AINDA NAO CONCEDIDA)
        permissionBanner = new LinearLayout(this);
        permissionBanner.setOrientation(LinearLayout.VERTICAL);
        permissionBanner.setBackground(UiHelper.roundedRect(this, 0xFF291D09, 12f, 0xFFF59E0B, 1.5f));
        int permPad = UiHelper.dp(this, 14);
        permissionBanner.setPadding(permPad, permPad, permPad, permPad);
        LinearLayout.LayoutParams permLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permLp.topMargin = UiHelper.dp(this, 14);

        TextView permTitle = new TextView(this);
        permTitle.setText("\u26A0\uFE0F Ative a permiss\u00e3o de Popup sobre outros Apps");
        permTitle.setTextColor(0xFFFDE68A);
        permTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        permTitle.setTypeface(Typeface.DEFAULT_BOLD);
        permissionBanner.addView(permTitle);

        TextView permDesc = new TextView(this);
        permDesc.setText("Para que o card apare\u00e7a por cima da transmiss\u00e3o do debate (Globoplay, YouTube, TV), autorize a sobreposi\u00e7\u00e3o de tela:");
        permDesc.setTextColor(0xFFE5E5E5);
        permDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams permDescLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permDescLp.topMargin = UiHelper.dp(this, 4);
        permDescLp.bottomMargin = UiHelper.dp(this, 10);
        permissionBanner.addView(permDesc, permDescLp);

        TextView grantBtn = createFocusableButton(
                "PERMITIR SOBREPOR A OUTROS APPS",
                0xFFF59E0B,
                0xFFD97706,
                0xFF171717,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestOverlayPermission();
                    }
                }
        );
        permissionBanner.addView(grantBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        mainCol.addView(permissionBanner, permLp);

        // 3. PAINEL DE CONTROLES PRINCIPAIS (TV REMOTE & TOUCH FRIENDLY)
        LinearLayout controlsPanel = new LinearLayout(this);
        controlsPanel.setOrientation(LinearLayout.VERTICAL);
        controlsPanel.setBackground(UiHelper.roundedRect(this, 0xFF171717, 12f, 0xFF2E2E2E, 1f));
        int ctrlPad = UiHelper.dp(this, 14);
        controlsPanel.setPadding(ctrlPad, ctrlPad, ctrlPad, ctrlPad);
        LinearLayout.LayoutParams ctrlPanelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        ctrlPanelLp.topMargin = UiHelper.dp(this, 14);

        TextView ctrlTitle = new TextView(this);
        ctrlTitle.setText("PAINEL DE CONTROLE AO VIVO");
        ctrlTitle.setTextColor(0xFFA3A3A3);
        ctrlTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        ctrlTitle.setTypeface(Typeface.DEFAULT_BOLD);
        controlsPanel.addView(ctrlTitle);

        // Linha 1: Monitoramento + Popup Fixo + Testar + Auto Update
        HorizontalScrollView row1Scroll = new HorizontalScrollView(this);
        row1Scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, UiHelper.dp(this, 10), 0, UiHelper.dp(this, 6));

        toggleServiceBtn = createFocusableButton(
                "\u23F9 PAUSAR MONITORAMENTO",
                0xFF15803D,
                0xFF166534,
                0xFFFFFFFF,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean active = AppPrefs.isMonitoringActive(MainActivity.this);
                        if (active) {
                            sendServiceAction(FactCheckService.ACTION_STOP);
                            Toast.makeText(MainActivity.this, "Monitoramento pausado.", Toast.LENGTH_SHORT).show();
                        } else {
                            sendServiceAction(FactCheckService.ACTION_START);
                            Toast.makeText(MainActivity.this, "Monitoramento ao vivo ativado!", Toast.LENGTH_SHORT).show();
                        }
                        refreshUiState();
                    }
                }
        );
        addButtonWithMargin(row1, toggleServiceBtn);

        pinnedModeBtn = createFocusableButton(
                "\uD83D\uDCCC Modo: Tempor\u00e1rio",
                0xFF262626,
                0xFF3F3F46,
                0xFFFAFAFA,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean nextPinned = !AppPrefs.isPopupPinned(MainActivity.this);
                        AppPrefs.setPopupPinned(MainActivity.this, nextPinned);
                        refreshUiState();
                        sendServiceAction(FactCheckService.ACTION_SYNC_PINNED);
                        Toast.makeText(
                                MainActivity.this,
                                nextPinned
                                        ? "\uD83D\uDCCC Popup Fixo ativado! O card ficar\u00e1 fixo na tela e atualizar\u00e1 sozinho."
                                        : "\u23F1 Modo Tempor\u00e1rio ativado! O popup fechar\u00e1 ap\u00f3s alguns segundos.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
        addButtonWithMargin(row1, pinnedModeBtn);

        TextView testNowBtn = createFocusableButton(
                "\u26A1 Testar Popup",
                0xFF262626,
                0xFF3F3F46,
                0xFFFAFAFA,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendServiceAction(FactCheckService.ACTION_TEST_NOW);
                    }
                }
        );
        addButtonWithMargin(row1, testNowBtn);

        TextView test5sBtn = createFocusableButton(
                "\u23F1 Testar em 5s",
                0xFF262626,
                0xFF3F3F46,
                0xFFFAFAFA,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendServiceAction(FactCheckService.ACTION_TEST_DELAYED);
                        Toast.makeText(
                                MainActivity.this,
                                "Popup aparecer\u00e1 em 5s! Abra seu app de TV/YouTube agora.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
        addButtonWithMargin(row1, test5sBtn);

        autoUpdateBtn = createFocusableButton(
                "\u2B06\uFE0F Atualizar App (v" + AppUpdater.getCurrentVersionName(this) + ")",
                0xFF262626,
                0xFF1D4ED8,
                0xFF93C5FD,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppUpdater.checkAndPromptUpdate(MainActivity.this);
                    }
                }
        );
        addButtonWithMargin(row1, autoUpdateBtn);

        row1Scroll.addView(row1);
        controlsPanel.addView(row1Scroll);

        // Linha 2 de ajustes rapidos
        HorizontalScrollView row2Scroll = new HorizontalScrollView(this);
        row2Scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, UiHelper.dp(this, 6), 0, 0);

        posSettingBtn = createFocusableButton(
                "Posi\u00e7\u00e3o: " + AppPrefs.getPopupPositionLabel(this),
                0xFF1F1F1F,
                0xFF333333,
                0xFFD4D4D4,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int next = (AppPrefs.getPopupPosition(MainActivity.this) + 1) % 4;
                        AppPrefs.setPopupPosition(MainActivity.this, next);
                        refreshUiState();
                        sendServiceAction(FactCheckService.ACTION_TEST_NOW);
                    }
                }
        );
        addButtonWithMargin(row2, posSettingBtn);

        sizeSettingBtn = createFocusableButton(
                "Tamanho: " + AppPrefs.getPopupSizeLabel(this),
                0xFF1F1F1F,
                0xFF333333,
                0xFFD4D4D4,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppPrefs.cyclePopupSize(MainActivity.this);
                        refreshUiState();
                        sendServiceAction(FactCheckService.ACTION_TEST_NOW);
                    }
                }
        );
        addButtonWithMargin(row2, sizeSettingBtn);

        durationSettingBtn = createFocusableButton(
                "Tempo: " + AppPrefs.getPopupDurationSec(this) + "s",
                0xFF1F1F1F,
                0xFF333333,
                0xFFD4D4D4,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppPrefs.cyclePopupDurationSec(MainActivity.this);
                        refreshUiState();
                    }
                }
        );
        addButtonWithMargin(row2, durationSettingBtn);

        soundSettingBtn = createFocusableButton(
                "Som: Ligado",
                0xFF1F1F1F,
                0xFF333333,
                0xFFD4D4D4,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AppPrefs.setSoundEnabled(MainActivity.this, !AppPrefs.isSoundEnabled(MainActivity.this));
                        refreshUiState();
                    }
                }
        );
        addButtonWithMargin(row2, soundSettingBtn);

        TextView forceSyncBtn = createFocusableButton(
                "\uD83D\uDD04 Sincronizar posts.json",
                0xFF1F1F1F,
                0xFF333333,
                0xFFD4D4D4,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(MainActivity.this, "Consultando verifiqueosfatos.com/posts.json...", Toast.LENGTH_SHORT).show();
                        sendServiceAction(FactCheckService.ACTION_FORCE_CHECK);
                    }
                }
        );
        addButtonWithMargin(row2, forceSyncBtn);

        row2Scroll.addView(row2);
        controlsPanel.addView(row2Scroll);

        mainCol.addView(controlsPanel, ctrlPanelLp);

        // 4. FATOMETRO (.thermo do novo design oficial)
        thermoContainer = new LinearLayout(this);
        thermoContainer.setOrientation(LinearLayout.VERTICAL);
        thermoContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams thermoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        thermoLp.topMargin = UiHelper.dp(this, 16);
        mainCol.addView(thermoContainer, thermoLp);

        // 5. LISTA DE CHECAGENS EM TEMPO REAL
        TextView feedHeader = new TextView(this);
        feedHeader.setText("CHECAGENS PUBLICADAS (Toque ou aperte OK para ver no Popup)");
        feedHeader.setTextColor(0xFFA3A3A3);
        feedHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        feedHeader.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams feedHeaderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        feedHeaderLp.topMargin = UiHelper.dp(this, 20);
        feedHeaderLp.bottomMargin = UiHelper.dp(this, 10);
        mainCol.addView(feedHeader, feedHeaderLp);

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        mainCol.addView(cardsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Footer
        TextView footerTv = new TextView(this);
        footerTv.setText("Um projeto feito pela comunidade para a comunidade \u2022 verifiqueosfatos.com");
        footerTv.setTextColor(0xFF737373);
        footerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        footerTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        footerLp.topMargin = UiHelper.dp(this, 24);
        mainCol.addView(footerTv, footerLp);

        rootScroll.addView(centerWrapper);
        return rootScroll;
    }

    private void addButtonWithMargin(LinearLayout row, View btn) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = UiHelper.dp(this, 8);
        row.addView(btn, lp);
    }

    private TextView createFocusableButton(
            String text,
            int normalBg,
            int focusedBg,
            int textColor,
            View.OnClickListener listener
    ) {
        final TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        int padH = UiHelper.dp(this, 14);
        int padV = UiHelper.dp(this, 10);
        btn.setPadding(padH, padV, padH, padV);
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(false);
        btn.setClickable(true);
        btn.setBackground(UiHelper.focusableButtonDrawable(
                this,
                normalBg,
                focusedBg,
                0xFF333333,
                0xFFFAFAFA,
                10f
        ));
        btn.setOnClickListener(listener);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate()
                        .scaleX(hasFocus ? 1.04f : 1.0f)
                        .scaleY(hasFocus ? 1.04f : 1.0f)
                        .setDuration(140L)
                        .start();
            }
        });
        return btn;
    }

    private void refreshUiState() {
        boolean active = AppPrefs.isMonitoringActive(this);
        if (active) {
            statusBadgeTv.setText("\u25CF MONITORANDO AO VIVO");
            statusBadgeTv.setTextColor(0xFFFFFFFF);
            statusBadgeTv.setBackground(UiHelper.roundedRect(this, 0xFF15803D, 99f, 0, 0));

            toggleServiceBtn.setText("\u23F9 PAUSAR MONITORAMENTO");
            toggleServiceBtn.setBackground(UiHelper.focusableButtonDrawable(
                    this, 0xFF262626, 0xFFB91C1C, 0xFF404040, 0xFFFAFAFA, 10f
            ));
        } else {
            statusBadgeTv.setText("\u25CB PAUSADO");
            statusBadgeTv.setTextColor(0xFFA3A3A3);
            statusBadgeTv.setBackground(UiHelper.roundedRect(this, 0xFF1F1F1F, 99f, 0xFF404040, 1f));

            toggleServiceBtn.setText("\u25B6 INICIAR MONITORAMENTO");
            toggleServiceBtn.setBackground(UiHelper.focusableButtonDrawable(
                    this, 0xFF15803D, 0xFF166534, 0xFF15803D, 0xFFFAFAFA, 10f
            ));
        }

        boolean isPinned = AppPrefs.isPopupPinned(this);
        if (isPinned) {
            pinnedModeBtn.setText("\uD83D\uDCCC Popup: FIXO NA TELA");
            pinnedModeBtn.setTextColor(0xFFFFFFFF);
            pinnedModeBtn.setBackground(UiHelper.focusableButtonDrawable(
                    this, 0xFF15803D, 0xFF166534, 0xFF15803D, 0xFFFAFAFA, 10f
            ));
        } else {
            pinnedModeBtn.setText("\u23F1 Popup: Tempor\u00e1rio (" + AppPrefs.getPopupDurationSec(this) + "s)");
            pinnedModeBtn.setTextColor(0xFFFAFAFA);
            pinnedModeBtn.setBackground(UiHelper.focusableButtonDrawable(
                    this, 0xFF262626, 0xFF3F3F46, 0xFF404040, 0xFFFAFAFA, 10f
            ));
        }

        lastCheckTv.setText("\u00daltima checagem: " + AppPrefs.getLastCheckTime(this));

        boolean canOverlay = OverlayPopupManager.canDrawOverlays(this);
        permissionBanner.setVisibility(canOverlay ? View.GONE : View.VISIBLE);

        posSettingBtn.setText("Posi\u00e7\u00e3o: " + AppPrefs.getPopupPositionLabel(this));
        sizeSettingBtn.setText("Tamanho: " + AppPrefs.getPopupSizeLabel(this));
        durationSettingBtn.setText("Tempo: " + AppPrefs.getPopupDurationSec(this) + "s");
        soundSettingBtn.setText(AppPrefs.isSoundEnabled(this) ? "Som: Ligado \uD83D\uDD14" : "Som: Mudo \uD83D\uDD15");

        List<FactCheckItem> items = FactCheckItem.parseJsonArray(AppPrefs.getCachedJson(this));
        renderThermometer(items);
        renderFactCheckCards(items);
    }

    // Novo Fatometro (.thermo) igual ao repositorio atualizado
    private void renderThermometer(List<FactCheckItem> items) {
        thermoContainer.removeAllViews();

        List<FactCheckItem> cand1 = new ArrayList<>();
        List<FactCheckItem> cand2 = new ArrayList<>();
        for (FactCheckItem item : items) {
            if (item.n == 1) cand1.add(item);
            else if (item.n == 2) cand2.add(item);
        }

        if (cand1.size() < MIN_CHECKS_THERMO || cand2.size() < MIN_CHECKS_THERMO) {
            thermoContainer.setVisibility(View.GONE);
            return;
        }

        thermoContainer.setVisibility(View.VISIBLE);
        thermoContainer.setBackground(UiHelper.roundedRect(this, 0xFF171717, 12f, 0xFF2E2E2E, 1f));
        int pad = UiHelper.dp(this, 16);
        thermoContainer.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Fat\u00f4metro");
        title.setTextColor(0xFFFAFAFA);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleLp.bottomMargin = UiHelper.dp(this, 12);
        thermoContainer.addView(title, titleLp);

        thermoContainer.addView(buildThermoRow("Lula", R.drawable.lula, cand1));
        thermoContainer.addView(buildThermoRow("Fl\u00e1vio", R.drawable.flavio, cand2));

        LinearLayout scaleRow = new LinearLayout(this);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView sFalso = new TextView(this);
        sFalso.setText("Falso");
        sFalso.setTextColor(0xFFA3A3A3);
        sFalso.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        scaleRow.addView(sFalso, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView sParcial = new TextView(this);
        sParcial.setText("Parcial");
        sParcial.setTextColor(0xFFA3A3A3);
        sParcial.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sParcial.setGravity(Gravity.CENTER);
        scaleRow.addView(sParcial, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView sVerd = new TextView(this);
        sVerd.setText("Verdadeiro");
        sVerd.setTextColor(0xFFA3A3A3);
        sVerd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sVerd.setGravity(Gravity.END);
        scaleRow.addView(sVerd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        thermoContainer.addView(scaleRow);
    }

    private View buildThermoRow(String name, int avatarRes, List<FactCheckItem> mine) {
        double sum = 0;
        for (FactCheckItem item : mine) {
            if (item.c == 1) sum += 1.0;
            else if (item.c == 3) sum += 0.5;
        }
        final int pct = (int) Math.round((sum / mine.size()) * 100.0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = UiHelper.dp(this, 10);
        row.setLayoutParams(rowLp);

        int avatarPx = UiHelper.dp(this, 32);
        ImageView img = new ImageView(this);
        Bitmap bmp = UiHelper.getRoundedAvatarBitmap(this, avatarRes, avatarPx, 8f);
        if (bmp != null) img.setImageBitmap(bmp);
        else img.setImageResource(avatarRes);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(avatarPx, avatarPx);
        imgLp.rightMargin = UiHelper.dp(this, 10);
        row.addView(img, imgLp);

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameColLp = new LinearLayout.LayoutParams(
                UiHelper.dp(this, 86),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameColLp.rightMargin = UiHelper.dp(this, 10);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextColor(0xFFFAFAFA);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        nameCol.addView(nameTv);

        TextView countTv = new TextView(this);
        countTv.setText(mine.size() + " checagens");
        countTv.setTextColor(0xFFA3A3A3);
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        nameCol.addView(countTv);
        row.addView(nameCol, nameColLp);

        final FrameLayout barWrapper = new FrameLayout(this);
        LinearLayout.LayoutParams barWrapLp = new LinearLayout.LayoutParams(0, UiHelper.dp(this, 20), 1f);

        View barTrack = new View(this);
        barTrack.setBackground(UiHelper.thermoGradientBar(this));
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiHelper.dp(this, 10),
                Gravity.CENTER_VERTICAL
        );
        barWrapper.addView(barTrack, trackLp);

        final View thumb = new View(this);
        thumb.setBackground(UiHelper.roundedRect(this, 0xFF171717, 99f, 0xFFFAFAFA, 3f));
        final int thumbSize = UiHelper.dp(this, 18);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize, Gravity.CENTER_VERTICAL);
        barWrapper.addView(thumb, thumbLp);

        barWrapper.post(new Runnable() {
            @Override
            public void run() {
                int w = barWrapper.getWidth() - thumbSize;
                if (w > 0) {
                    thumb.setTranslationX((w * pct) / 100f);
                }
            }
        });

        row.addView(barWrapper, barWrapLp);

        TextView pctTv = new TextView(this);
        pctTv.setText(pct + "%");
        pctTv.setTextColor(0xFFFAFAFA);
        pctTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pctTv.setTypeface(Typeface.DEFAULT_BOLD);
        pctTv.setGravity(Gravity.END);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                UiHelper.dp(this, 44),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pctLp.leftMargin = UiHelper.dp(this, 8);
        row.addView(pctTv, pctLp);

        return row;
    }

    private void renderFactCheckCards(List<FactCheckItem> rawItems) {
        cardsContainer.removeAllViews();
        List<FactCheckItem> items = new ArrayList<>(rawItems);

        if (items.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Nada por aqui ainda... verifique novamente daqui alguns minutos.\nAssim que uma nova checagem entrar no ar em posts.json, o Popup aparecer\u00e1 automaticamente!");
            emptyTv.setTextColor(0xFFA3A3A3);
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            emptyTv.setGravity(Gravity.CENTER);
            int pad = UiHelper.dp(this, 28);
            emptyTv.setPadding(pad, pad, pad, pad);
            emptyTv.setBackground(UiHelper.roundedRect(this, 0xFF171717, 12f, 0xFF2E2E2E, 1f));
            cardsContainer.addView(emptyTv);
            return;
        }

        // Mostrar os mais recentes no topo
        Collections.reverse(items);

        for (final FactCheckItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            int pad = UiHelper.dp(this, 16);
            card.setPadding(pad, pad, pad, pad);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(UiHelper.focusableButtonDrawable(
                    this,
                    0xFF171717,
                    0xFF222222,
                    0xFF2E2E2E,
                    item.getStatusColor(),
                    12f
            ));

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = UiHelper.dp(this, 14);

            // Opcional: Horario ("h") e Tema ("a") no topo do card (.meta)
            if (item.hasMeta()) {
                TextView metaTv = new TextView(this);
                metaTv.setText(item.getMetaText());
                metaTv.setTextColor(0xFFA3A3A3);
                metaTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                metaTv.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                metaLp.bottomMargin = UiHelper.dp(this, 10);
                card.addView(metaTv, metaLp);
            }

            // Header do Card (Avatar quadrado arredondado 10dp + Nome + Badge solido)
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            int avatarSize = UiHelper.dp(this, 48);
            ImageView avatar = new ImageView(this);
            Bitmap bmp = UiHelper.getRoundedAvatarBitmap(this, item.getCandidateAvatarRes(), avatarSize, 10f);
            if (bmp != null) {
                avatar.setImageBitmap(bmp);
            } else {
                avatar.setImageResource(item.getCandidateAvatarRes());
            }
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(avatarSize, avatarSize);
            avatarLp.rightMargin = UiHelper.dp(this, 12);
            header.addView(avatar, avatarLp);

            TextView nameTv = new TextView(this);
            nameTv.setText(item.getCandidateName());
            nameTv.setTextColor(0xFFFAFAFA);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(nameTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Novo Badge solido (style.css: background: var(--tone), color: #FFFFFF)
            TextView badgeLabel = new TextView(this);
            badgeLabel.setText(item.getStatusLabel());
            badgeLabel.setTextColor(0xFFFFFFFF);
            badgeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            badgeLabel.setTypeface(Typeface.DEFAULT_BOLD);
            badgeLabel.setBackground(UiHelper.roundedRect(this, item.getStatusColor(), 99f, 0, 0));
            badgeLabel.setPadding(UiHelper.dp(this, 14), UiHelper.dp(this, 8), UiHelper.dp(this, 14), UiHelper.dp(this, 8));
            header.addView(badgeLabel);

            card.addView(header);

            // "Disse que:" com blockquote de borda esquerda 3px
            TextView saidLbl = new TextView(this);
            saidLbl.setText("DISSE QUE:");
            saidLbl.setTextColor(0xFFA3A3A3);
            saidLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            saidLbl.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams saidLblLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            saidLblLp.topMargin = UiHelper.dp(this, 14);
            saidLblLp.bottomMargin = UiHelper.dp(this, 6);
            card.addView(saidLbl, saidLblLp);

            LinearLayout saidRow = new LinearLayout(this);
            saidRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams saidRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            saidRowLp.bottomMargin = UiHelper.dp(this, 16);

            View quoteBar = new View(this);
            quoteBar.setBackgroundColor(0xFF404040);
            LinearLayout.LayoutParams quoteBarLp = new LinearLayout.LayoutParams(
                    UiHelper.dp(this, 3),
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            quoteBarLp.rightMargin = UiHelper.dp(this, 14);
            saidRow.addView(quoteBar, quoteBarLp);

            TextView saidTxt = new TextView(this);
            saidTxt.setText("\u201C" + item.t + "\u201D");
            saidTxt.setTextColor(0xFFFAFAFA);
            saidTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            saidTxt.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            saidRow.addView(saidTxt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            card.addView(saidRow, saidRowLp);

            // "Verificacao:"
            TextView verifLbl = new TextView(this);
            verifLbl.setText("VERIFICA\u00c7\u00c3O:");
            verifLbl.setTextColor(0xFFA3A3A3);
            verifLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            verifLbl.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(verifLbl);

            TextView verifTxt = new TextView(this);
            verifTxt.setText(item.v);
            verifTxt.setTextColor(0xFFD4D4D4);
            verifTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            LinearLayout.LayoutParams verifTxtLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            verifTxtLp.topMargin = UiHelper.dp(this, 6);
            card.addView(verifTxt, verifTxtLp);

            // Opcional: "Correcao:" (.fix)
            if (!item.e.isEmpty()) {
                TextView fixLbl = new TextView(this);
                fixLbl.setText("CORRE\u00c7\u00c3O:");
                fixLbl.setTextColor(0xFFA3A3A3);
                fixLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                fixLbl.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams fixLblLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                fixLblLp.topMargin = UiHelper.dp(this, 14);
                fixLblLp.bottomMargin = UiHelper.dp(this, 6);
                card.addView(fixLbl, fixLblLp);

                TextView fixTxt = new TextView(this);
                fixTxt.setText(item.e);
                fixTxt.setTextColor(0xFFD4D4D4);
                fixTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                int fixPad = UiHelper.dp(this, 10);
                fixTxt.setPadding(fixPad, fixPad, fixPad, fixPad);
                fixTxt.setBackground(UiHelper.roundedRect(this, 0xFF262626, 6f, 0xFF404040, 1f));
                card.addView(fixTxt);
            }

            // Opcional: "Fontes:" (.sources)
            if (!item.s.isEmpty()) {
                TextView srcLbl = new TextView(this);
                srcLbl.setText("FONTES:");
                srcLbl.setTextColor(0xFFA3A3A3);
                srcLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                srcLbl.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams srcLblLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                srcLblLp.topMargin = UiHelper.dp(this, 14);
                srcLblLp.bottomMargin = UiHelper.dp(this, 6);
                card.addView(srcLbl, srcLblLp);

                HorizontalScrollView srcScroll = new HorizontalScrollView(this);
                srcScroll.setHorizontalScrollBarEnabled(false);
                LinearLayout srcRow = new LinearLayout(this);
                srcRow.setOrientation(LinearLayout.HORIZONTAL);
                for (final String urlStr : item.s) {
                    TextView chip = new TextView(this);
                    chip.setText(FactCheckItem.extractHostname(urlStr) + " \u2197");
                    chip.setTextColor(0xFF93C5FD);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    chip.setTypeface(Typeface.DEFAULT_BOLD);
                    chip.setBackground(UiHelper.roundedRect(this, 0x1A93C5FD, 8f, 0, 0));
                    chip.setPadding(UiHelper.dp(this, 10), UiHelper.dp(this, 5), UiHelper.dp(this, 10), UiHelper.dp(this, 5));
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)));
                            } catch (Exception ignored) {
                            }
                        }
                    });
                    LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    chipLp.rightMargin = UiHelper.dp(this, 8);
                    srcRow.addView(chip, chipLp);
                }
                srcScroll.addView(srcRow);
                card.addView(srcScroll);
            }

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OverlayPopupManager.getInstance(MainActivity.this).showImmediatePopup(item);
                }
            });

            cardsContainer.addView(card, cardLp);
        }
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, FactCheckService.class);
        intent.setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !FactCheckService.ACTION_STOP.equals(action)) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
                return;
            } catch (Exception ignored) {
            }
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
                return;
            } catch (Exception ignored) {
            }
            try {
                Intent intent = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
                Toast.makeText(
                        this,
                        "Procure a op\u00e7\u00e3o 'Sobrepor a outros apps' nas configura\u00e7\u00f5es do aplicativo.",
                        Toast.LENGTH_LONG
                ).show();
            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "Abra Configura\u00e7\u00f5es > Apps > Acesso especial > Sobrepor a outros apps.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
            }
        }
    }
}
