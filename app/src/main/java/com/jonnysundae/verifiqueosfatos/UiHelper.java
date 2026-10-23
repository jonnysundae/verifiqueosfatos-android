package com.jonnysundae.verifiqueosfatos;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

public class UiHelper {

    public static int dp(Context context, float dp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm));
    }

    public static boolean isTelevision(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode;
        return (uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static GradientDrawable roundedRect(Context context, int fillColor, float radiusDp, int strokeColor, float strokeWidthDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(context, radiusDp));
        if (strokeWidthDp > 0) {
            gd.setStroke(dp(context, strokeWidthDp), strokeColor);
        }
        return gd;
    }

    public static Drawable focusableButtonDrawable(Context context, int normalBg, int focusedBg, int strokeNormal, int strokeFocused, float radiusDp) {
        StateListDrawable sld = new StateListDrawable();

        // Focused (Controle remoto da Smart TV / D-Pad)
        GradientDrawable focused = roundedRect(context, focusedBg, radiusDp, strokeFocused, 2.5f);
        sld.addState(new int[]{android.R.attr.state_focused}, focused);

        // Pressed (Touch no celular ou OK no controle)
        GradientDrawable pressed = roundedRect(context, focusedBg, radiusDp, strokeFocused, 2.0f);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);

        // Normal
        GradientDrawable normal = roundedRect(context, normalBg, radiusDp, strokeNormal, 1.0f);
        sld.addState(new int[]{}, normal);

        return sld;
    }

    public static Bitmap getCircularBitmap(Context context, int drawableResId, int sizePx) {
        try {
            Bitmap src = BitmapFactory.decodeResource(context.getResources(), drawableResId);
            if (src == null) return null;

            Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
            Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            BitmapShader shader = new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);

            float r = sizePx / 2f;
            canvas.drawCircle(r, r, r, paint);

            // Borda sutil circular
            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(0xFF404040);
            borderPaint.setStrokeWidth(Math.max(2f, sizePx * 0.03f));
            canvas.drawCircle(r, r, r - borderPaint.getStrokeWidth() / 2f, borderPaint);

            return output;
        } catch (Exception e) {
            return null;
        }
    }
}
