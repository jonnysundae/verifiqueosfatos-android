package com.jonnysundae.verifiqueosfatos;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class AppPrefs {
    private static final String PREFS_NAME = "verifique_os_fatos_prefs";

    private static final String KEY_MONITORING_ACTIVE = "monitoring_active";
    private static final String KEY_POPUP_POSITION = "popup_position"; // 0=Inf.Direito, 1=Sup.Direito, 2=Rodape GC, 3=Topo Central
    private static final String KEY_POPUP_SIZE = "popup_size"; // 0=Compacto (Padrao), 1=Mini, 2=Grande (TV)
    private static final String KEY_POPUP_DURATION_SEC = "popup_duration_sec"; // 15, 25, 35, 45
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_CACHED_JSON = "cached_json";
    private static final String KEY_SEEN_KEYS = "seen_keys";
    private static final String KEY_INITIALIZED_SEEN = "initialized_seen";
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";

    public static final String DEFAULT_POSTS_URL = "https://verifiqueosfatos.com/posts.json";

    private static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isMonitoringActive(Context context) {
        return get(context).getBoolean(KEY_MONITORING_ACTIVE, true);
    }

    public static void setMonitoringActive(Context context, boolean active) {
        get(context).edit().putBoolean(KEY_MONITORING_ACTIVE, active).apply();
    }

    public static int getPopupPosition(Context context) {
        return get(context).getInt(KEY_POPUP_POSITION, 0);
    }

    public static void setPopupPosition(Context context, int pos) {
        get(context).edit().putInt(KEY_POPUP_POSITION, pos % 4).apply();
    }

    public static String getPopupPositionLabel(Context context) {
        switch (getPopupPosition(context)) {
            case 0:
                return "Canto Inf. Direito (TV)";
            case 1:
                return "Canto Sup. Direito";
            case 2:
                return "Rodap\u00e9 (Estilo GC TV)";
            case 3:
                return "Topo Central";
            default:
                return "Canto Inf. Direito";
        }
    }

    public static int getPopupSize(Context context) {
        return get(context).getInt(KEY_POPUP_SIZE, 0);
    }

    public static void cyclePopupSize(Context context) {
        int next = (getPopupSize(context) + 1) % 3;
        get(context).edit().putInt(KEY_POPUP_SIZE, next).apply();
    }

    public static String getPopupSizeLabel(Context context) {
        switch (getPopupSize(context)) {
            case 1:
                return "Mini (P)";
            case 2:
                return "Grande (TV)";
            default:
                return "Compacto (M)";
        }
    }

    public static int getPopupDurationSec(Context context) {
        return get(context).getInt(KEY_POPUP_DURATION_SEC, 20);
    }

    public static void cyclePopupDurationSec(Context context) {
        int current = getPopupDurationSec(context);
        int next;
        if (current <= 15) next = 20;
        else if (current <= 20) next = 30;
        else if (current <= 30) next = 45;
        else next = 15;
        get(context).edit().putInt(KEY_POPUP_DURATION_SEC, next).apply();
    }

    public static boolean isSoundEnabled(Context context) {
        return get(context).getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    public static String getCachedJson(Context context) {
        return get(context).getString(KEY_CACHED_JSON, "[]");
    }

    public static void setCachedJson(Context context, String json) {
        get(context).edit().putString(KEY_CACHED_JSON, json).apply();
    }

    public static Set<String> getSeenKeys(Context context) {
        Set<String> set = get(context).getStringSet(KEY_SEEN_KEYS, null);
        return set == null ? new HashSet<String>() : new HashSet<String>(set);
    }

    public static void setSeenKeys(Context context, Set<String> keys) {
        get(context).edit()
                .putStringSet(KEY_SEEN_KEYS, new HashSet<String>(keys))
                .putBoolean(KEY_INITIALIZED_SEEN, true)
                .apply();
    }

    public static boolean hasInitializedSeen(Context context) {
        return get(context).getBoolean(KEY_INITIALIZED_SEEN, false);
    }

    public static void resetSeenKeys(Context context) {
        get(context).edit()
                .remove(KEY_SEEN_KEYS)
                .putBoolean(KEY_INITIALIZED_SEEN, false)
                .apply();
    }

    public static String getLastCheckTime(Context context) {
        return get(context).getString(KEY_LAST_CHECK_TIME, "Aguardando...");
    }

    public static void setLastCheckTime(Context context, String timeStr) {
        get(context).edit().putString(KEY_LAST_CHECK_TIME, timeStr).apply();
    }
}
