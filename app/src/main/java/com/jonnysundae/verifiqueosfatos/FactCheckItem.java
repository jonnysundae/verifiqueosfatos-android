package com.jonnysundae.verifiqueosfatos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FactCheckItem {
    public final int n; // 1 = Lula, 2 = Flavio
    public final int c; // 1 = Verdadeiro, 2 = Falso, 3 = Parcial
    public final String t; // Declaracao ("Disse que...")
    public final String v; // Verificacao ("Verificacao...")

    public FactCheckItem(int n, int c, String t, String v) {
        this.n = n;
        this.c = c;
        this.t = t != null ? t.trim() : "";
        this.v = v != null ? v.trim() : "";
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

    public int getStatusColor() {
        switch (c) {
            case 1:
                return 0xFF16A34A; // Verde (#16A34A)
            case 2:
                return 0xFFDC2626; // Vermelho (#DC2626)
            case 3:
                return 0xFFF59E0B; // Amarelo/Ambar para destaque em TV (#F59E0B)
            default:
                return 0xFF8E939C;
        }
    }

    public int getStatusBgTint() {
        switch (c) {
            case 1:
                return 0x2616A34A;
            case 2:
                return 0x26DC2626;
            case 3:
                return 0x26F59E0B;
            default:
                return 0x268E939C;
        }
    }

    public String getUniqueKey() {
        return n + "|" + c + "|" + t + "|" + v;
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
                    if (!t.isEmpty() || !v.isEmpty()) {
                        result.add(new FactCheckItem(n, c, t, v));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
