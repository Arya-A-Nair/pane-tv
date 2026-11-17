package tv.nova;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Fire OS will not let another app be Home, so this watches the system log and
 * brings NOVA forward whenever Amazon's home activity is started.
 * After a Home press Android holds background activity starts for ~5 s (app-switch
 * protection); an Obsidian cover with the wordmark hides Amazon's screen meanwhile.
 * Runs in its own process so the redirect survives the UI being reclaimed.
 */
public class Watcher extends Service {
    static final String HOME_START = "cat=[android.intent.category.HOME]";
    static final String AMAZON_HOME = "com.amazon.tv.launcher/";
    static final String NOVA_SHOWN = "Displayed tv.nova/";
    // Android's APP_SWITCH_DELAY_TIME is 5000 ms; margin lets NOVA draw. Tune if Amazon peeks through.
    static final long COVER_MS = 5600;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = new Runnable() { public void run() { hideCover(); } };
    private View cover;
    private Thread thread;

    static void openNova(Context ctx) {
        ctx.startActivity(new Intent(Intent.ACTION_MAIN).setClassName(ctx, "tv.nova.Nova")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, new Notification.Builder(this).setContentTitle("Home")
                .setSmallIcon(android.R.drawable.ic_menu_revert).build());
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(new Runnable() { public void run() { loop(); } }, "nova-watch");
            thread.start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (true) {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[]{"logcat", "-T", "1", "-v", "brief", "ActivityManager:I", "*:S"});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains(HOME_START) && line.contains(AMAZON_HOME)) {
                        main.post(new Runnable() { public void run() { showCover(); } });
                        openNova(this);
                    } else if (line.contains(NOVA_SHOWN)) {
                        main.postDelayed(hide, 250);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (p != null) p.destroy();
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { return; } // logcat died: restart
        }
    }

    private void showCover() {
        main.removeCallbacks(hide);
        main.postDelayed(hide, COVER_MS);
        if (cover != null) return;
        FrameLayout v = new FrameLayout(this);
        v.setBackgroundColor(0xff090b10);
        // swallow remote presses so they don't land on Amazon's home underneath
        v.setFocusable(true);
        v.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View view, int c, KeyEvent e) { return true; } });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(v, lp);
            v.requestFocus();
            cover = v;
        } catch (Exception ignored) { } // no overlay permission: redirect still works, uncovered
    }

    private void hideCover() {
        main.removeCallbacks(hide);
        if (cover == null) return;
        final View v = cover;
        cover = null;
        v.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() { public void run() {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) { }
        } }).start();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
