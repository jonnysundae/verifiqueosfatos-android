package com.jonnysundae.verifiqueosfatos;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Layout;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.Queue;

public class OverlayPopupManager {

    private static final String CHANNEL_ID = "verifique_fatos_popup_channel";
    private static int notificationIdCounter = 2000;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler;
    private final Queue<FactCheckItem> popupQueue = new LinkedList<>();

    private View currentOverlayView = null;
    private ValueAnimator countdownAnimator = null;
    private ValueAnimator autoScrollAnimator = null;
    private Runnable dismissRunnable = null;

    private static OverlayPopupManager instance;

    public static synchronized OverlayPopupManager getInstance(Context context) {
        if (instance == null) {
            instance = new OverlayPopupManager(context.getApplicationContext());
        }
        return instance;
    }

    private OverlayPopupManager(Context appContext) {
        this.context = appContext;
        this.windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        ensureNotificationChannel();
    }

    public void enqueuePopup(final FactCheckItem item, final boolean playSound) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (playSound && AppPrefs.isSoundEnabled(context)) {
                    playAlertChime(item.c);
                }
                sendHeadsUpNotification(item);

                if (!canDrawOverlays(context)) {
                    Toast.makeText(
                            context,
                            "[" + item.getCandidateName() + " - " + item.getStatusLabel().toUpperCase() + "] " + item.t,
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                popupQueue.offer(item);
                if (currentOverlayView == null) {
                    showNextFromQueue();
                }
            }
        });
    }

    public void showImmediatePopup(final FactCheckItem item) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (AppPrefs.isSoundEnabled(context)) {
                    playAlertChime(item.c);
                }
                dismissCurrentOverlay();
                popupQueue.clear();
                popupQueue.offer(item);
                if (canDrawOverlays(context)) {
                    showNextFromQueue();
                } else {
                    sendHeadsUpNotification(item);
                    Toast.makeText(
                            context,
                            "Ative a permiss\u00e3o 'Sobrepor a outros apps' para ver o Popup flutuante na TV/Tela!",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });
    }

    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    private void showNextFromQueue() {
        final FactCheckItem item = popupQueue.poll();
        if (item == null) {
            return;
        }

        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;
            boolean isLandscape = screenW > screenH;

            int posMode = AppPrefs.getPopupPosition(context);
            int sizeMode = AppPrefs.getPopupSize(context); // 0=Compacto (M), 1=Mini (P), 2=Grande (TV)

            // Escala baseada no modo de tamanho
            int maxWidthDp;
            int padDp;
            int avatarDp;
            float nameSp;
            float badgeSp;
            float labelSp;
            float quoteSp;
            float verifSp;
            int quoteMaxLines;
            final int verifMaxLines;

            if (sizeMode == 1) {
                // MINI (P) - Super discreto no canto
                maxWidthDp = 240;
                padDp = 9;
                avatarDp = 24;
                nameSp = 13f;
                badgeSp = 10f;
                labelSp = 9f;
                quoteSp = 11.5f;
                verifSp = 11.5f;
                quoteMaxLines = 2;
                verifMaxLines = 2;
            } else if (sizeMode == 2) {
                // GRANDE (TV) - Para leitura de longe em telas grandes
                maxWidthDp = isLandscape ? 380 : 330;
                padDp = 14;
                avatarDp = 36;
                nameSp = 16f;
                badgeSp = 11.5f;
                labelSp = 10f;
                quoteSp = 13.5f;
                verifSp = 13f;
                quoteMaxLines = 3;
                verifMaxLines = 4;
            } else {
                // COMPACTO (M - Padrao) - Equilibrado para Celular e TV sem ocupar a tela
                maxWidthDp = isLandscape ? 300 : 285;
                padDp = 11;
                avatarDp = 28;
                nameSp = 14f;
                badgeSp = 10.5f;
                labelSp = 9.5f;
                quoteSp = 12f;
                verifSp = 12f;
                quoteMaxLines = 2;
                verifMaxLines = 3;
            }

            int cardWidth;
            if (isLandscape) {
                if (posMode == 2) {
                    // Rodape GC TV
                    cardWidth = Math.min((int) (screenW * 0.65f), UiHelper.dp(context, maxWidthDp + 100));
                } else {
                    cardWidth = Math.min((int) (screenW * 0.42f), UiHelper.dp(context, maxWidthDp));
                }
            } else {
                // Celular em retrato: nunca ocupa a largura toda e fica compacto
                cardWidth = Math.min((int) (screenW * 0.84f), UiHelper.dp(context, maxWidthDp));
            }

            int gravity;
            int marginX = UiHelper.dp(context, 14);
            int marginY = UiHelper.dp(context, isLandscape ? 16 : 28);

            if (posMode == 2) {
                gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            } else if (posMode == 3) {
                gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            } else if (posMode == 1) {
                gravity = Gravity.TOP | Gravity.END;
            } else {
                gravity = Gravity.BOTTOM | Gravity.END;
            }

            // Card principal compacto (sem ScrollView para nunca esticar no WindowManager)
            final LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(UiHelper.roundedRect(context, 0xF2141414, 16f, item.getStatusColor(), 1.5f));
            int pad = UiHelper.dp(context, padDp);
            card.setPadding(pad, pad, pad, UiHelper.dp(context, Math.max(8, padDp - 2)));

            // 1. HEADER COMPACTO: Avatar + Nome + Badge + Botao Fechar (X)
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            int avatarPx = UiHelper.dp(context, avatarDp);
            ImageView avatarView = new ImageView(context);
            Bitmap circularAvatar = UiHelper.getCircularBitmap(context, item.getCandidateAvatarRes(), avatarPx);
            if (circularAvatar != null) {
                avatarView.setImageBitmap(circularAvatar);
            } else {
                avatarView.setImageResource(item.getCandidateAvatarRes());
            }
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(avatarPx, avatarPx);
            avatarLp.rightMargin = UiHelper.dp(context, 8);
            header.addView(avatarView, avatarLp);

            TextView candidateTv = new TextView(context);
            candidateTv.setText(item.getCandidateName());
            candidateTv.setTextColor(0xFFFAFAFA);
            candidateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, nameSp);
            candidateTv.setTypeface(Typeface.DEFAULT_BOLD);
            candidateTv.setSingleLine(true);
            candidateTv.setEllipsize(TextUtils.TruncateAt.END);
            header.addView(candidateTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Badge (Verdadeiro / Falso / Parcial)
            LinearLayout badgeLayout = new LinearLayout(context);
            badgeLayout.setOrientation(LinearLayout.HORIZONTAL);
            badgeLayout.setGravity(Gravity.CENTER_VERTICAL);
            badgeLayout.setBackground(UiHelper.roundedRect(context, item.getStatusBgTint(), 99f, item.getStatusColor(), 1f));
            int badgePadH = UiHelper.dp(context, 8);
            int badgePadV = UiHelper.dp(context, 3);
            badgeLayout.setPadding(badgePadH, badgePadV, badgePadH, badgePadV);

            View dotView = new View(context);
            dotView.setBackground(UiHelper.roundedRect(context, item.getStatusColor(), 99f, 0, 0));
            int dotSize = UiHelper.dp(context, 6);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.rightMargin = UiHelper.dp(context, 5);
            badgeLayout.addView(dotView, dotLp);

            TextView badgeText = new TextView(context);
            badgeText.setText(item.getStatusLabel().toUpperCase());
            badgeText.setTextColor(0xFFFAFAFA);
            badgeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, badgeSp);
            badgeText.setTypeface(Typeface.DEFAULT_BOLD);
            badgeLayout.addView(badgeText);

            header.addView(badgeLayout);

            // Botao Fechar (X)
            TextView closeBtn = new TextView(context);
            closeBtn.setText("\u2715");
            closeBtn.setTextColor(0xFFA3A3A3);
            closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            closeBtn.setGravity(Gravity.CENTER);
            int closeSize = UiHelper.dp(context, 24);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(closeSize, closeSize);
            closeLp.leftMargin = UiHelper.dp(context, 6);
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissCurrentOverlay();
                    showNextFromQueue();
                }
            });
            header.addView(closeBtn, closeLp);

            card.addView(header);

            // 2. BLOCO "DISSE QUE" COMPACTO
            LinearLayout saidBox = new LinearLayout(context);
            saidBox.setOrientation(LinearLayout.VERTICAL);
            saidBox.setBackground(UiHelper.roundedRect(context, 0xFF202020, 10f, 0xFF2E2E2E, 1f));
            int saidPadH = UiHelper.dp(context, 8);
            int saidPadV = UiHelper.dp(context, 6);
            saidBox.setPadding(saidPadH, saidPadV, saidPadH, saidPadV);
            LinearLayout.LayoutParams saidLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            saidLp.topMargin = UiHelper.dp(context, 7);
            saidLp.bottomMargin = UiHelper.dp(context, 7);

            TextView saidText = new TextView(context);
            saidText.setText("Disse: \u201C" + item.t + "\u201D");
            saidText.setTextColor(0xFFE5E5E5);
            saidText.setTextSize(TypedValue.COMPLEX_UNIT_SP, quoteSp);
            saidText.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            saidText.setMaxLines(quoteMaxLines);
            saidText.setEllipsize(TextUtils.TruncateAt.END);
            saidBox.addView(saidText);

            card.addView(saidBox, saidLp);

            // 3. BLOCO "VERIFICACAO" COMPACTO COM ALTURA ESTRITAMENTE LIMITADA
            TextView verifLabel = new TextView(context);
            verifLabel.setText("VERIFICA\u00c7\u00c3O");
            verifLabel.setTextColor(item.getStatusColor());
            verifLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp);
            verifLabel.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(verifLabel);

            final TextView verifText = new TextView(context);
            verifText.setText(item.v);
            verifText.setTextColor(0xFFFAFAFA);
            verifText.setTextSize(TypedValue.COMPLEX_UNIT_SP, verifSp);
            verifText.setMaxLines(verifMaxLines);
            verifText.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams verifLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            verifLp.topMargin = UiHelper.dp(context, 2);
            verifLp.bottomMargin = UiHelper.dp(context, 8);
            card.addView(verifText, verifLp);

            // Auto-scroll suave caso o texto tenha mais linhas que o limite compacto
            verifText.post(new Runnable() {
                @Override
                public void run() {
                    Layout layout = verifText.getLayout();
                    if (layout != null && layout.getLineCount() > verifMaxLines) {
                        verifText.setEllipsize(null);
                        int totalHeight = layout.getHeight();
                        int visibleHeight = verifText.getHeight();
                        final int scrollDist = Math.max(0, totalHeight - visibleHeight);
                        if (scrollDist > 0) {
                            int durationSec = AppPrefs.getPopupDurationSec(context);
                            long scrollMs = Math.max(3500L, (durationSec - 4) * 1000L);
                            autoScrollAnimator = ValueAnimator.ofInt(0, scrollDist);
                            autoScrollAnimator.setStartDelay(3000L);
                            autoScrollAnimator.setDuration(scrollMs);
                            autoScrollAnimator.setInterpolator(new LinearInterpolator());
                            autoScrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    verifText.scrollTo(0, (Integer) animation.getAnimatedValue());
                                }
                            });
                            autoScrollAnimator.start();
                        }
                    }
                }
            });

            // 4. BARRA FINA DE PROGRESSO (COUNTDOWN)
            FrameLayout progressTrack = new FrameLayout(context);
            progressTrack.setBackground(UiHelper.roundedRect(context, 0xFF2E2E2E, 3f, 0, 0));
            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UiHelper.dp(context, 3)
            );

            final View progressFill = new View(context);
            progressFill.setBackground(UiHelper.roundedRect(context, item.getStatusColor(), 3f, 0, 0));
            FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UiHelper.dp(context, 3)
            );
            progressTrack.addView(progressFill, fillLp);
            card.addView(progressTrack, trackLp);

            int overlayType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                overlayType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            // Medir altura exata do card antes de adicionar na janela para nunca ocupar a tela inteira!
            int widthSpec = View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY);
            int maxAllowedHeight = Math.min((int) (screenH * 0.42f), UiHelper.dp(context, 220));
            int heightSpec = View.MeasureSpec.makeMeasureSpec(maxAllowedHeight, View.MeasureSpec.AT_MOST);
            card.measure(widthSpec, heightSpec);
            int measuredHeight = card.getMeasuredHeight();

            WindowManager.LayoutParams wmParams = new WindowManager.LayoutParams(
                    cardWidth,
                    measuredHeight,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
            );
            wmParams.gravity = gravity;
            wmParams.x = marginX;
            wmParams.y = marginY;

            // Animacao de entrada suave
            card.setAlpha(0f);
            card.setTranslationY(UiHelper.dp(context, 16));
            windowManager.addView(card, wmParams);
            currentOverlayView = card;

            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            int durationSec = AppPrefs.getPopupDurationSec(context);
            long durationMs = durationSec * 1000L;

            final int fullBarWidth = cardWidth - (pad * 2);
            countdownAnimator = ValueAnimator.ofInt(fullBarWidth, 0);
            countdownAnimator.setDuration(durationMs);
            countdownAnimator.setInterpolator(new LinearInterpolator());
            countdownAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
                    lp.width = (Integer) animation.getAnimatedValue();
                    progressFill.setLayoutParams(lp);
                }
            });
            countdownAnimator.start();

            dismissRunnable = new Runnable() {
                @Override
                public void run() {
                    dismissCurrentOverlay();
                    showNextFromQueue();
                }
            };
            mainHandler.postDelayed(dismissRunnable, durationMs);

        } catch (Exception e) {
            currentOverlayView = null;
        }
    }

    public void dismissCurrentOverlay() {
        if (dismissRunnable != null) {
            mainHandler.removeCallbacks(dismissRunnable);
            dismissRunnable = null;
        }
        if (countdownAnimator != null) {
            countdownAnimator.cancel();
            countdownAnimator = null;
        }
        if (autoScrollAnimator != null) {
            autoScrollAnimator.cancel();
            autoScrollAnimator = null;
        }
        if (currentOverlayView != null) {
            try {
                windowManager.removeViewImmediate(currentOverlayView);
            } catch (Exception ignored) {
            }
            currentOverlayView = null;
        }
    }

    private void playAlertChime(int checkStatus) {
        try {
            final ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
            int tone = (checkStatus == 2) ? ToneGenerator.TONE_PROP_NACK : ToneGenerator.TONE_PROP_ACK;
            toneGen.startTone(tone, 220);
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        toneGen.release();
                    } catch (Exception ignored) {
                    }
                }
            }, 400L);
        } catch (Exception ignored) {
        }
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Alertas de Checagem ao Vivo",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifica\u00e7\u00f5es pop-up quando entra uma nova checagem de fatos");
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void sendHeadsUpNotification(FactCheckItem item) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(context, 0, openIntent, flags);

            String title = item.getCandidateName() + " \u2022 " + item.getStatusLabel().toUpperCase();
            String body = "\u201C" + item.t + "\u201D\nVerifica\u00e7\u00e3o: " + item.v;

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
                builder.setPriority(Notification.PRIORITY_HIGH);
                builder.setDefaults(Notification.DEFAULT_ALL);
            }

            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(item.v)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setColor(item.getStatusColor())
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            nm.notify(notificationIdCounter++, builder.build());
        } catch (Exception ignored) {
        }
    }
}
