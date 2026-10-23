package com.jonnysundae.verifiqueosfatos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class FactCheckItem {
    public final int n; // 1 = Lula, 2 = Flavio
    public final int c; // 1 = Verdadeiro, 2 = Falso, 3 = Parcial
    public final String t; // Declaracao ("Disse que:")
    public final String v; // Verificacao ("Verificacao:")
    public final String h; // Horario opcional (ex: "21:14")
    public final String a; // Assunto/Tema opcional (ex: "Economia")
    public final String e; // Correcao/Errata opcional ("Correcao:")
    public final List<String> s; // Fontes opcionais (URLs)

    public FactCheckItem(int n, int c, String t, String v, String h, String a, String e, List<String> s) {
        this.n = n;
        this.c = c;
        this.t = t != null ? t.trim() : "";
        this.v = v != null ? v.trim() : "";
        this.h = h != null ? h.trim() : "";
        this.a = a != null ? a.trim() : "";
        this.e = e != null ? e.trim() : "";
        this.s = s != null ? s : new ArrayList<String>();
    }

    public FactCheckItem(int n, int c, String t, String v) {
        this(n, c, t, v, "", "", "", null);
    }

    public String getCandidateName() {
        switch (n) {
            case 1:
                return "Lula";
            case 2:
                return "Fl\u00e1vio";
            default:
                return "Candidato " + n;
        }
    }

    public int getCandidateAvatarRes() {
        switch (n) {
            case 1:
                return R.drawable.lula;
            case 2:
                return R.drawable.flavio;
            default:
                return R.drawable.ic_launcher;
        }
    }

    public String getStatusLabel() {
        switch (c) {
            case 1:
                return "Verdadeiro";
            case 2:
                return "Falso";
            case 3:
                return "Parcial";
            default:
                return "Checagem";
        }
    }

    // Novas cores solidas de --tone do repositorio original (style.css atualizado)
    public int getStatusColor() {
        switch (c) {
            case 1:
                return 0xFF15803D; // Verde solido (#15803D)
            case 2:
                return 0xFFB91C1C; // Vermelho solido (#B91C1C)
            case 3:
                return 0xFFC2410C; // Laranja/Ferrugem solido (#C2410C)
            default:
                return 0xFF525252;
        }
    }

    public boolean hasMeta() {
        return !h.isEmpty() || !a.isEmpty();
    }

    public String getMetaText() {
        if (!h.isEmpty() && !a.isEmpty()) {
            return h + "  \u2022  " + a;
        }
        return !h.isEmpty() ? h : a;
    }

    public static String extractHostname(String urlStr) {
        try {
            URI uri = new URI(urlStr);
            String host = uri.getHost();
            if (host != null) {
                return host.replaceFirst("^www\\.", "");
            }
        } catch (Exception ignored) {
        }
        return urlStr;
    }

    public String getUniqueKey() {
        return n + "|" + c + "|" + t + "|" + v + "|" + h + "|" + a + "|" + e;
    }

    public static List<FactCheckItem> parseJsonArray(String jsonStr) {
        List<FactCheckItem> result = new ArrayList<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    int n = obj.optInt("n", 1);
                    int c = obj.optInt("c", 1);
                    String t = obj.optString("t", "");
                    String v = obj.optString("v", "");
                    String h = obj.optString("h", "");
                    String a = obj.optString("a", "");
                    String e = obj.optString("e", "");
                    List<String> sources = new ArrayList<>();
                    JSONArray sArr = obj.optJSONArray("s");
                    if (sArr != null) {
                        for (int j = 0; j < sArr.length(); j++) {
                            String u = sArr.optString(j, "").trim();
                            if (u.startsWith("http://") || u.startsWith("https://")) {
                                sources.add(u);
                            }
                        }
                    }
                    if (!t.isEmpty() || !v.isEmpty()) {
                        result.add(new FactCheckItem(n, c, t, v, h, a, e, sources));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
