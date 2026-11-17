package tv.nova;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * What is left when nobody has touched the remote for a while: the environment as the
 * picture, the time set large at the bottom left, and one line with the date and weather.
 */
final class Screensaver {
    final FrameLayout view;
    private final RollingText clock;
    private final TextView line;

    Screensaver(Ui ui) {
        view = new FrameLayout(ui.context);
        view.setAlpha(0f);
        clock = new RollingText(ui, ui.manrope, 132, Ui.PORCELAIN).shadowed(ui.px(14)).accent(':', Ui.CHAMPAGNE_LIGHT);   // the one warm mark
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM);
        cl.setMargins(ui.px(Ui.MARGIN - 6), 0, 0, ui.px(Ui.BOTTOM + 30));
        view.addView(clock, cl);
        line = ui.text(ui.inter, 16, Ui.PORCELAIN);
        ui.shadow(line);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM);
        ll.setMargins(ui.px(Ui.MARGIN), 0, 0, ui.px(Ui.BOTTOM));
        view.addView(line, ll);
    }

    void show(String weatherSummary) {
        clock.set(Ui.time());
        line.setText(Ui.date() + (weatherSummary.isEmpty() ? "" : "   ·   " + weatherSummary));
    }
}
