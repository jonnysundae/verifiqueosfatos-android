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

import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY_PERMISSION = 501;
    private static final int REQ_POST_NOTIFICATIONS = 502;

    private TextView statusBadgeTv;
    private TextView lastCheckTv;
    private LinearLayout permissionBanner;
    private TextView toggleServiceBtn;
    private TextView posSettingBtn;
    private TextView durationSettingBtn;
    private TextView soundSettingBtn;
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
        int horizontalPad = UiHelper.dp(this, UiHelper.isTelevision(this) ? 42 : 18);
        int verticalPad = UiHelper.dp(this, 24);
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
        headerRow.setPadding(0, 0, 0, UiHelper.dp(this, 18));

        ImageView logoImg = new ImageView(this);
        logoImg.setImageResource(R.drawable.ic_launcher);
        int logoSize = UiHelper.dp(this, 46);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        logoLp.rightMargin = UiHelper.dp(this, 14);
        headerRow.addView(logoImg, logoLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBoxLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView appTitle = new TextView(this);
        appTitle.setText("Verifique os Fatos");
        appTitle.setTextColor(0xFFFAFAFA);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        appTitle.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        titleBox.addView(appTitle);

        TextView appSub = new TextView(this);
        appSub.setText("Smart TV & Mobile \u2022 Monitoramento em tempo real com Popup ao vivo");
        appSub.setTextColor(0xFFA3A3A3);
        appSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titleBox.addView(appSub);

        headerRow.addView(titleBox, titleBoxLp);

        // Status Box à direita
        LinearLayout statusCol = new LinearLayout(this);
        statusCol.setOrientation(LinearLayout.VERTICAL);
        statusCol.setGravity(Gravity.END);

        statusBadgeTv = new TextView(this);
        statusBadgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusBadgeTv.setTypeface(Typeface.DEFAULT_BOLD);
        int badgePadH = UiHelper.dp(this, 12);
        int badgePadV = UiHelper.dp(this, 5);
        statusBadgeTv.setPadding(badgePadH, badgePadV, badgePadH, badgePadV);
        statusCol.addView(statusBadgeTv);

        lastCheckTv = new TextView(this);
        lastCheckTv.setTextColor(0xFFA3A3A3);
        lastCheckTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
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
        divider1.setBackgroundColor(0xFF262626);
        mainCol.addView(divider1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiHelper.dp(this, 1)));

        // 2. BANNER DE PERMISSAO DE POPUP (SE AINDA NAO CONCEDIDA)
        permissionBanner = new LinearLayout(this);
        permissionBanner.setOrientation(LinearLayout.VERTICAL);
        permissionBanner.setBackground(UiHelper.roundedRect(this, 0xFF291D09, 16f, 0xFFF59E0B, 1.5f));
        int permPad = UiHelper.dp(this, 16);
        permissionBanner.setPadding(permPad, permPad, permPad, permPad);
        LinearLayout.LayoutParams permLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permLp.topMargin = UiHelper.dp(this, 16);

        TextView permTitle = new TextView(this);
        permTitle.setText("\u26A0\uFE0F Ative a permiss\u00e3o de Popup sobre outros Apps (Modo TV / Debate)");
        permTitle.setTextColor(0xFFFDE68A);
        permTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        permTitle.setTypeface(Typeface.DEFAULT_BOLD);
        permissionBanner.addView(permTitle);

        TextView permDesc = new TextView(this);
        permDesc.setText("Para que o card de checagem apare\u00e7a na tela por cima da transmiss\u00e3o do debate (Globoplay, YouTube, TV), autorize a sobreposi\u00e7\u00e3o de tela:");
        permDesc.setTextColor(0xFFE5E5E5);
        permDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams permDescLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permDescLp.topMargin = UiHelper.dp(this, 6);
        permDescLp.bottomMargin = UiHelper.dp(this, 12);
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

        TextView adbHint = new TextView(this);
        adbHint.setText("Dica para Android TV / TV Box que ocultam essa tela: adb shell appops set com.jonnysundae.verifiqueosfatos SYSTEM_ALERT_WINDOW allow");
        adbHint.setTextColor(0xFFA3A3A3);
        adbHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams adbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        adbLp.topMargin = UiHelper.dp(this, 8);
        permissionBanner.addView(adbHint, adbLp);

        mainCol.addView(permissionBanner, permLp);

        // 3. PAINEL DE CONTROLES PRINCIPAIS (TV REMOTE FRIENDLY)
        LinearLayout controlsPanel = new LinearLayout(this);
        controlsPanel.setOrientation(LinearLayout.VERTICAL);
        controlsPanel.setBackground(UiHelper.roundedRect(this, 0xFF141414, 18f, 0xFF262626, 1f));
        int ctrlPad = UiHelper.dp(this, 16);
        controlsPanel.setPadding(ctrlPad, ctrlPad, ctrlPad, ctrlPad);
        LinearLayout.LayoutParams ctrlPanelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        ctrlPanelLp.topMargin = UiHelper.dp(this, 16);

        TextView ctrlTitle = new TextView(this);
        ctrlTitle.setText("PAINEL DE CONTROLE AO VIVO (Use o Controle Remoto ou Toque)");
        ctrlTitle.setTextColor(0xFFA3A3A3);
        ctrlTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        ctrlTitle.setTypeface(Typeface.DEFAULT_BOLD);
        controlsPanel.addView(ctrlTitle);

        // Linha 1 de botoes principais
        HorizontalScrollView row1Scroll = new HorizontalScrollView(this);
        row1Scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, UiHelper.dp(this, 10), 0, UiHelper.dp(this, 6));

        toggleServiceBtn = createFocusableButton(
                "\u23F9 PAUSAR MONITORAMENTO",
                0xFF16A34A,
                0xFF15803D,
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

        TextView testNowBtn = createFocusableButton(
                "\u26A1 TESTAR POPUP AGORA",
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
                "\u23F1 TESTAR EM 5s (ABRIR TV/V\u00cdDEO)",
                0xFF262626,
                0xFF3F3F46,
                0xFFFAFAFA,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendServiceAction(FactCheckService.ACTION_TEST_DELAYED);
                        Toast.makeText(
                                MainActivity.this,
                                "Popup ser\u00e1 exibido em 5 segundos! Abra seu app de TV/YouTube agora.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
        addButtonWithMargin(row1, test5sBtn);

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

        durationSettingBtn = createFocusableButton(
                "Tempo na Tela: " + AppPrefs.getPopupDurationSec(this) + "s",
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
                "\uD83D\uDD04 Sincronizar Agora",
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

        // 4. LISTA DE CHECAGENS EM TEMPO REAL
        TextView feedHeader = new TextView(this);
        feedHeader.setText("CHECAGENS PUBLICADAS (Selecione um card para exibir no Popup)");
        feedHeader.setTextColor(0xFFA3A3A3);
        feedHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        feedHeader.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams feedHeaderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        feedHeaderLp.topMargin = UiHelper.dp(this, 24);
        feedHeaderLp.bottomMargin = UiHelper.dp(this, 12);
        mainCol.addView(feedHeader, feedHeaderLp);

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        mainCol.addView(cardsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Footer
        TextView footerTv = new TextView(this);
        footerTv.setText("Fonte oficial em tempo real: https://verifiqueosfatos.com/posts.json");
        footerTv.setTextColor(0xFF737373);
        footerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        footerTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        footerLp.topMargin = UiHelper.dp(this, 28);
        mainCol.addView(footerTv, footerLp);

        rootScroll.addView(centerWrapper);
        return rootScroll;
    }

    private void addButtonWithMargin(LinearLayout row, View btn) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = UiHelper.dp(this, 10);
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
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        int padH = UiHelper.dp(this, 16);
        int padV = UiHelper.dp(this, 11);
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
                12f
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
            statusBadgeTv.setTextColor(0xFF4ADE80);
            statusBadgeTv.setBackground(UiHelper.roundedRect(this, 0x2616A34A, 99f, 0xFF16A34A, 1f));

            toggleServiceBtn.setText("\u23F9 PAUSAR MONITORAMENTO");
            toggleServiceBtn.setBackground(UiHelper.focusableButtonDrawable(
                    this, 0xFF262626, 0xFFDC2626, 0xFF404040, 0xFFFAFAFA, 12f
            ));
        } else {
            statusBadgeTv.setText("\u25CB PAUSADO");
            statusBadgeTv.setTextColor(0xFFA3A3A3);
            statusBadgeTv.setBackground(UiHelper.roundedRect(this, 0xFF1F1F1F, 99f, 0xFF404040, 1f));

            toggleServiceBtn.setText("\u25B6 INICIAR MONITORAMENTO AO VIVO");
            toggleServiceBtn.setBackground(UiHelper.focusableButtonDrawable(
                    this, 0xFF16A34A, 0xFF15803D, 0xFF16A34A, 0xFFFAFAFA, 12f
            ));
        }

        lastCheckTv.setText("\u00daltima checagem: " + AppPrefs.getLastCheckTime(this));

        boolean canOverlay = OverlayPopupManager.canDrawOverlays(this);
        permissionBanner.setVisibility(canOverlay ? View.GONE : View.VISIBLE);

        posSettingBtn.setText("Posi\u00e7\u00e3o: " + AppPrefs.getPopupPositionLabel(this));
        durationSettingBtn.setText("Tempo na Tela: " + AppPrefs.getPopupDurationSec(this) + "s");
        soundSettingBtn.setText(AppPrefs.isSoundEnabled(this) ? "Som: Ligado \uD83D\uDD14" : "Som: Mudo \uD83D\uDD15");

        renderFactCheckCards();
    }

    private void renderFactCheckCards() {
        cardsContainer.removeAllViews();
        List<FactCheckItem> items = FactCheckItem.parseJsonArray(AppPrefs.getCachedJson(this));

        if (items.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Nenhuma declara\u00e7\u00e3o publicada no momento em posts.json.\nAssim que uma nova checagem entrar no ar, o Popup aparecer\u00e1 automaticamente na sua tela!");
            emptyTv.setTextColor(0xFFA3A3A3);
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            emptyTv.setGravity(Gravity.CENTER);
            int pad = UiHelper.dp(this, 32);
            emptyTv.setPadding(pad, pad, pad, pad);
            emptyTv.setBackground(UiHelper.roundedRect(this, 0xFF141414, 18f, 0xFF262626, 1f));
            cardsContainer.addView(emptyTv);
            return;
        }

        // Mostrar os mais recentes no topo
        Collections.reverse(items);

        for (final FactCheckItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            int pad = UiHelper.dp(this, 20);
            card.setPadding(pad, pad, pad, pad);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(UiHelper.focusableButtonDrawable(
                    this,
                    0xFF171717,
                    0xFF222222,
                    0xFF2E2E2E,
                    item.getStatusColor(),
                    20f
            ));

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = UiHelper.dp(this, 16);

            // Header do Card
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            int avatarSize = UiHelper.dp(this, 48);
            ImageView avatar = new ImageView(this);
            Bitmap bmp = UiHelper.getCircularBitmap(this, item.getCandidateAvatarRes(), avatarSize);
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
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(nameTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Badge
            LinearLayout badge = new LinearLayout(this);
            badge.setOrientation(LinearLayout.HORIZONTAL);
            badge.setGravity(Gravity.CENTER_VERTICAL);
            badge.setBackground(UiHelper.roundedRect(this, item.getStatusBgTint(), 99f, item.getStatusColor(), 1f));
            badge.setPadding(UiHelper.dp(this, 12), UiHelper.dp(this, 6), UiHelper.dp(this, 12), UiHelper.dp(this, 6));

            View dot = new View(this);
            dot.setBackground(UiHelper.roundedRect(this, item.getStatusColor(), 99f, 0, 0));
            int dotSize = UiHelper.dp(this, 8);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.rightMargin = UiHelper.dp(this, 6);
            badge.addView(dot, dotLp);

            TextView badgeLabel = new TextView(this);
            badgeLabel.setText(item.getStatusLabel());
            badgeLabel.setTextColor(0xFFFAFAFA);
            badgeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            badgeLabel.setTypeface(Typeface.DEFAULT_BOLD);
            badge.addView(badgeLabel);

            header.addView(badge);
            card.addView(header);

            // Disse que
            LinearLayout saidBox = new LinearLayout(this);
            saidBox.setOrientation(LinearLayout.VERTICAL);
            saidBox.setBackground(UiHelper.roundedRect(this, 0xFF262626, 14f, 0, 0));
            int saidPad = UiHelper.dp(this, 14);
            saidBox.setPadding(saidPad, saidPad, saidPad, saidPad);
            LinearLayout.LayoutParams saidLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            saidLp.topMargin = UiHelper.dp(this, 14);
            saidLp.bottomMargin = UiHelper.dp(this, 14);

            TextView saidLbl = new TextView(this);
            saidLbl.setText("DISSE QUE");
            saidLbl.setTextColor(0xFFA3A3A3);
            saidLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            saidLbl.setTypeface(Typeface.DEFAULT_BOLD);
            saidBox.addView(saidLbl);

            TextView saidTxt = new TextView(this);
            saidTxt.setText(item.t);
            saidTxt.setTextColor(0xFFFAFAFA);
            saidTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            saidTxt.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            LinearLayout.LayoutParams saidTxtLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            saidTxtLp.topMargin = UiHelper.dp(this, 4);
            saidBox.addView(saidTxt, saidTxtLp);

            card.addView(saidBox, saidLp);

            // Verificacao
            TextView verifLbl = new TextView(this);
            verifLbl.setText("VERIFICA\u00c7\u00c3O");
            verifLbl.setTextColor(item.getStatusColor());
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
            verifTxtLp.topMargin = UiHelper.dp(this, 4);
            card.addView(verifTxt, verifTxtLp);

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
