package tv.pane;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Calendar;

/**
 * The home screen. This activity holds the pieces together — the room, five pages, the
 * bottom bar, the screensaver — and owns what crosses them: where focus is, the remote,
 * launching an app, and going idle. The design language is written down in DESIGN.md.
 */
public class Pane extends Activity {
    /** The one SharedPreferences file: favourites, history, chosen environment, settings, cached weather. */
    static final String PREFS = "pane";
    static final String[] PAGE_NAMES = {"Home", "Apps", "Today", "Backgrounds", "Settings"};
    // Indices into PAGE_NAMES and the pages array. TODAY and BACKGROUNDS are only reached through the bar.
    static final int HOME = 0, APPS = 1, TODAY = 2, BACKGROUNDS = 3, SETTINGS = 4;
    /** The list of hidden apps: a sixth page reached from Settings, with no place of its own in the bar (Settings stays lit). */
    static final int HIDDEN = 5;

    Ui ui;
    Apps apps;
    Weather weather;
    SharedPreferences prefs;

    private Room room;
    private FrameLayout root, stage, launchLayer;
    private Page[] pages;
    private HomePage home;
    private AppsPage appsPage, hiddenPage;
    private ActionSheet sheet;
    private BottomBar bar;
    private Screensaver screensaver;
    private final Handler handler = new Handler();
    private BroadcastReceiver packageReceiver;

    private int page;             // the page on show
    private boolean onBar;        // focus is on the bottom bar rather than in the page
    private int barItem;
    private boolean idle, launching, entered;
    private long lastInput = SystemClock.uptimeMillis();
    private int minuteShown = -1;
    private ImageView launchedIcon;   // the icon at the centre of the screen while its app is open, to fly home on return
    private Apps.App launchedApp;
    private static final long MINUTE_MS = 60_000L;
    private static boolean dryRun;   // test hook: launches play their animation but open nothing

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.OBSIDIAN));
        ui = new Ui(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Motion.reduced = prefs.getBoolean("reducedMotion", false);
        Env.load(this, Env.indexOf(prefs.getString("env", Env.IDS[0])), getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        apps = new Apps(this);
        weather = new Weather(this, w -> refreshPages());

        root = new FrameLayout(this);
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        room = new Room(root, stage);
        root.addView(stage, Ui.fill());
        home = new HomePage(this);
        appsPage = new AppsPage(this, false);
        hiddenPage = new AppsPage(this, true);
        pages = new Page[]{home, appsPage, new TodayPage(this), new BackgroundsPage(this), new SettingsPage(this), hiddenPage};
        for (Page p : pages) stage.addView(p.view, Ui.fill());
        bar = new BottomBar(ui, PAGE_NAMES);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = ui.px(Ui.BOTTOM);
        stage.addView(bar.view, bl);
        screensaver = new Screensaver(ui);
        root.addView(screensaver.view, Ui.fill());
        launchLayer = new FrameLayout(this);
        root.addView(launchLayer, Ui.fill());
        setContentView(root);

        startService(new Intent(this, Watcher.class));
        watchPackages();
        handleTestExtras(getIntent());
        showFocus(false);
        everySecond.run();
    }

    // ---------------------------------------------------------------- focus and pages

    /** Draw the current state: which page is on show, where focus is, how the room is lit. */
    private void showFocus(boolean animate) {
        for (int i = 0; i < pages.length; i++) {
            pages[i].view.setVisibility(i == page ? View.VISIBLE : View.GONE);
            pages[i].focus(i == page && !onBar, animate);
        }
        bar.show(Math.min(page, SETTINGS), onBar, animate);
        room.relight(page == HOME && !idle ? home.accent() : null, animate);
        room.watch(page == HOME || idle);
    }

    private void show(int target) {
        if (target == page) return;
        closeSheet();
        page = target;
        if (page == APPS) appsPage.fill();   // pick up what was added to or taken off Home
        if (page == HIDDEN) hiddenPage.fill();
        showFocus(true);
        room.dim(page != HOME, Motion.ms(450));
        pages[page].enter();
    }

    /** Go to a page and put focus in it. */
    void open(int target) {
        barItem = Math.min(target, SETTINGS);
        onBar = !pages[target].selectable();
        show(target);
        showFocus(true);
    }

    private void goHome() {
        closeSheet();
        onBar = false;
        barItem = HOME;
        home.focusLastWatched();
        show(HOME);
    }

    /** Focus moved along Home's row: the new app's light will come in from that side. */
    void lightFrom(boolean right) { room.spreadNextFrom(right); }

    /** The favourites changed: Home's row must follow. */
    void homeChanged() { home.fillRow(); }

    /** origin is the card that was chosen, which the new environment spreads from; null to simply switch. */
    void useEnvironment(int index, View origin) {
        prefs.edit().putString("env", Env.IDS[index]).apply();
        room.use(index, origin, () -> showFocus(true));
    }

    /** A small glass sheet of actions beside this tile; labels[i] runs actions[i] (null just closes it). */
    void openSheet(AppTile anchor, String title, String[] labels, Runnable[] actions) {
        closeSheet();
        sheet = new ActionSheet(this, stage, anchor, title, labels, actions);
    }

    void closeSheet() {
        if (sheet == null) return;
        sheet.dismiss();
        sheet = null;
    }

    void sheetPicked(Runnable action) {
        closeSheet();
        if (action != null) action.run();
        showFocus(true);
    }

    private void refreshPages() { for (Page p : pages) p.refresh(); }

    // ---------------------------------------------------------------- the remote

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = SystemClock.uptimeMillis();
        if (idle) { if (e.getAction() == KeyEvent.ACTION_UP) wake(); return true; }
        if (launching) return true;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A) k = KeyEvent.KEYCODE_DPAD_CENTER;

        if (sheet != null) sheet.key(k);
        else if (k == KeyEvent.KEYCODE_BACK) goHome();
        else if (onBar) {
            boolean left = k == KeyEvent.KEYCODE_DPAD_LEFT, right = k == KeyEvent.KEYCODE_DPAD_RIGHT;
            if ((left && barItem == 0) || (right && barItem == PAGE_NAMES.length - 1)) Motion.nudge(bar.view, ui.px(left ? -6 : 6), 0);
            else if (left) barItem--;
            else if (right) barItem++;
            else if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_CENTER) onBar = !pages[barItem].selectable();
            else if (k == KeyEvent.KEYCODE_MENU) { open(SETTINGS); return true; }
            show(barItem);   // pages follow the bar, like tabs
        } else if (!pages[page].key(k)) {
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) { onBar = true; barItem = Math.min(page, SETTINGS); }
            else if (k == KeyEvent.KEYCODE_MENU) { open(SETTINGS); return true; }
        }
        if (!launching) showFocus(true);
        return true;
    }

    // ---------------------------------------------------------------- launching an app

    /** The app's icon travels to the centre and its name appears, while the room goes dark around it; then the app opens. */
    void launch(AppTile tile) {
        final Apps.App app = tile.app;
        if (launching || app == null) return;
        launching = true;
        if (!dryRun) apps.opened(app);
        int[] at = new int[2];
        tile.disc.getLocationInWindow(at);
        float scale = tile.disc.getScaleX(), ring = ui.px(AppTile.RING_ROOM) * scale, size = tile.disc.getWidth() * scale - 2 * ring;

        View veil = new View(this);
        veil.setBackgroundColor(Ui.OBSIDIAN);
        veil.setAlpha(0f);
        launchLayer.addView(veil, Ui.fill());
        ImageView icon = new ImageView(this);
        launchedIcon = icon;
        launchedApp = app;
        icon.setImageBitmap(app.round);
        launchLayer.addView(icon, new FrameLayout.LayoutParams((int) size, (int) size));
        icon.setX(at[0] + ring);
        icon.setY(at[1] + ring);
        TextView name = ui.text(ui.manrope, 30, Ui.PORCELAIN);
        name.setText(app.label);
        name.setAlpha(0f);
        name.setTranslationY(ui.px(8));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL);
        nl.topMargin = ui.px(Ui.H / 2 + 70);
        launchLayer.addView(name, nl);

        long ms = Motion.ms(520);
        float grow = ui.px(112) / size;
        veil.animate().alpha(1f).setDuration(ms).setInterpolator(Motion.EASE).start();
        stage.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(Motion.ms(320)).start();
        name.animate().alpha(1f).translationY(0).setStartDelay(Motion.ms(180)).setDuration(Motion.ms(360)).setInterpolator(Motion.GLIDE).start();
        icon.animate().x(root.getWidth() / 2f - size / 2).y(root.getHeight() / 2f - size / 2 - ui.px(16)).scaleX(grow).scaleY(grow)
                .setDuration(ms).setInterpolator(Motion.GLIDE).start();
        handler.postDelayed(() -> {
            if (dryRun) { handler.postDelayed(this::returnFromApp, 1200); return; }
            try {
                startActivity(app.launch);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } catch (RuntimeException gone) {   // uninstalled or disabled since the list was built
                returnFromApp();
            }
        }, ms + Motion.ms(260));
    }

    private void returnFromApp() {
        final ImageView icon = launching ? launchedIcon : null;
        final Apps.App app = launchedApp;
        if (launching) {   // back from an app: what was just watched comes first
            home.fillRow();
            home.focusLastWatched();
        }
        launching = false;
        launchedIcon = null;
        if (icon == null || Motion.reduced) launchLayer.removeAllViews();
        else {
            // the reverse of the launch: the icon shrinks back into its own place as the room returns
            for (int i = 0; i < launchLayer.getChildCount(); i++) if (launchLayer.getChildAt(i) != icon) launchLayer.getChildAt(i).animate().alpha(0f).setDuration(260).start();
            root.post(() -> {
                AppTile tile = pages[page].tileFor(app);
                if (tile == null) { launchLayer.removeAllViews(); return; }
                int[] at = new int[2];
                tile.disc.getLocationInWindow(at);
                float ring = ui.px(AppTile.RING_ROOM) * tile.disc.getScaleX(), size = tile.disc.getWidth() * tile.disc.getScaleX() - 2 * ring;
                float shrink = size / icon.getWidth();
                icon.animate().x(at[0] + ring + (size - icon.getWidth()) / 2).y(at[1] + ring + (size - icon.getHeight()) / 2).scaleX(shrink).scaleY(shrink)
                        .setDuration(450).setInterpolator(Motion.GLIDE).withEndAction(launchLayer::removeAllViews).start();
            });
        }
        stage.setAlpha(0f);
        stage.setScaleX(1.02f);
        stage.setScaleY(1.02f);
        stage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ms(600)).setInterpolator(Motion.GLIDE).start();
        showFocus(false);
        handler.postDelayed(() -> Room.redrawGlass(stage), Motion.ms(650));
    }

    /** True in a test run where nothing outside this app may be opened. */
    boolean opensNothing() { return dryRun; }

    // ---------------------------------------------------------------- idle

    private void sleep() {
        idle = true;
        screensaver.show(weather.summary());
        showFocus(true);   // the room returns to its own colours
        stage.animate().alpha(0f).setDuration(1400).start();
        room.dim(false, 1400);
        room.drift(true);
        screensaver.view.animate().alpha(1f).setStartDelay(900).setDuration(1600).start();
    }

    private void wake() {
        idle = false;
        screensaver.view.animate().cancel();
        screensaver.view.animate().alpha(0f).setStartDelay(0).setDuration(Motion.ms(300)).start();
        room.drift(false);
        room.dim(page != HOME, Motion.ms(600));
        stage.animate().alpha(1f).setDuration(Motion.ms(600)).start();
        showFocus(true);
        handler.postDelayed(() -> Room.redrawGlass(stage), Motion.ms(950));
    }

    private final Runnable everySecond = new Runnable() {
        public void run() {
            int minute = Calendar.getInstance().get(Calendar.MINUTE);
            if (minute != minuteShown) {
                minuteShown = minute;
                refreshPages();
                if (idle) screensaver.show(weather.summary());
            }
            long quiet = SystemClock.uptimeMillis() - lastInput;
            if (!idle && !launching && hasWindowFocus() && quiet > prefs.getInt("ambientMin", SettingsPage.DEFAULT_SCREENSAVER_MINUTES) * MINUTE_MS) sleep();
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 5);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        lastInput = SystemClock.uptimeMillis();
        room.resume();
        if (idle) wake();
        if (entered) returnFromApp();
        else { entered = true; enter(); }
    }

    @Override protected void onPause() {
        super.onPause();
        room.pause();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        handler.removeCallbacksAndMessages(null);
        weather.stop();
        super.onDestroy();
    }

    /** The Home button (redirected here by Watcher) always lands on Home. */
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        goHome();
        handleTestExtras(i);
        showFocus(false);
    }

    /** First appearance: each block of Home rises into place, one after another. */
    private void enter() {
        View[] home = this.home.entrance();
        for (int i = 0; i <= home.length; i++) {
            final View v = i < home.length ? home[i] : bar.view;
            v.setAlpha(0f);
            v.setTranslationY(ui.px(14));
            v.animate().alpha(1f).translationY(0).setStartDelay(Motion.ms(150 + i * 80)).setDuration(Motion.ms(800)).setInterpolator(Motion.GLIDE)
                    .setUpdateListener(a -> Room.redrawGlass(v))
                    .withEndAction(() -> { v.animate().setStartDelay(0); Room.redrawGlass(v); }).start();
        }
    }

    /** Apps come and go: rebuild the lists, and let a newly installed app join Home by itself. */
    private void watchPackages() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        packageReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String pkg = i.getData() == null ? null : i.getData().getSchemeSpecificPart();
                apps = new Apps(Pane.this);
                if (Intent.ACTION_PACKAGE_ADDED.equals(i.getAction()) && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false) && pkg != null) apps.autoPin(pkg);
                home.fillRow();
                appsPage.fill();
                hiddenPage.fill();
                refreshPages();
                showFocus(false);
            }
        };
        registerReceiver(packageReceiver, f);
    }

    // ---------------------------------------------------------------- test hooks (adb only)

    /**
     * States that cannot be reached with the remote, or must not be while the family's apps are
     * signed in. Only an adb "am start" can set them:
     *   --ez dry true        launches play their animation but open nothing
     *   --ei aqi 40          pretend air reading          --ei now 600   pretend time of day, in minutes, for the daylight arc
     *   --ez noweather true  pretend there is no reading  --es env tide  use an environment
     *   --ei page 2          show a page, focus on the bar; with --ei sel 3 on Home, focus that tile instead
     *   --ez ambient true    start the screensaver now    --ez audit true  run LayoutAudit over every page
     */
    private void handleTestExtras(Intent i) {
        // The activity is exported (it is a launcher), so only development builds honour these;
        // otherwise any app could start Pane with, say, dry=true and stop launches from working.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        if (i == null || i.getExtras() == null) return;
        if (i.hasExtra("dry")) dryRun = i.getBooleanExtra("dry", false);
        if (i.hasExtra("aqi")) Weather.testAqi = i.getIntExtra("aqi", 0);
        if (i.hasExtra("now")) SkyArc.testNow = i.getIntExtra("now", -1);
        if (i.hasExtra("noweather")) Weather.testOffline = i.getBooleanExtra("noweather", false);
        refreshPages();
        if (i.hasExtra("env")) useEnvironment(Env.indexOf(i.getStringExtra("env")), null);
        if (i.hasExtra("page")) {
            barItem = i.getIntExtra("page", HOME);
            onBar = !i.hasExtra("sel");
            if (!onBar) home.select(i.getIntExtra("sel", 0));
            show(barItem);
        }
        if (i.getBooleanExtra("ambient", false)) handler.postDelayed(this::sleep, 600);
        if (i.getBooleanExtra("audit", false)) handler.postDelayed(() -> {
            for (Page p : pages) p.view.setVisibility(View.VISIBLE);
            root.post(() -> { LayoutAudit.run(stage, ui); showFocus(false); });
        }, 2500);
    }
}
