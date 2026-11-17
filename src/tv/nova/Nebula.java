package tv.nova;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The sky behind the planet: volumetric space smoke from a fragment shader (domain-warped
 * fractal noise), lit warm from the side the real sun is on. Rendered small and upscaled by
 * the display hardware, at 30 fps; steps its resolution down once if the GPU can't keep up.
 */
final class Nebula extends GLSurfaceView implements GLSurfaceView.Renderer, Choreographer.FrameCallback {
    private int rw = 480, rh = 270;
    private boolean degraded, running;
    private volatile float sunX = 0.1f, sunY = 0.7f, dimTarget = 1f;
    private float dim = 0f;
    private int prog, uRes, uTime, uSun, uDim, frame;
    private FloatBuffer quad;
    private final long start = SystemClock.elapsedRealtime();
    private long statT0, drawNs, lastFrame;
    private int statN;

    Nebula(Context c) {
        super(c);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        getHolder().setFixedSize(rw, rh);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setPreserveEGLContextOnPause(true);
    }

    /** Sun position in 0..1 screen space, y measured from the top. */
    void setSun(float x, float yFromTop) { sunX = x; sunY = 1 - yFromTop; }
    void setDim(float d) { dimTarget = d; }

    void start() { if (!running) { running = true; onResume(); Choreographer.getInstance().postFrameCallback(this); } }
    void stop() { running = false; Choreographer.getInstance().removeFrameCallback(this); onPause(); }

    @Override public void doFrame(long n) {
        if (!running) return;
        if ((frame++ & 1) == 0) requestRender();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
        prog = link(VERT, FRAG);
        uRes = GLES20.glGetUniformLocation(prog, "uRes");
        uTime = GLES20.glGetUniformLocation(prog, "uTime");
        uSun = GLES20.glGetUniformLocation(prog, "uSun");
        uDim = GLES20.glGetUniformLocation(prog, "uDim");
        quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
    }

    @Override public void onSurfaceChanged(GL10 gl, int w, int h) { GLES20.glViewport(0, 0, w, h); rw = w; rh = h; }

    @Override public void onDrawFrame(GL10 gl) {
        long now = SystemClock.elapsedRealtime();
        float dt = lastFrame == 0 ? 0.033f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        dim += (dimTarget - dim) * (1 - (float) Math.exp(-dt * 1.2));
        long t0 = System.nanoTime();
        GLES20.glUseProgram(prog);
        GLES20.glUniform2f(uRes, rw, rh);
        GLES20.glUniform1f(uTime, (float) (((now - start) / 1000.0) % 3600.0));
        GLES20.glUniform2f(uSun, sunX, sunY);
        GLES20.glUniform1f(uDim, dim);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFinish();
        drawNs += System.nanoTime() - t0;
        statN++;
        if (now - statT0 > 5000) {
            float ms = drawNs / 1e6f / Math.max(1, statN);
            Log.i("NOVA", String.format("nebula %dx%d %.1f ms/frame", rw, rh, ms));
            if (statT0 != 0 && ms > 24 && !degraded) {
                degraded = true;
                post(new Runnable() { public void run() { getHolder().setFixedSize(384, 216); } });
            }
            statT0 = now; drawNs = 0; statN = 0;
        }
    }

    private static int link(String vs, String fs) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs));
        GLES20.glBindAttribLocation(p, 0, "aPos");
        GLES20.glLinkProgram(p);
        return p;
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) Log.e("NOVA", "shader: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    static final String VERT = "attribute vec2 aPos; void main(){ gl_Position = vec4(aPos,0.,1.); }";

    static final String FRAG =
        "precision highp float;\n" +
        "uniform vec2 uRes; uniform float uTime; uniform vec2 uSun; uniform float uDim;\n" +
        "float hash(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }\n" +
        "float noise(vec2 p){ vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.-2.*f);\n" +
        "  return mix(mix(hash(i),hash(i+vec2(1,0)),u.x), mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x), u.y); }\n" +
        "const mat2 ROT = mat2(1.6,1.2,-1.2,1.6);\n" +
        "float fbm(vec2 p){ float v=0., a=.5; for(int i=0;i<4;i++){ v+=a*noise(p); p=ROT*p+vec2(1.7,9.2); a*=.5; } return v; }\n" +
        "void main(){\n" +
        "  vec2 uv = gl_FragCoord.xy/uRes;\n" +
        "  float asp = uRes.x/uRes.y;\n" +
        "  vec2 p = uv*vec2(asp,1.)*1.35;\n" +
        "  float t = uTime;\n" +
        // two levels of warping: slow, folded, smoke-like
        "  vec2 q = vec2(fbm(p + vec2(0., t*.016)), fbm(p + vec2(5.2,1.3) - t*.013));\n" +
        "  vec2 r = vec2(fbm(p + 2.4*q + vec2(1.7,9.2) + t*.010), fbm(p + 2.4*q + vec2(8.3,2.8) - t*.008));\n" +
        "  float f = fbm(p + 2.8*r);\n" +
        "  float dens = smoothstep(.25,.95,f);\n" +
        // cold body of the cloud
        "  vec3 col = vec3(.020,.024,.036);\n" +
        "  col = mix(col, vec3(.065,.085,.135), dens*dens*1.6);\n" +
        "  col = mix(col, vec3(.120,.145,.200), clamp(dot(r,r)*.55,0.,1.)*dens*.75);\n" +
        // light from the sun scattering through it: warm near the sun, cooling with distance
        "  vec2 sd = (uv-uSun)*vec2(asp,1.);\n" +
        "  float d2 = dot(sd,sd);\n" +
        "  float scatter = exp(-d2*1.05);\n" +
        "  vec3 warm = mix(vec3(.85,.42,.24), vec3(1.,.78,.55), exp(-d2*9.));\n" +
        "  col += warm * scatter * (.05 + .75*dens*dens) * (.7 + .6*q.x);\n" +
        "  col += vec3(1.,.82,.62) * exp(-d2*55.) * .22;\n" +
        "  vec2 cd = (uv-vec2(.92,.95))*vec2(asp,1.);\n" +
        "  col += vec3(.40,.52,.80) * exp(-dot(cd,cd)*1.4) * dens * .16;\n" +
        // the dark lanes between clouds stay deep
        "  col *= mix(.55, 1., smoothstep(.1,.6,f));\n" +
        "  col *= uDim;\n" +
        "  col += (hash(gl_FragCoord.xy + fract(t*13.))-.5)/255.;\n" + // dither: no banding on near-black
        "  gl_FragColor = vec4(col,1.);\n" +
        "}\n";
}
