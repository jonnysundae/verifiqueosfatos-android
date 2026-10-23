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
import android.widget.ScrollView;
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
            int cardWidth;
            int gravity;
            int marginX = UiHelper.dp(context, 24);
            int marginY = UiHelper.dp(context, 24);

            if (isLandscape) {
                if (posMode == 2) {
                    // Rodape Central estilo GC de Telejornal
                    cardWidth = Math.min(screenW - UiHelper.dp(context, 80), UiHelper.dp(context, 720));
                    gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                } else if (posMode == 3) {
                    // Topo Central
                    cardWidth = Math.min(screenW - UiHelper.dp(context, 80), UiHelper.dp(context, 680));
                    gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                } else if (posMode == 1) {
                    // Canto Superior Direito
                    cardWidth = Math.min((int) (screenW * 0.45f), UiHelper.dp(context, 480));
                    gravity = Gravity.TOP | Gravity.END;
                } else {
                    // Canto Inferior Direito (Padrao TV)
                    cardWidth = Math.min((int) (screenW * 0.45f), UiHelper.dp(context, 480));
                    gravity = Gravity.BOTTOM | Gravity.END;
                }
            } else {
                // Smartphone em modo retrato
                cardWidth = screenW - UiHelper.dp(context, 24);
                marginX = UiHelper.dp(context, 12);
                marginY = UiHelper.dp(context, 36);
                if (posMode == 1 || posMode == 3) {
                    gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                } else {
                    gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                }
            }

            final FrameLayout rootWrapper = new FrameLayout(context);

            // Card principal escuro com borda na cor do status
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(UiHelper.roundedRect(context, 0xF5141414, 20f, item.getStatusColor(), 2.0f));
            int pad = UiHelper.dp(context, 18);
            card.setPadding(pad, pad, pad, UiHelper.dp(context, 14));

            // 1. HEADER: Avatar + Nome do Candidato + Badge de Verificacao + Botao Fechar
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            int avatarSize = UiHelper.dp(context, 48);
            ImageView avatarView = new ImageView(context);
            Bitmap circularAvatar = UiHelper.getCircularBitmap(context, item.getCandidateAvatarRes(), avatarSize);
            if (circularAvatar != null) {
                avatarView.setImageBitmap(circularAvatar);
            } else {
                avatarView.setImageResource(item.getCandidateAvatarRes());
            }
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(avatarSize, avatarSize);
            avatarLp.rightMargin = UiHelper.dp(context, 12);
            header.addView(avatarView, avatarLp);

            // Coluna Nome + Subtitulo
            LinearLayout titleCol = new LinearLayout(context);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            TextView candidateTv = new TextView(context);
            candidateTv.setText(item.getCandidateName());
            candidateTv.setTextColor(0xFFFAFAFA);
            candidateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            candidateTv.setTypeface(Typeface.DEFAULT_BOLD);
            titleCol.addView(candidateTv);

            TextView liveTagTv = new TextView(context);
            liveTagTv.setText("VERIFIQUE OS FATOS \u2022 AO VIVO");
            liveTagTv.setTextColor(0xFFA3A3A3);
            liveTagTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            liveTagTv.setTypeface(Typeface.DEFAULT_BOLD);
            titleCol.addView(liveTagTv);

            header.addView(titleCol, titleColLp);

            // Badge (Verdadeiro / Falso / Parcial)
            LinearLayout badgeLayout = new LinearLayout(context);
            badgeLayout.setOrientation(LinearLayout.HORIZONTAL);
            badgeLayout.setGravity(Gravity.CENTER_VERTICAL);
            badgeLayout.setBackground(UiHelper.roundedRect(context, item.getStatusBgTint(), 99f, item.getStatusColor(), 1.5f));
            int badgePadH = UiHelper.dp(context, 12);
            int badgePadV = UiHelper.dp(context, 6);
            badgeLayout.setPadding(badgePadH, badgePadV, badgePadH, badgePadV);

            View dotView = new View(context);
            dotView.setBackground(UiHelper.roundedRect(context, item.getStatusColor(), 99f, 0, 0));
            int dotSize = UiHelper.dp(context, 9);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.rightMargin = UiHelper.dp(context, 6);
            badgeLayout.addView(dotView, dotLp);

            TextView badgeText = new TextView(context);
            badgeText.setText(item.getStatusLabel().toUpperCase());
            badgeText.setTextColor(0xFFFAFAFA);
            badgeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            badgeText.setTypeface(Typeface.DEFAULT_BOLD);
            badgeLayout.addView(badgeText);

            header.addView(badgeLayout);

            // Botao fechar para toque
            TextView closeBtn = new TextView(context);
            closeBtn.setText("\u2715");
            closeBtn.setTextColor(0xFFA3A3A3);
            closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            closeBtn.setGravity(Gravity.CENTER);
            int closeSize = UiHelper.dp(context, 32);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(closeSize, closeSize);
            closeLp.leftMargin = UiHelper.dp(context, 8);
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissCurrentOverlay();
                    showNextFromQueue();
                }
            });
            header.addView(closeBtn, closeLp);

            card.addView(header);

            // 2. BLOCO "DISSE QUE"
            LinearLayout saidBox = new LinearLayout(context);
            saidBox.setOrientation(LinearLayout.VERTICAL);
            saidBox.setBackground(UiHelper.roundedRect(context, 0xFF222222, 14f, 0xFF333333, 1f));
            int saidPad = UiHelper.dp(context, 13);
            saidBox.setPadding(saidPad, UiHelper.dp(context, 10), saidPad, UiHelper.dp(context, 12));
            LinearLayout.LayoutParams saidLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            saidLp.topMargin = UiHelper.dp(context, 12);
            saidLp.bottomMargin = UiHelper.dp(context, 12);

            TextView saidLabel = new TextView(context);
            saidLabel.setText("DISSE QUE");
            saidLabel.setTextColor(0xFFA3A3A3);
            saidLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            saidLabel.setTypeface(Typeface.DEFAULT_BOLD);
            saidBox.addView(saidLabel);

            TextView saidText = new TextView(context);
            saidText.setText("\u201C" + item.t + "\u201D");
            saidText.setTextColor(0xFFFAFAFA);
            saidText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            saidText.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            saidText.setMaxLines(4);
            saidText.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams saidTextLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            saidTextLp.topMargin = UiHelper.dp(context, 4);
            saidBox.addView(saidText, saidTextLp);

            card.addView(saidBox, saidLp);

            // 3. BLOCO "VERIFICACAO"
            TextView verifLabel = new TextView(context);
            verifLabel.setText("VERIFICA\u00c7\u00c3O");
            verifLabel.setTextColor(item.getStatusColor());
            verifLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            verifLabel.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(verifLabel);

            final ScrollView verifScroll = new ScrollView(context);
            verifScroll.setVerticalScrollBarEnabled(false);
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                     ViewGroup.LayoutParams.WRAP_CONTENT
            );
            scrollLp.topMargin = UiHelper.dp(context, 4);
            scrollLp.bottomMargin = UiHelper.dp(context, 12);

            final TextView verifText = new TextView(context);
            verifText.setText(item.v);
            verifText.setTextColor(0xFFE5E5E5);
            verifText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            verifText.setLineSpacing(UiHelper.dp(context, 2), 1.05f);
            verifScroll.addView(verifText);

            card.addView(verifScroll, scrollLp);

            // Limitar altura maxima do texto de verificacao para nao ocupar a TV toda
            final int maxScrollHeight = UiHelper.dp(context, isLandscape ? 130 : 180);
            verifText.post(new Runnable() {
                @Override
                public void run() {
                    int textH = verifText.getHeight();
                    if (textH > maxScrollHeight) {
                        ViewGroup.LayoutParams lp = verifScroll.getLayoutParams();
                        lp.height = maxScrollHeight;
                        verifScroll.setLayoutParams(lp);

                        // Auto-scroll suave para leitura em Smart TV sem precisar de controle remoto
                        final int scrollDistance = textH - maxScrollHeight;
                        int durationSec = AppPrefs.getPopupDurationSec(context);
                        long scrollDurationMs = Math.max(4000L, (durationSec - 5) * 1000L);
                        autoScrollAnimator = ValueAnimator.ofInt(0, scrollDistance);
                        autoScrollAnimator.setStartDelay(3500L);
                        autoScrollAnimator.setDuration(scrollDurationMs);
                        autoScrollAnimator.setInterpolator(new LinearInterpolator());
                        autoScrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                verifScroll.scrollTo(0, (Integer) animation.getAnimatedValue());
                            }
                        });
                        autoScrollAnimator.start();
                    }
                }
            });

            // 4. BARRA DE PROGRESSO DE TEMPO (COUNTDOWN)
            FrameLayout progressTrack = new FrameLayout(context);
            progressTrack.setBackground(UiHelper.roundedRect(context, 0xFF2E2E2E, 4f, 0, 0));
            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UiHelper.dp(context, 4)
            );

            final View progressFill = new View(context);
            progressFill.setBackground(UiHelper.roundedRect(context, item.getStatusColor(), 4f, 0, 0));
            FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            progressTrack.addView(progressFill, fillLp);
            card.addView(progressTrack, trackLp);

            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                    cardWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rootWrapper.addView(card, cardLp);

            int overlayType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                overlayType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            // FLAG_NOT_FOCUSABLE garante que na Smart TV o video do debate nao pause nem perca foco
            WindowManager.LayoutParams wmParams = new WindowManager.LayoutParams(
                    cardWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
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
            rootWrapper.setAlpha(0f);
            rootWrapper.setTranslationY(UiHelper.dp(context, 24));
            windowManager.addView(rootWrapper, wmParams);
            currentOverlayView = rootWrapper;

            rootWrapper.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320L)
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
