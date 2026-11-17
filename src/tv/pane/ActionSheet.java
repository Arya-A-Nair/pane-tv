package tv.pane;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A small glass sheet of actions beside the app it is about: a Mist caption naming the app, then
 * one line per action in words. Focus is a solid Porcelain capsule, as in Settings. It sits below
 * the tile when there is room, above it otherwise. Up and Down choose, OK does it, Back or Menu closes.
 */
final class ActionSheet {
    private static final float WIDTH = 248, ROW = 38, GAP = 6, INSET = 6, BAR_TOP = 444;

    private final Pane host;
    private final GlassPanel view;
    private final View scrim;   // Obsidian over the page, so the sheet reads as the one thing to act on
    private final TextView[] rows;
    private final Runnable[] actions;
    private int selected;
    private boolean dismissed;

    /** actions[i] runs when labels[i] is chosen; null just closes. */
    ActionSheet(Pane host, FrameLayout parent, AppTile anchor, String title, String[] labels, Runnable[] actions) {
        this.host = host;
        this.actions = actions;
        Ui ui = host.ui;
        view = new GlassPanel(host, ui.dp, 28);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ui.px(INSET), 0, ui.px(INSET), ui.px(INSET));
        TextView caption = ui.text(ui.inter, 14, Ui.MIST);
        caption.setText(title);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ui.px(WIDTH - 2 * INSET - 40), -2);
        cl.setMargins(ui.px(20), ui.px(16), 0, ui.px(8));
        list.addView(caption, cl);
        rows = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView t = ui.text(ui.interMedium, 16, Ui.PORCELAIN);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(ui.px(20), 0, ui.px(20), 0);
            GradientDrawable capsule = new GradientDrawable();
            capsule.setCornerRadius(ui.px(ROW / 2));
            t.setBackground(capsule);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, ui.px(ROW));
            rl.bottomMargin = ui.px(i == labels.length - 1 ? 0 : GAP);
            list.addView(t, rl);
            rows[i] = t;
        }
        view.addView(list);
        view.measure(View.MeasureSpec.makeMeasureSpec(ui.px(WIDTH), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float height = view.getMeasuredHeight() / ui.dp;

        // beside the tile: below its name if that clears the bar, else above its icon
        int[] at = new int[2];
        anchor.disc.getLocationInWindow(at);
        float scale = anchor.disc.getScaleX(), ring = ui.px(AppTile.RING_ROOM) * scale, size = anchor.disc.getHeight() * scale;
        float top = (at[1] + size - ring) / ui.dp + 56;
        if (top + height > BAR_TOP) top = Math.max(Ui.SAFE, (at[1] + ring) / ui.dp - 14 - height);
        float centre = (at[0] + anchor.disc.getWidth() * scale / 2) / ui.dp;
        float left = Math.max(Ui.MARGIN, Math.min(Ui.W - Ui.MARGIN - WIDTH, centre - WIDTH / 2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ui.px(WIDTH), -2);
        lp.leftMargin = ui.px(left);
        lp.topMargin = ui.px(top);
        scrim = new View(host);
        scrim.setBackgroundColor(Ui.OBSIDIAN);
        scrim.setAlpha(0f);
        scrim.animate().alpha(0.5f).setDuration(Motion.ms(300)).setInterpolator(Motion.EASE).start();
        parent.addView(scrim, Ui.fill());
        parent.addView(view, lp);
        Motion.rise(view, 0, ui.px(10));
        show();
    }

    private void show() {
        for (int i = 0; i < rows.length; i++) {
            boolean on = i == selected;
            ((GradientDrawable) rows[i].getBackground()).setColor(on ? Ui.PORCELAIN : 0);
            rows[i].setTextColor(on ? Ui.OBSIDIAN : Ui.PORCELAIN);
        }
    }

    void key(int k) {
        if (dismissed) return;
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            int to = selected + (k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            if (to >= 0 && to < rows.length) selected = to;
            else Motion.nudge(rows[selected], 0, host.ui.px(to < 0 ? -6 : 6));
            show();
        } else if (k == KeyEvent.KEYCODE_DPAD_CENTER) {
            final Runnable action = actions[selected];
            Motion.press(rows[selected], () -> { if (!dismissed) host.sheetPicked(action); });
        } else if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) host.closeSheet();
    }

    void dismiss() {
        dismissed = true;
        view.animate().cancel();
        view.animate().alpha(0f).setStartDelay(0).setUpdateListener(null).setDuration(Motion.ms(160)).setInterpolator(Motion.EASE)
                .withEndAction(() -> { ((ViewGroup) view.getParent()).removeView(view); ((ViewGroup) scrim.getParent()).removeView(scrim); }).start();
        scrim.animate().cancel();
        scrim.animate().alpha(0f).setDuration(Motion.ms(160)).setInterpolator(Motion.EASE).start();
    }
}
