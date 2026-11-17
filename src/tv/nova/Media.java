package tv.nova;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import java.util.List;

/**
 * Now playing, from whichever app holds an active media session (Spotify, YouTube, Prime...).
 * Reading sessions requires this listener to be enabled; setup grants it over adb.
 */
public class Media extends NotificationListenerService {
    static final class Now {
        String title, artist, pkg;
        Bitmap art;
        boolean playing;
        MediaController controller;
    }

    static Now current(Context c) {
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = m.getActiveSessions(new ComponentName(c, Media.class));
            for (MediaController mc : list) {
                MediaMetadata md = mc.getMetadata();
                PlaybackState st = mc.getPlaybackState();
                if (md == null || md.getString(MediaMetadata.METADATA_KEY_TITLE) == null) continue;
                Now n = new Now();
                n.title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                n.artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (n.artist == null) n.artist = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                n.art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (n.art == null) n.art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                n.playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
                n.pkg = mc.getPackageName();
                n.controller = mc;
                return n;
            }
        } catch (SecurityException ignored) { } // listener not enabled
        return null;
    }
}
