package tv.pane;

import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Choose the environment. The pictures themselves are the tiles; the one in use has a Glacier dot before its name. */
final class BackgroundsPage extends Page {
    private static final int COLUMNS = 5;
    private static final float GAP = 20, CORNER = 12;

    private final TextView context;
    private final List<Tile> tiles = new ArrayList<>();
    private int selected;

    BackgroundsPage(Pane host) {
        super(host);
        context = header("Backgrounds");
        float w = (Ui.W - 2 * Ui.MARGIN - (COLUMNS - 1) * GAP) / COLUMNS, pitch = w * 9 / 16f + 62;
        for (int i = 0; i < Env.IDS.length; i++) {
            Tile t = new Tile(i, w);
            ui.place(view, t.view, Ui.MARGIN + (i % COLUMNS) * (w + GAP), Ui.CONTENT_TOP + 12 + (i / COLUMNS) * pitch);
            tiles.add(t);
        }
        for (int r = 0; r * COLUMNS < tiles.size(); r++) {
            View[] inRow = new View[Math.min(COLUMNS, tiles.size() - r * COLUMNS)];
            for (int i = 0; i < inRow.length; i++) inRow[i] = tiles.get(r * COLUMNS + i).view;
            row(inRow);
        }
        selected = Math.max(0, Env.index);
    }

    @Override void focus(boolean holdsFocus, boolean animate) {
        for (int i = 0; i < tiles.size(); i++) tiles.get(i).show(holdsFocus && i == selected, i == Env.index);
        context.setText(holdsFocus ? Env.CREDITS[selected] + "  ·  OK to use" : Env.NAMES[Math.max(0, Env.index)]);
    }

    @Override boolean key(int k) {
        int n = tiles.size(), column = selected % COLUMNS;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (column > 0) selected--; else Motion.nudge(tiles.get(selected).view, -ui.px(7), 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (column < COLUMNS - 1 && selected + 1 < n) selected++; else Motion.nudge(tiles.get(selected).view, ui.px(7), 0); return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (selected >= COLUMNS) selected -= COLUMNS; else Motion.nudge(tiles.get(selected).view, 0, -ui.px(7)); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (selected + COLUMNS >= n) return false; selected += COLUMNS; return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: { final Tile t = tiles.get(selected); Motion.press(t.picture, () -> host.useEnvironment(selected, t.picture)); return true; }
            default: return false;
        }
    }

    /** One environment: its picture, a hairline, a focus ring standing off the edge, and its name. */
    private final class Tile {
        final LinearLayout view = new LinearLayout(host);
        private final FrameLayout picture = new FrameLayout(host);
        private final View ring, inUse;
        private final TextView name;

        Tile(int index, float widthDp) {
            view.setOrientation(LinearLayout.VERTICAL);
            view.setClipChildren(false);
            picture.setClipChildren(false);
            ImageView image = new ImageView(host);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setOutlineProvider(new ViewOutlineProvider() {
                public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), ui.px(CORNER)); }
            });
            image.setClipToOutline(true);
            image.setElevation(ui.px(6));
            try { image.setImageBitmap(Env.decode(host, index, ui.px(widthDp), ui.px(widthDp * 9 / 16f))); }
            catch (java.io.IOException ignored) { }   // a missing picture leaves an empty tile
            picture.addView(image, Ui.fill());
            picture.addView(outline(CORNER, Math.max(1, ui.px(0.75f)), 0x33ffffff), Ui.fill());
            ring = outline(CORNER + 5, ui.px(2.5f), Ui.PORCELAIN);
            ring.setAlpha(0f);
            FrameLayout.LayoutParams rl = Ui.fill();
            int off = -ui.px(5);
            rl.setMargins(off, off, off, off);
            picture.addView(ring, rl);
            view.addView(picture, new LinearLayout.LayoutParams(ui.px(widthDp), ui.px(widthDp * 9 / 16f)));

            LinearLayout caption = new LinearLayout(host);
            caption.setGravity(Gravity.CENTER_VERTICAL);
            inUse = ui.dot(Ui.GLACIER);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ui.px(8), ui.px(8));
            dl.rightMargin = ui.px(8);
            caption.addView(inUse, dl);
            name = ui.text(ui.interMedium, 14, Ui.MIST);
            name.setText(Env.NAMES[index]);
            caption.addView(name);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.topMargin = ui.px(12);
            view.addView(caption, cl);
        }

        private View outline(float cornerDp, int strokePx, int color) {
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(ui.px(cornerDp));
            d.setStroke(strokePx, color);
            View v = new View(host);
            v.setBackground(d);
            v.setElevation(ui.px(6));   // same height as the picture, so it draws over it
            return v;
        }

        void show(boolean focused, boolean current) {
            ring.animate().alpha(focused ? 1f : 0f).setDuration(Motion.ms(160)).start();
            picture.animate().scaleX(focused ? 1.07f : 1f).scaleY(focused ? 1.07f : 1f).translationZ(focused ? ui.px(12) : 0)
                    .setDuration(Motion.ms(300)).setInterpolator(Motion.FOCUS).start();
            inUse.setVisibility(current ? View.VISIBLE : View.GONE);
            name.setTextColor(focused || current ? Ui.PORCELAIN : Ui.MIST);
            name.animate().translationY(focused ? ui.px(5) : 0).setDuration(Motion.ms(300)).setInterpolator(Motion.FOCUS).start();
        }
    }
}
