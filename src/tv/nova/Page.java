package tv.nova;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** One of the five pages. A page owns its view, shows where focus is, and answers the remote while it holds focus. */
abstract class Page {
    final Nova host;
    final Ui ui;
    final FrameLayout view;

    Page(Nova host) {
        this.host = host;
        ui = host.ui;
        view = new FrameLayout(host);
        view.setClipChildren(false);
    }

    /** Show which item is focused. holdsFocus is false while focus is on the bottom bar. */
    abstract void focus(boolean holdsFocus, boolean animate);

    /** A remote key while this page holds focus. Return false if it means nothing here; Down then moves to the bar. */
    boolean key(int keyCode) { return false; }

    /** False for a page that is only read: focus then stays on the bar. */
    boolean selectable() { return true; }

    /** Something this page shows may have changed (the minute, the weather, the installed apps). */
    void refresh() {}

    /** Page title on the left, one line of context on the right. Returns the context line. */
    protected TextView header(String title) {
        TextView t = ui.text(ui.manrope, 40, Ui.PORCELAIN);
        t.setLetterSpacing(-0.02f);
        t.setText(title);
        ui.place(view, t, Ui.MARGIN - 2, Ui.TOP - 9);
        TextView context = ui.text(ui.inter, 15, Ui.MIST);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-2, -2, Gravity.END);
        l.setMargins(0, ui.px(Ui.TOP + 14), ui.px(Ui.MARGIN), 0);
        view.addView(context, l);
        return context;
    }
}
