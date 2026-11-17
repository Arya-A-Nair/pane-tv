package tv.pane;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After boot or an update: start the Home watcher and show Pane. */
public class Boot extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        ctx.startService(new Intent(ctx, Watcher.class));
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) Watcher.openPane(ctx);
    }
}
