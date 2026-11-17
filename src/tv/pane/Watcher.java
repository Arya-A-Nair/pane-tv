package tv.pane;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Fire OS will not let another app be Home, so this watches the system log and
 * brings Pane forward whenever Amazon's home activity is started.
 * After a Home press Android holds background activity starts for ~5 s (app-switch
 * protection); an Obsidian cover with the wordmark hides Amazon's screen meanwhile.
 * Runs in its own process so the redirect survives the UI being reclaimed.
 */
public class Watcher extends Service {
    static final String HOME_START = "cat=[android.intent.category.HOME]";
    static final String AMAZON_HOME = "com.amazon.tv.launcher/";
    static final String PANE_SHOWN = "Displayed tv.pane/";
    // Android's APP_SWITCH_DELAY_TIME is 5000 ms; margin lets Pane draw. Tune if Amazon peeks through.
    static final long COVER_MS = 5600;
    private static final String TAG = "PaneWatcher";
    private static final int NOTIFICATION_ID = 1;
    private static final long RESTART_DELAY_MS = 1000;   // pause before re-reading the log after logcat dies
    private static final long COVER_HIDE_DELAY_MS = 250;   // let Pane's first frame land before the cover fades

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hideCover;
    private View cover;
    private Thread thread;
    private volatile Process logcat;

    static void openPane(Context ctx) {
        ctx.startActivity(new Intent(Intent.ACTION_MAIN).setClassName(ctx, "tv.pane.Pane")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, new Notification.Builder(this).setContentTitle("Pane")
                .setSmallIcon(android.R.drawable.ic_menu_revert).build());
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this::loop, "pane-watch");
            thread.start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[]{"logcat", "-T", "1", "-v", "brief", "ActivityManager:I", "*:S"});
                logcat = p;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains(HOME_START) && line.contains(AMAZON_HOME)) {
                            main.post(this::showCover);
                            openPane(this);
                        } else if (line.contains(PANE_SHOWN)) {
                            main.postDelayed(hide, COVER_HIDE_DELAY_MS);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "log reader stopped, restarting", e);
            } finally {
                if (p != null) p.destroy();
            }
            try { Thread.sleep(RESTART_DELAY_MS); } catch (InterruptedException e) { return; }
        }
    }

    private void showCover() {
        main.removeCallbacks(hide);
        main.postDelayed(hide, COVER_MS);
        if (cover != null) return;
        FrameLayout v = new FrameLayout(this);
        v.setBackgroundColor(Ui.OBSIDIAN);
        // swallow remote presses so they don't land on Amazon's home underneath
        v.setFocusable(true);
        v.setOnKeyListener((view, code, e) -> true);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(v, lp);
            v.requestFocus();
            cover = v;
        } catch (RuntimeException noPermission) { }   // no overlay permission: the redirect still works, just uncovered
    }

    private void hideCover() {
        main.removeCallbacks(hide);
        if (cover == null) return;
        final View v = cover;
        cover = null;
        v.animate().alpha(0f).setDuration(260).withEndAction(() -> removeCover(v)).start();
    }

    private void removeCover(View v) {
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); }
        catch (RuntimeException gone) { }   // already detached, for instance because the service is stopping
    }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (thread != null) thread.interrupt();
        Process p = logcat;
        if (p != null) p.destroy();   // unblocks the reader
        if (cover != null) { removeCover(cover); cover = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
