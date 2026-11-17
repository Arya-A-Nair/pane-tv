package tv.nova;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The design tokens from DESIGN.md — colour, space, type — and the few helpers every screen builds with. */
final class Ui {
    // colour
    static final int OBSIDIAN = 0xff090b10, GRAPHITE = 0xff20242c, PORCELAIN = 0xffe9ecf2, MIST = 0xffced4df,
            GLACIER = 0xffa4b8ee, CHAMPAGNE = 0xffb4a28c, CHAMPAGNE_LIGHT = 0xffe0d2bf;
    /** Air quality from good to hazardous. Used for marks only, never for text. */
    static final int[] AIR = {0xff8fcba8, 0xffd9c58a, 0xffe0a66e, 0xffde8266, 0xffc97b9e, 0xffa97fb5};

    // space, in dp on a 960 x 540 canvas
    static final float W = 960, H = 540, MARGIN = 56, TOP = 48, BOTTOM = 48, SAFE = 48, CONTENT_TOP = 112;

    final Context context;
    final float dp;
    final Typeface manrope, inter, interMedium, mono;

    Ui(Context c) {
        context = c;
        dp = c.getResources().getDisplayMetrics().density;
        manrope = Typeface.createFromAsset(c.getAssets(), "fonts/manrope-light.ttf");
        inter = Typeface.createFromAsset(c.getAssets(), "fonts/inter-regular.ttf");
        interMedium = Typeface.createFromAsset(c.getAssets(), "fonts/inter-medium.ttf");
        mono = Typeface.createFromAsset(c.getAssets(), "fonts/jbmono-regular.ttf");
    }

    int px(float dps) { return Math.round(dps * dp); }

    TextView text(Typeface face, float sp, int color) {
        TextView t = new TextView(context);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (face == mono) t.setLetterSpacing(0.01f);
        return t;
    }

    /** Text standing directly on the environment gets a wide, soft shadow: insurance against a busy picture. */
    void shadow(TextView t) { t.setShadowLayer(px(8), 0, px(1), 0x73000000); }

    void place(ViewGroup parent, View v, float leftDp, float topDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(leftDp);
        lp.topMargin = px(topDp);
        parent.addView(v, lp);
    }

    GlassPanel panel(ViewGroup parent, float x, float y, float w, float h) {
        GlassPanel g = new GlassPanel(context, dp, 28);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(w), px(h));
        l.leftMargin = px(x);
        l.topMargin = px(y);
        parent.addView(g, l);
        return g;
    }

    /** A small round mark. */
    View dot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        View v = new View(context);
        v.setBackground(d);
        return v;
    }

    static FrameLayout.LayoutParams fill() { return new FrameLayout.LayoutParams(-1, -1); }

    static String time() { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()); }
    static String date() { return new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(new Date()); }
}
