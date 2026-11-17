package tv.pane;

import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A check for the rules in DESIGN.md that a machine can verify: logs (tag AUDIT) any text
 * that does not fit or is under 13 sp, and anything drawn outside the safe area. Run from
 * adb with "--ez audit true"; it never runs on its own.
 */
final class LayoutAudit {
    private LayoutAudit() {}
    private static final String TAG = "AUDIT";

    static void run(View root, Ui ui) { check(root, root, ui); }

    private static void check(View v, View root, Ui ui) {
        float scaleX = 1, scaleY = 1, alpha = 1;
        for (View q = v; q != null && q != root; q = q.getParent() instanceof View ? (View) q.getParent() : null) {
            scaleX *= q.getScaleX();
            scaleY *= q.getScaleY();
            alpha *= q.getAlpha();
        }
        if (alpha < 0.05f || !v.isShown()) return;
        int[] at = new int[2];
        v.getLocationOnScreen(at);
        float left = at[0], top = at[1], right = left + v.getWidth() * scaleX, bottom = top + v.getHeight() * scaleY;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (t.getLineCount() == 1) {   // measure the ink, not the line box
                Rect ink = new Rect();
                t.getPaint().getTextBounds("H", 0, 1, ink);
                top += t.getBaseline() - ink.height();
                bottom = top + ink.height();
            }
            if (t.getLayout() != null) {
                int lines = t.getLayout().getLineCount();
                float widest = 0;
                for (String line : t.getText().toString().split("\n")) widest = Math.max(widest, t.getPaint().measureText(line));
                boolean cut = lines > 0 && (t.getLayout().getEllipsisCount(lines - 1) > 0
                        || (lines > t.getMaxLines() && t.getMaxLines() > 0));   // more lines than are shown
                boolean tooTall = lines > 1 && t.getLayout().getHeight() > t.getHeight() + 1;   // one line is judged by its ink, above
                boolean tooWide = t.getMaxLines() == 1 && widest > t.getWidth() - t.getPaddingLeft() - t.getPaddingRight() + 1;
                if (cut || tooTall || tooWide) Log.w(TAG, "text does not fit: " + name(v));
            }
            if (t.getTextSize() < 13 * ui.dp - 0.5f) Log.w(TAG, "text under 13 sp: " + name(v));
        }
        boolean leaf = !(v instanceof ViewGroup) || v instanceof GlassPanel;
        float safe = ui.px(Ui.SAFE) - 1;
        if (leaf && v.getWidth() > 0 && (left < safe || top < safe || right > ui.px(Ui.W) - safe || bottom > ui.px(Ui.H) - safe))
            Log.w(TAG, "outside the safe area: " + name(v) + " [" + left / ui.dp + ", " + top / ui.dp + " – " + right / ui.dp + ", " + bottom / ui.dp + "]");
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) check(((ViewGroup) v).getChildAt(i), root, ui);
    }

    private static String name(View v) { return v.getClass().getSimpleName() + (v instanceof TextView ? " \"" + ((TextView) v).getText() + "\"" : ""); }
}
