package tv.pane;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** One of the five pages. A page owns its view, shows where focus is, and answers the remote while it holds focus. */
abstract class Page {
    final Pane host;
    final Ui ui;
    final FrameLayout view;
    private final List<View[]> rows = new ArrayList<>();   // what rises into place, row after row, when the page opens

    Page(Pane host) {
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

    /** The tile showing this app, if the page has one: a returning app's icon flies back to it. */
    AppTile tileFor(Apps.App app) { return null; }

    /** Name the next row of the page, top to bottom. */
    protected void row(View... views) { rows.add(views); }

    /** The page has just come on screen: its rows rise into place one after another. */
    void enter() {
        for (int i = 0; i < rows.size(); i++) for (View v : rows.get(i)) Motion.rise(v, i, ui.px(12));
    }

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
        row(t, context);
        return context;
    }
}
