package tv.pane;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * The navigation: a glass bar naming the pages. One pill sits behind the page on show; it is
 * solid Porcelain while the bar holds focus and faint otherwise, and it glides from name to
 * name, changing width as it goes.
 */
final class BottomBar {
    final GlassPanel view;
    private final List<TextView> items = new ArrayList<>();
    private final View pill;
    private final int inset;
    private ValueAnimator gliding;
    private boolean placed;

    BottomBar(Ui ui, String[] names) {
        view = new GlassPanel(ui.context, ui.dp, 22);
        inset = ui.px(4);
        FrameLayout layers = new FrameLayout(ui.context);
        layers.setPadding(inset, inset, inset, inset);
        GradientDrawable capsule = new GradientDrawable();
        capsule.setCornerRadius(ui.px(18));
        capsule.setColor(Ui.PORCELAIN);
        pill = new View(ui.context);
        pill.setBackground(capsule);
        layers.addView(pill, new FrameLayout.LayoutParams(0, ui.px(36)));
        LinearLayout row = new LinearLayout(ui.context);
        for (String name : names) {
            TextView item = ui.text(ui.interMedium, 15, Ui.PORCELAIN);
            item.setGravity(Gravity.CENTER);
            item.setPadding(ui.px(20), 0, ui.px(20), 0);
            item.setText(name);
            row.addView(item, new LinearLayout.LayoutParams(-2, ui.px(36)));
            items.add(item);
        }
        layers.addView(row);
        view.addView(layers);
    }

    /** current is the page on show; focused says whether the bar itself holds focus. */
    void show(final int current, final boolean focused, final boolean animate) {
        for (int i = 0; i < items.size(); i++) items.get(i).setTextColor(i != current ? Ui.MIST : focused ? Ui.OBSIDIAN : Ui.PORCELAIN);
        final TextView target = items.get(current);
        if (target.getWidth() == 0) { view.post(() -> show(current, focused, false)); return; }   // not laid out yet
        boolean glide = animate && placed && !Motion.reduced;
        placed = true;
        pill.animate().alpha(focused ? 1f : 0.15f).setDuration(glide ? 160 : 0).start();
        final float fromX = pill.getTranslationX(), toX = target.getLeft();
        final int fromW = pill.getLayoutParams().width, toW = target.getWidth();
        if (gliding != null) gliding.cancel();
        if (!glide || (fromX == toX && fromW == toW)) { place(toX, toW); return; }
        gliding = ValueAnimator.ofFloat(0f, 1f).setDuration(300);
        gliding.setInterpolator(Motion.GLIDE);
        gliding.addUpdateListener(a -> { float t = (Float) a.getAnimatedValue(); place(fromX + (toX - fromX) * t, Math.round(fromW + (toW - fromW) * t)); });
        gliding.start();
    }

    private void place(float x, int width) {
        pill.setTranslationX(x);
        pill.getLayoutParams().width = width;
        pill.requestLayout();
    }
}
