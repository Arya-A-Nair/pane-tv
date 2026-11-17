package tv.nova;

import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Every app, six to a row, two rows on show. The apps that are on Home come first, marked
 * with a Glacier dot; Menu adds the focused app to Home or takes it off, and the page says so.
 */
final class AppsPage extends Page {
    private static final int COLUMNS = 6;
    private static final float ROW_HEIGHT = 152, DISC = 80;

    private final FrameLayout grid;
    private final TextView context;
    private final List<AppTile> tiles = new ArrayList<>();
    private int selected;

    AppsPage(Nova host) {
        super(host);
        context = header("Apps");
        grid = new FrameLayout(host);   // rows outside the two on show are faded out, not clipped
        grid.setClipChildren(false);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-1, ui.px(2000));
        gl.topMargin = ui.px(Ui.CONTENT_TOP + 4);
        view.addView(grid, gl);
        fill();
    }

    /** Rebuild the grid: the apps on Home first, in Home order, then the rest by name. */
    void fill() {
        grid.removeAllViews();
        tiles.clear();
        float cell = (Ui.W - 2 * Ui.MARGIN) / COLUMNS;
        List<Apps.App> apps = host.apps.ordered();
        for (int i = 0; i < apps.size(); i++) {
            AppTile t = new AppTile(ui, apps.get(i), DISC, cell);
            FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(ui.px(cell), -2);
            l.leftMargin = ui.px(Ui.MARGIN + (i % COLUMNS) * cell);
            l.topMargin = ui.px((i / COLUMNS) * ROW_HEIGHT - AppTile.RING_ROOM);
            grid.addView(t.view, l);
            tiles.add(t);
        }
        selected = Math.min(selected, Math.max(0, tiles.size() - 1));
        markOnHome();
    }

    private void markOnHome() {
        for (AppTile t : tiles) t.markOnHome(host.apps.onHome(t.app));
    }

    @Override boolean selectable() { return !tiles.isEmpty(); }

    @Override void focus(boolean holdsFocus, boolean animate) {
        int lastRow = (tiles.size() - 1) / COLUMNS, top = Math.max(0, Math.min(selected / COLUMNS, lastRow - 1));
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).focus(holdsFocus && i == selected, animate);
            int r = i / COLUMNS;
            tiles.get(i).view.animate().alpha(r >= top && r <= top + 1 ? 1f : 0f).setDuration(animate ? Motion.ms(260) : 0).start();
        }
        grid.animate().translationY(-top * ui.px(ROW_HEIGHT)).setDuration(animate ? Motion.ms(420) : 0).setInterpolator(Motion.GLIDE).start();
        if (tiles.isEmpty()) context.setText("");
        else context.setText(!holdsFocus ? tiles.size() + " apps" : host.apps.onHome(tiles.get(selected).app) ? "Menu  ·  Remove from Home" : "Menu  ·  Add to Home");
    }

    @Override boolean key(int k) {
        int n = tiles.size(), column = selected % COLUMNS;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (column > 0) selected--; return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (column < COLUMNS - 1 && selected + 1 < n) selected++; return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (selected >= COLUMNS) selected -= COLUMNS; return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (selected + COLUMNS < n) selected += COLUMNS;
                else if (selected / COLUMNS < (n - 1) / COLUMNS) selected = n - 1;   // a short last row
                else return false;
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: host.launch(tiles.get(selected)); return true;
            case KeyEvent.KEYCODE_MENU:
                host.apps.toggleFavourite(tiles.get(selected).app);
                markOnHome();
                host.homeChanged();
                return true;
            default: return false;
        }
    }
}
