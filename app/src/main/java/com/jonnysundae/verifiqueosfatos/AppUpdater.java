package com.jonnysundae.verifiqueosfatos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppUpdater {

    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/jonnysundae/verifiqueosfatos-android/releases/latest";
    private static final String DIRECT_LATEST_APK = "https://github.com/jonnysundae/verifiqueosfatos-android/releases/latest/download/verifiqueosfatos.apk";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean pendingInstallAfterPermission = false;

    public interface UpdateCheckCallback {
        void onResult(boolean hasNewVersion, String latestTag, String currentVersion);
    }

    public static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    public static void checkSilently(final Context context, final UpdateCheckCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String current = getCurrentVersionName(context);
                    JSONObject release = fetchLatestReleaseJson();
                    if (release != null) {
                        final String tag = release.optString("tag_name", "").replaceFirst("^v", "");
                        final boolean newer = !tag.isEmpty() && !tag.equals(current) && compareVersions(tag, current) > 0;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(newer, tag, current);
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void checkAndPromptUpdate(final Activity activity) {
        Toast.makeText(activity, "Verificando atualiza\u00e7\u00f5es no GitHub...", Toast.LENGTH_SHORT).show();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String current = getCurrentVersionName(activity);
                JSONObject release = fetchLatestReleaseJson();

                String latestTag = current;
                String notes = "";
                String downloadUrl = DIRECT_LATEST_APK;

                if (release != null) {
                    latestTag = release.optString("tag_name", current).replaceFirst("^v", "");
                    notes = release.optString("body", "");
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets != null && assets.length() > 0) {
                        JSONObject firstAsset = assets.optJSONObject(0);
                        if (firstAsset != null) {
                            String url = firstAsset.optString("browser_download_url", "");
                            if (!url.isEmpty()) {
                                downloadUrl = url;
                            }
                        }
                    }
                }

                final String finalTag = latestTag;
                final String finalNotes = notes;
                final String finalUrl = downloadUrl;
                final boolean isNewer = compareVersions(finalTag, current) > 0;

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing()) return;
                        AlertDialog.Builder b = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
                        if (isNewer) {
                            b.setTitle("\uD83D\uDE80 Nova Vers\u00e3o Dispon\u00edvel: v" + finalTag);
                            b.setMessage("Vers\u00e3o instalada: v" + current + "\nNova vers\u00e3o: v" + finalTag
                                    + (finalNotes.isEmpty() ? "" : "\n\nNovidades:\n" + finalNotes)
                                    + "\n\nDeseja baixar e atualizar o aplicativo agora?");
                            b.setPositiveButton("Atualizar Agora", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    downloadAndInstallApk(activity, finalUrl);
                                }
                            });
                            b.setNegativeButton("Cancelar", null);
                        } else {
                            b.setTitle("\u2705 App Atualizado (v" + current + ")");
                            b.setMessage("Voc\u00ea j\u00e1 est\u00e1 na vers\u00e3o mais recente publicada (v" + finalTag + ").\n\nSe desejar, voc\u00ea pode for\u00e7ar o download e reinstala\u00e7\u00e3o do APK mais recente do GitHub.");
                            b.setPositiveButton("Baixar e Reinstalar Mesmo Assim", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    downloadAndInstallApk(activity, finalUrl);
                                }
                            });
                            b.setNegativeButton("Fechar", null);
                        }
                        b.show();
                    }
                });
            }
        });
    }

    public static void downloadAndInstallApk(final Activity activity, final String apkUrl) {
        Toast.makeText(activity, "Baixando nova vers\u00e3o do APK...", Toast.LENGTH_SHORT).show();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String urlWithBust = apkUrl + (apkUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                    URL url = new URL(urlWithBust);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "VerifiqueOsFatos-AutoUpdater/1.2");

                    int status = conn.getResponseCode();
                    // Seguir redirecionamento manualmente caso mude de protocolo/dominio (github -> objects.githubusercontent)
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP
                            || status == HttpURLConnection.HTTP_MOVED_PERM
                            || status == HttpURLConnection.HTTP_SEE_OTHER
                            || status == 307 || status == 308) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = (HttpURLConnection) new URL(newUrl).openConnection();
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(15000);
                    }

                    InputStream is = conn.getInputStream();
                    File outFile = ApkContentProvider.getApkUpdateFile(activity);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                    fos.close();
                    is.close();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            triggerInstallIntent(activity);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, "Erro ao baixar APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    public static void triggerInstallIntent(Activity activity) {
        File apkFile = ApkContentProvider.getApkUpdateFile(activity);
        if (!apkFile.exists()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                pendingInstallAfterPermission = true;
                Toast.makeText(
                        activity,
                        "Autorize 'Instalar apps desconhecidos' para concluir a atualiza\u00e7\u00e3o!",
                        Toast.LENGTH_LONG
                ).show();
                try {
                    Intent permIntent = new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())
                    );
                    activity.startActivity(permIntent);
                    return;
                } catch (Exception ignored) {
                }
            }
        }

        pendingInstallAfterPermission = false;
        try {
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri contentUri = ApkContentProvider.getApkContentUri();
                installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                installIntent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(activity, "N\u00e3o foi poss\u00edvel abrir o instalador: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static JSONObject fetchLatestReleaseJson() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(LATEST_RELEASE_API + "?t=" + System.currentTimeMillis());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "VerifiqueOsFatos-AutoUpdater/1.2");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return new JSONObject(sb.toString());
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private static int compareVersions(String v1, String v2) {
        try {
            String[] p1 = v1.split("\\.");
            String[] p2 = v2.split("\\.");
            int len = Math.max(p1.length, p2.length);
            for (int i = 0; i < len; i++) {
                int n1 = i < p1.length ? Integer.parseInt(p1[i].replaceAll("[^0-9]", "")) : 0;
                int n2 = i < p2.length ? Integer.parseInt(p2[i].replaceAll("[^0-9]", "")) : 0;
                if (n1 != n2) {
                    return n1 - n2;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
