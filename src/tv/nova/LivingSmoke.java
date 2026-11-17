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
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The artwork environment, alive: real smoke, not a moving picture of it. It curls, billows,
 * thins and re-forms across the whole screen, slowly, for as long as you care to watch.
 *
 * Three passes a frame:
 *  1. Density. Fractal noise warped by fractal noise, twice over, all of it evolving in time, so
 *     shapes change rather than slide. Drawn small (smoke is soft) and read back smoothly.
 *  2. Light. The colour of the room is a small field kept from frame to frame. When focus moves
 *     on Home the new app's colour enters at the side focus moved toward and spreads like dye
 *     dropped in oil: its edge is carried by the smoke's own currents, so it billows, sends
 *     tendrils ahead and leaves pockets behind, and it only replaces the old colour where it
 *     has reached. A second move simply starts a new spread over whatever mixture is there.
 *  3. The picture, at full resolution: the environment's own colours and light (a blurred copy
 *     of the still, so each environment keeps its palette and its dark areas for text) shaped
 *     by the density, with faces toward the light picked out and thin gaps left deep, plus the
 *     still's fine grain so it never looks like a low-resolution effect.
 *
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
    private static final int SURFACE_W = 1280, SURFACE_H = 720;   // smoke is soft: drawn at 720p and scaled up by the display, it looks the same and leaves the GPU free for the interface
    private static final int DENSITY_W = 320, DENSITY_H = 180, LIGHT_W = 160, LIGHT_H = 90, NOISE = 256, PALETTE_W = 256, PALETTE_H = 144;
    private static final long SPREAD_MS = 2200;
    private static final float SLOW_FRAME_MS = 26f;

    private final Listener listener;
    private volatile Bitmap picture;                      // waiting to be uploaded
    private final float[] light = {1, 1, 1, 0};           // tint r, g, b and how much of it; guarded by itself
    private volatile float spreadFrom;                    // 1: the colour enters at the right; -1: at the left; 0: it changes everywhere
    private volatile long spreadStart = -SPREAD_MS, zoomStart;
    private volatile boolean zooming, lightAtOnce = true;
    private boolean running, alive, judged;
    private boolean oddFrame;

    private int densityProgram, lightProgram, pictureProgram;
    private int noiseTexture, pictureTexture, paletteTexture, densityTexture, densityBuffer;
    private final int[] lightTexture = new int[2], lightBuffer = new int[2];
    private int lightNow;
    private FloatBuffer quad;
    private int width = 1, height = 1;
    private long frames, drawNanos;
    private float lastZoom;

    LivingSmoke(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        getHolder().setFixedSize(SURFACE_W, SURFACE_H);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setPreserveEGLContextOnPause(true);
    }

    /** The still to draw. It must stay un-recycled until the next call. */
    void setPicture(Bitmap b) { picture = b; }

    /**
     * Change the colour of the light: keep each pixel's brightness, replace 'amount' of its colour with the tint.
     * from says how the change arrives: 1 spreading in from the right edge, -1 from the left, 0 everywhere together.
     */
    void light(float r, float g, float b, float amount, int from, boolean atOnce) {
        synchronized (light) {
            if (!atOnce && light[0] == r && light[1] == g && light[2] == b && light[3] == amount) return;   // nothing new: let any spread under way finish
            light[0] = r; light[1] = g; light[2] = b; light[3] = amount;
        }
        spreadFrom = from;
        lightAtOnce = atOnce;
        if (from != 0) spreadStart = SystemClock.uptimeMillis();
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
            "attribute vec2 aPos;\n" +                    // 0..1, y down on screen
            "uniform float uFlip;\n" +                    // 1 when drawing to the screen, whose y runs the other way from a texture's
            "varying highp vec2 vPos;\n" +
            "void main() { vPos = aPos; gl_Position = vec4(aPos.x * 2.0 - 1.0, (aPos.y * 2.0 - 1.0) * (1.0 - 2.0 * uFlip), 0.0, 1.0); }\n";

    /** Out: r = density, g = density a step toward the light, b and a = the second warp (the currents the dye follows). */
    private static final String DENSITY =
            "precision highp float;\n" +
            "uniform sampler2D uNoise;\n" +
            "uniform float uTime;\n" +
            "varying vec2 vPos;\n" +
            "const mat2 TURN = mat2(1.6, 1.2, -1.2, 1.6);\n" +     // rotate and double the frequency
            // smooth value noise from a texture of random numbers: one read gives four independent values
            "vec4 noise(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f); return texture2D(uNoise, (i + f + 0.5) / 256.0); }\n" +
            // each octave slides on its own, so shapes change rather than just move
            "vec4 coarse(vec2 p, vec2 slide) {\n" +       // three octaves: enough for the warps, which only steer
            "  vec4 v = vec4(0.0); float a = 0.5;\n" +
            "  for (int i = 0; i < 3; i++) { v += a * noise(p); p = TURN * p + vec2(1.7, 9.2) + slide; a *= 0.5; }\n" +
            "  return v * 1.07;\n" +
            "}\n" +
            "vec4 fbm(vec2 p, vec2 slide) {\n" +
            "  vec4 v = vec4(0.0); float a = 0.5;\n" +
            "  for (int i = 0; i < 4; i++) { v += a * noise(p); p = TURN * p + vec2(1.7, 9.2) + slide; a *= 0.5; }\n" +
            "  return v;\n" +
            "}\n" +
            "void main() {\n" +
            "  float t = uTime;\n" +
            "  vec2 p = (vPos - vec2(0.77, 0.26)) * vec2(1.78, 1.0);\n" +
            "  float turn = 0.05 * sin(t * 0.021) + t * 0.0025;\n" +                           // the whole body turns slowly about the light
            "  p = mat2(cos(turn), sin(turn), -sin(turn), cos(turn)) * p * 2.1 + vec2(3.0 - t * 0.016, 7.0 + t * 0.009);\n" +   // and drifts up and across
            "  vec2 q = coarse(p + vec2(0.0, t * 0.034), vec2(t * 0.011, 0.0)).rg;\n" +
            "  vec2 r = coarse(p + 2.4 * q + vec2(1.7, 9.2) + t * 0.022, vec2(0.0, -t * 0.013)).gb;\n" +
            "  vec2 at = p + 2.8 * r;\n" +
            "  float here = fbm(at, vec2(t * 0.008)).r;\n" +
            "  float lit = coarse(at + vec2(0.16, -0.13), vec2(t * 0.008)).r;\n" +                // the same smoke, a step toward the light
            "  gl_FragColor = vec4(here, lit, r);\n" +
            "}\n";

    /**
     * How fully the spreading colour has reached a point: 0 not yet, 1 well inside. The edge is where the
     * straight front would be, pushed forward and back by the smoke's own currents and density.
     */
    private static final String REACHED =
            "float reached(vec4 smoke, vec2 pos, float front, float side) {\n" +
            "  float distance = side > 0.0 ? 1.0 - pos.x : pos.x;\n" +
            "  float carried = (smoke.b - 0.47) * 0.85 + (smoke.a - 0.47) * 0.35 + (smoke.r - 0.47) * 0.5 + (pos.y - 0.5) * 0.12;\n" +
            "  return smoothstep(0.0, 0.13, front - distance + carried);\n" +
            "}\n";

    /** One step of the light field: a texel takes the target colour once the spreading colour has reached it. */
    private static final String LIGHT =
            "precision mediump float;\n" +
            "uniform sampler2D uLight, uDensity;\n" +
            "uniform vec4 uTarget;\n" +
            "uniform vec3 uSpread;\n" +                   // how far the colour has got (0..1 and beyond), which side it came from (0 = everywhere), rate
            "varying highp vec2 vPos;\n" +
            REACHED +
            "void main() {\n" +
            "  float on = uSpread.y == 0.0 ? 1.0 : reached(texture2D(uDensity, vPos), vPos, uSpread.x, uSpread.y);\n" +
            "  gl_FragColor = mix(texture2D(uLight, vPos), uTarget, uSpread.z * on);\n" +
            "}\n";

    private static final String PICTURE =
            "precision mediump float;\n" +
            "uniform sampler2D uPicture, uPalette, uDensity, uLight;\n" +
            "uniform float uZoom;\n" +
            "uniform vec3 uSpread;\n" +
            "varying highp vec2 vPos;\n" +
            REACHED +
            "void main() {\n" +
            "  highp vec2 uv = (vPos - 0.5) / uZoom + 0.5;\n" +
            "  vec4 smoke = texture2D(uDensity, uv);\n" +
            "  float density = smoothstep(0.30, 0.74, smoke.r);\n" +
            "  float facing = clamp((smoke.r - smoke.g) * 5.0, -0.6, 0.9);\n" +          // density falls away toward the light: this face catches it
            "  vec3 palette = texture2D(uPalette, uv).rgb;\n" +
            "  vec3 c = palette * (0.22 + 1.75 * density + 0.7 * facing * density)\n" +   // deep in the gaps, bright on the lit faces
            "         + (texture2D(uPicture, uv).rgb - palette) * 0.16;\n" +              // the still's grain and finest detail
            "  float luma = dot(c, vec3(0.299, 0.587, 0.114));\n" +
            "  vec4 tint = texture2D(uLight, vPos);\n" +
            "  c = mix(c, luma * tint.rgb * 4.0, tint.a * (0.82 + 0.3 * density));\n" +   // dense smoke takes the colour a touch more strongly
            "  if (uSpread.y != 0.0) {\n" +
            "    float on = reached(smoke, vPos, uSpread.x, uSpread.y);\n" +
            "    c += tint.rgb * (on * (1.0 - on) * (0.18 + 0.5 * luma));\n" +            // fresh dye is most vivid just inside its edge
            "  }\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        densityProgram = link(DENSITY);
        lightProgram = link(LIGHT);
        pictureProgram = link(PICTURE);

        int[] id = new int[4];
        GLES20.glGenTextures(4, id, 0);
        noiseTexture = id[0];
        pictureTexture = id[1];
        paletteTexture = id[2];
        densityTexture = id[3];
        texture(noiseTexture, GLES20.GL_REPEAT);
        ByteBuffer random = ByteBuffer.allocateDirect(NOISE * NOISE * 4);
        byte[] bytes = new byte[NOISE * NOISE * 4];
        new java.util.Random(11).nextBytes(bytes);
        random.put(bytes).position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, NOISE, NOISE, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, random);
        texture(pictureTexture, GLES20.GL_CLAMP_TO_EDGE);
        texture(paletteTexture, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glGenTextures(2, lightTexture, 0);
        int[] buffers = new int[3];
        GLES20.glGenFramebuffers(3, buffers, 0);
        densityBuffer = target(buffers[0], densityTexture, DENSITY_W, DENSITY_H);
        for (int i = 0; i < 2; i++) lightBuffer[i] = target(buffers[1 + i], lightTexture[i], LIGHT_W, LIGHT_H);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        lightAtOnce = true;
        if (picture == null) picture = Env.photo;   // the context was lost: upload again

        quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(new float[]{0, 0, 1, 0, 0, 1, 1, 1}).position(0);
    }

    private static void texture(int id, int wrap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
    }

    /** A texture to draw into. */
    private static int target(int buffer, int texture, int w, int h) {
        texture(texture, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, buffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
        return buffer;
    }

    private static int link(String fragment) {
        int program = GLES20.glCreateProgram();
        for (int pass = 0; pass < 2; pass++) {
            int shader = GLES20.glCreateShader(pass == 0 ? GLES20.GL_VERTEX_SHADER : GLES20.GL_FRAGMENT_SHADER);
            GLES20.glShaderSource(shader, pass == 0 ? VERTEX : fragment);
            GLES20.glCompileShader(shader);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
            GLES20.glAttachShader(program, shader);
        }
        GLES20.glBindAttribLocation(program, 0, "aPos");
        GLES20.glLinkProgram(program);
        return program;
    }

    /** Make 'program' current, drawing into 'buffer' (0 = the screen) with these textures on units 0, 1, 2… under these names. */
    private void begin(int program, int buffer, int w, int h, Object... textures) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, buffer);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glUseProgram(program);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFlip"), buffer == 0 ? 1f : 0f);
        for (int i = 0; i < textures.length; i += 2) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i / 2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, (Integer) textures[i + 1]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, (String) textures[i]), i / 2);
        }
    }

    private void draw() {
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    @Override public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = w;
        height = h;
    }

    @Override public void onDrawFrame(GL10 unused) {
        long began = System.nanoTime(), now = SystemClock.uptimeMillis();
        Bitmap fresh = picture;
        if (fresh != null) {
            picture = null;
            if (fresh.isRecycled()) return;   // the environment changed again before this one was drawn
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pictureTexture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, fresh, 0);
            // the palette: the still with its shapes blurred away, leaving where its colours and its light are
            Bitmap small = Bitmap.createScaledBitmap(fresh, PALETTE_W, PALETTE_H, true), palette = Env.blur(small, 9);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, paletteTexture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, palette, 0);
            small.recycle();
            palette.recycle();
        }
        float seconds = (now % 7_200_000L) / 1000f;

        begin(densityProgram, densityBuffer, DENSITY_W, DENSITY_H, "uNoise", noiseTexture);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(densityProgram, "uTime"), seconds);
        draw();

        // how far a spreading colour has got: away quickly, then a long glide, ending well past the far edge
        float t = (now - spreadStart) / (float) SPREAD_MS, side = spreadFrom;
        boolean spreading = side != 0 && t >= 0 && t < 1;
        float front = spreading ? -0.75f + 2.6f * (1 - (float) Math.pow(1 - t, 1.7)) : 0f;

        int next = 1 - lightNow;
        begin(lightProgram, lightBuffer[next], LIGHT_W, LIGHT_H, "uLight", lightTexture[lightNow], "uDensity", densityTexture);
        synchronized (light) { GLES20.glUniform4f(GLES20.glGetUniformLocation(lightProgram, "uTarget"), light[0] / 4, light[1] / 4, light[2] / 4, light[3]); }
        GLES20.glUniform3f(GLES20.glGetUniformLocation(lightProgram, "uSpread"), front, spreading ? side : 0f, lightAtOnce ? 1f : spreading ? 0.3f : 0.12f);
        lightAtOnce = false;
        draw();
        lightNow = next;

        begin(pictureProgram, 0, width, height, "uPicture", pictureTexture, "uPalette", paletteTexture, "uDensity", densityTexture, "uLight", lightTexture[lightNow]);
        float zoomed = Math.min(1f, (now - zoomStart) / (zooming ? 180_000f : 900f));
        if (zooming) lastZoom = zoomed;
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pictureProgram, "uZoom"), 1f + 0.08f * (zooming ? zoomed : zoomStart == 0 ? 0 : (1 - zoomed) * lastZoom));
        GLES20.glUniform3f(GLES20.glGetUniformLocation(pictureProgram, "uSpread"), front, spreading ? side : 0f, 0f);
        draw();

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
}
