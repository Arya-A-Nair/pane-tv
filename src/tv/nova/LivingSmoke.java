package tv.nova;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The artwork environment, alive. It draws the same full-resolution still, one texel to one
 * pixel, but lets it flow: every point drifts a few pixels along a slow, smooth field, so the
 * smoke never quite holds still. When focus moves on Home, a soft band of light in the new
 * app's colour enters from the side focus moved toward and travels through — brighter where
 * the smoke is dense, so it reads as light scattering rather than a wipe.
 *
 * The flow is computed per vertex of a coarse grid, so each pixel costs one texture read.
 * Drawn on alternate display frames (30 fps). If the GPU cannot hold that, it says so once
 * and the caller falls back to the still.
 */
final class LivingSmoke extends GLSurfaceView implements GLSurfaceView.Renderer, Choreographer.FrameCallback {
    interface Listener {
        /** The first frame is on screen: the still above it can go. */
        void onAlive();
        /** Frames take too long on this device: stop using this view. */
        void onTooSlow();
    }

    private static final String TAG = "LivingSmoke";
    private static final int COLUMNS = 48, ROWS = 27;
    private static final float DRIFT_PX = 9f;            // how far a point strays from its place, at most
    private static final long SWEEP_MS = 1500;
    private static final float SWEEP_STRENGTH = 0.28f;   // most light the band adds, on the densest smoke
    private static final float SLOW_FRAME_MS = 26f;

    private final Listener listener;
    private volatile Bitmap picture;                      // waiting to be uploaded
    private volatile float tintR = 1, tintG = 1, tintB = 1, tintAmount;
    private volatile float sweepFrom, sweepR, sweepG, sweepB;
    private volatile long sweepStart = -SWEEP_MS, zoomStart;
    private volatile boolean zooming;
    private boolean running, alive, judged;
    private boolean oddFrame;

    private int program, texture, uTime, uDrift, uZoom, uTint, uBeam, uBeamColor;
    private FloatBuffer vertices;
    private ShortBuffer indices;
    private int indexCount, width = 1, height = 1;
    private long frames, drawNanos;

    LivingSmoke(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setPreserveEGLContextOnPause(true);
    }

    /** The still to draw. It must stay un-recycled until the next call. */
    void setPicture(Bitmap b) { picture = b; }

    /** Same grade as Room applies to glass: keep brightness, replace 'amount' of the colour with the tint. */
    void setTint(float r, float g, float b, float amount) { tintR = r; tintG = g; tintB = b; tintAmount = amount; }

    /** Send a band of light through. fromRight: it enters at the right edge and travels left. */
    void sweep(boolean fromRight, float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        sweepR = r / max; sweepG = g / max; sweepB = b / max;
        sweepFrom = fromRight ? 1 : -1;
        sweepStart = SystemClock.uptimeMillis();
    }

    /** The screensaver's slow push in. */
    void zoom(boolean on) { zoomStart = SystemClock.uptimeMillis(); zooming = on; }

    void start() {
        if (running) return;
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override public void doFrame(long nanos) {
        if (!running) return;
        oddFrame = !oddFrame;
        if (oddFrame) requestRender();
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ---- GL thread

    private static final String VERTEX =
            "attribute vec2 aPos;\n" +                    // 0..1 across the screen, y down
            "uniform highp float uTime;\n" +
            "uniform vec2 uDrift;\n" +                    // DRIFT_PX as a fraction of the picture
            "uniform float uZoom;\n" +
            "varying highp vec2 vUv;\n" +
            "varying vec2 vPos;\n" +
            "void main() {\n" +
            // two slow, curling waves per axis; periods of half a minute and more
            "  vec2 flow = vec2(\n" +
            "    sin(aPos.y * 5.0 + uTime * 0.19 + 1.7 * sin(aPos.x * 3.7 + uTime * 0.11)) + 0.5 * sin(aPos.x * 9.1 - uTime * 0.16 + aPos.y * 6.3),\n" +
            "    cos(aPos.x * 4.3 - uTime * 0.14 + 1.9 * sin(aPos.y * 4.1 + uTime * 0.09)) + 0.5 * sin(aPos.y * 8.3 + uTime * 0.21 + aPos.x * 5.7));\n" +
            // still at the edges, so nothing is ever pulled in from outside the picture
            "  float hold = smoothstep(0.0, 0.14, aPos.x) * smoothstep(0.0, 0.14, 1.0 - aPos.x) * smoothstep(0.0, 0.18, aPos.y) * smoothstep(0.0, 0.18, 1.0 - aPos.y);\n" +
            "  vUv = (aPos - 0.5) / uZoom + 0.5 + flow * uDrift * hold / 1.5;\n" +
            "  vPos = aPos;\n" +
            "  gl_Position = vec4(aPos.x * 2.0 - 1.0, 1.0 - aPos.y * 2.0, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT =
            "precision mediump float;\n" +
            "uniform sampler2D uPicture;\n" +
            "uniform vec4 uTint;\n" +                     // rgb, amount
            "uniform vec4 uBeam;\n" +                     // centre x, width, strength, lean
            "uniform vec3 uBeamColor;\n" +
            "varying highp vec2 vUv;\n" +
            "varying vec2 vPos;\n" +
            "void main() {\n" +
            "  vec3 c = texture2D(uPicture, vUv).rgb;\n" +
            "  float luma = dot(c, vec3(0.299, 0.587, 0.114));\n" +
            "  c = mix(c, luma * uTint.rgb, uTint.a);\n" +
            "  float d = (vPos.x + (vPos.y - 0.5) * uBeam.w - uBeam.x) / uBeam.y;\n" +
            "  c += uBeamColor * (exp(-d * d) * uBeam.z * (0.03 + luma * 1.5));\n" +   // dense smoke scatters more light
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX));
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT));
        GLES20.glBindAttribLocation(program, 0, "aPos");
        GLES20.glLinkProgram(program);
        uTime = GLES20.glGetUniformLocation(program, "uTime");
        uDrift = GLES20.glGetUniformLocation(program, "uDrift");
        uZoom = GLES20.glGetUniformLocation(program, "uZoom");
        uTint = GLES20.glGetUniformLocation(program, "uTint");
        uBeam = GLES20.glGetUniformLocation(program, "uBeam");
        uBeamColor = GLES20.glGetUniformLocation(program, "uBeamColor");

        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        texture = id[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        if (picture == null) picture = Env.photo;   // the context was lost: upload again

        vertices = ByteBuffer.allocateDirect((COLUMNS + 1) * (ROWS + 1) * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int y = 0; y <= ROWS; y++) for (int x = 0; x <= COLUMNS; x++) vertices.put(x / (float) COLUMNS).put(y / (float) ROWS);
        vertices.position(0);
        indexCount = COLUMNS * ROWS * 6;
        indices = ByteBuffer.allocateDirect(indexCount * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLUMNS; x++) {
            int a = y * (COLUMNS + 1) + x, b = a + 1, c = a + COLUMNS + 1, d = c + 1;
            indices.put((short) a).put((short) c).put((short) b).put((short) b).put((short) c).put((short) d);
        }
        indices.position(0);
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
        return shader;
    }

    @Override public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = w;
        height = h;
        GLES20.glViewport(0, 0, w, h);
    }

    @Override public void onDrawFrame(GL10 unused) {
        long began = System.nanoTime(), now = SystemClock.uptimeMillis();
        Bitmap fresh = picture;
        if (fresh != null) {
            picture = null;
            if (fresh.isRecycled()) return;   // the environment changed again before this one was drawn
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, fresh, 0);
        }
        GLES20.glUseProgram(program);
        GLES20.glUniform1f(uTime, (now % 3_600_000L) / 1000f);
        GLES20.glUniform2f(uDrift, DRIFT_PX / width, DRIFT_PX / height);
        float zoomed = Math.min(1f, (now - zoomStart) / (zooming ? 180_000f : 900f));
        GLES20.glUniform1f(uZoom, 1f + 0.08f * (zooming ? zoomed : zoomStart == 0 ? 0 : (1 - zoomed) * lastZoom));
        if (zooming) lastZoom = zoomed;
        GLES20.glUniform4f(uTint, tintR, tintG, tintB, tintAmount);

        float t = (now - sweepStart) / (float) SWEEP_MS;
        if (t >= 0 && t < 1) {
            float eased = 1 - (1 - t) * (1 - t) * (1 - t);
            float x = sweepFrom > 0 ? 1.2f - 1.05f * eased : -0.2f + 1.05f * eased;
            float in = Math.min(1f, t / 0.15f), out = Math.max(0f, Math.min(1f, (1 - t) / 0.55f));
            GLES20.glUniform4f(uBeam, x, 0.2f, SWEEP_STRENGTH * in * in * (3 - 2 * in) * out * out * (3 - 2 * out), 0.22f * sweepFrom);
            GLES20.glUniform3f(uBeamColor, sweepR, sweepG, sweepB);
        } else GLES20.glUniform4f(uBeam, 0f, 1f, 0f, 0f);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices);

        if (!alive) { alive = true; post(listener::onAlive); }
        if (!judged) {   // time the first few seconds once, with the GPU's work included
            GLES20.glFinish();
            drawNanos += System.nanoTime() - began;
            if (++frames == 120) {
                judged = true;
                float ms = drawNanos / 1e6f / frames;
                Log.i(TAG, String.format("%dx%d, %.1f ms a frame", width, height, ms));
                if (ms > SLOW_FRAME_MS) post(listener::onTooSlow);
            }
        }
    }

    private float lastZoom;   // GL thread: how far the push-in had got, so it can ease back out
}
