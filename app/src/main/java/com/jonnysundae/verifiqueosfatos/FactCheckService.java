package com.jonnysundae.verifiqueosfatos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FactCheckService extends Service {

    public static final String ACTION_START = "com.jonnysundae.verifiqueosfatos.ACTION_START";
    public static final String ACTION_STOP = "com.jonnysundae.verifiqueosfatos.ACTION_STOP";
    public static final String ACTION_FORCE_CHECK = "com.jonnysundae.verifiqueosfatos.ACTION_FORCE_CHECK";
    public static final String ACTION_TEST_NOW = "com.jonnysundae.verifiqueosfatos.ACTION_TEST_NOW";
    public static final String ACTION_TEST_DELAYED = "com.jonnysundae.verifiqueosfatos.ACTION_TEST_DELAYED";

    public static final String ACTION_UI_UPDATE = "com.jonnysundae.verifiqueosfatos.ACTION_UI_UPDATE";

    private static final String SERVICE_CHANNEL_ID = "verifique_fatos_service_channel";
    private static final int FOREGROUND_ID = 1001;
    private static final long POLL_INTERVAL_MS = 15000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private boolean isRunning = false;
    private int demoToggle = 0;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            performNetworkCheck(false);
            long jitterMs = (System.currentTimeMillis() / 1000L) % 10L * 1000L;
            mainHandler.postDelayed(this, POLL_INTERVAL_MS + Math.min(jitterMs, 5000L));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ensureServiceChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            AppPrefs.setMonitoringActive(this, false);
            isRunning = false;
            mainHandler.removeCallbacks(pollRunnable);
            OverlayPopupManager.getInstance(this).dismissCurrentOverlay();
            notifyUiUpdate();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();

        if (ACTION_TEST_NOW.equals(action)) {
            triggerDemoPopup();
        } else if (ACTION_TEST_DELAYED.equals(action)) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    triggerDemoPopup();
                }
            }, 5000L);
        } else if (ACTION_FORCE_CHECK.equals(action)) {
            performNetworkCheck(true);
        }

        if (!isRunning) {
            isRunning = true;
            AppPrefs.setMonitoringActive(this, true);
            mainHandler.removeCallbacks(pollRunnable);
            mainHandler.post(pollRunnable);
            notifyUiUpdate();
        }

        return START_STICKY;
    }

    private void triggerDemoPopup() {
        List<FactCheckItem> cached = FactCheckItem.parseJsonArray(AppPrefs.getCachedJson(this));
        FactCheckItem demoItem;
        if (!cached.isEmpty()) {
            int idx = (demoToggle++) % cached.size();
            demoItem = cached.get(idx);
        } else {
            int cand = (demoToggle++ % 2) + 1;
            int status = (demoToggle % 3) + 1;
            demoItem = new FactCheckItem(
                    cand,
                    status,
                    "Declara\u00e7\u00e3o de teste para validar a exibi\u00e7\u00e3o do popup ao vivo na sua Smart TV ou celular.",
                    "Este \u00e9 um card de demonstra\u00e7\u00e3o do Verifique os Fatos. Quando entrar uma nova checagem em verifiqueosfatos.com/posts.json, ela aparecer\u00e1 automaticamente aqui sobre o seu v\u00eddeo!"
            );
        }
        OverlayPopupManager.getInstance(this).showImmediatePopup(demoItem);
    }

    private void performNetworkCheck(final boolean showLatestIfNoNew) {
        networkExecutor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String cacheBuster = generateAlphanumeric(6);
                    String urlStr = AppPrefs.DEFAULT_POSTS_URL + "?v=" + cacheBuster;
                    URL url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setUseCaches(false);
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                    conn.setRequestProperty("Pragma", "no-cache");
                    conn.setRequestProperty("User-Agent", "VerifiqueOsFatos-AndroidTV/1.0");

                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        InputStream is = conn.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();

                        String json = sb.toString().trim();
                        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                        AppPrefs.setLastCheckTime(FactCheckService.this, timeStr + " (OK)");

                        handleFetchedJson(json, showLatestIfNoNew);
                        notifyUiUpdate();
                    }
                } catch (Exception e) {
                    String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    AppPrefs.setLastCheckTime(FactCheckService.this, timeStr + " (Offline/Retry)");
                    notifyUiUpdate();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    private void handleFetchedJson(String json, boolean showLatestIfNoNew) {
        List<FactCheckItem> items = FactCheckItem.parseJsonArray(json);

        // Se o servidor limpou o array ([]), limpamos o estado local igual ao site oficial
        if (json.startsWith("[") && items.isEmpty()) {
            AppPrefs.setCachedJson(this, "[]");
            AppPrefs.resetSeenKeys(this);
            return;
        }

        if (items.isEmpty()) {
            return;
        }

        AppPrefs.setCachedJson(this, json);

        boolean hadInitialized = AppPrefs.hasInitializedSeen(this);
        Set<String> seenKeys = AppPrefs.getSeenKeys(this);

        if (!hadInitialized) {
            // Primeira sincronizacao: salva todos os atuais como vistos e mostra apenas o ultimo card
            for (FactCheckItem item : items) {
                seenKeys.add(item.getUniqueKey());
            }
            AppPrefs.setSeenKeys(this, seenKeys);
            FactCheckItem latest = items.get(items.size() - 1);
            OverlayPopupManager.getInstance(this).enqueuePopup(latest, true);
            return;
        }

        int newCount = 0;
        for (FactCheckItem item : items) {
            String key = item.getUniqueKey();
            if (!seenKeys.contains(key)) {
                seenKeys.add(key);
                newCount++;
                OverlayPopupManager.getInstance(this).enqueuePopup(item, true);
            }
        }

        if (newCount > 0) {
            AppPrefs.setSeenKeys(this, seenKeys);
        } else if (showLatestIfNoNew) {
            FactCheckItem latest = items.get(items.size() - 1);
            OverlayPopupManager.getInstance(this).showImmediatePopup(latest);
        }
    }

    private String generateAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void startInForeground() {
        Intent openIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, SERVICE_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Verifique os Fatos \u2022 Monitorando ao vivo")
                .setContentText("Aguardando novas checagens em verifiqueosfatos.com")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private void ensureServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        SERVICE_CHANNEL_ID,
                        "Monitoramento em Segundo Plano",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Mant\u00e9m o monitoramento de fatos ativo durante o debate");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void notifyUiUpdate() {
        Intent broadcast = new Intent(ACTION_UI_UPDATE);
        broadcast.setPackage(getPackageName());
        sendBroadcast(broadcast);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        mainHandler.removeCallbacks(pollRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
