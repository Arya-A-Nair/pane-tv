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
    static final int HORIZON = 0, AURORA = 1, EARTH = 2, SMOKE = 3;
    private volatile float sunX = 0.1f, sunY = 0.7f, dimTarget = 1f;
    private volatile int sceneTarget = HORIZON;
    private float dim = 0f, mix = 1f;
    private int scene = HORIZON, prev = HORIZON;
    private int prog, uRes, uTime, uSun, uDim, uScene, uPrev, uMix, frame;
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
    void setScene(int sc) { sceneTarget = sc; }

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
        uScene = GLES20.glGetUniformLocation(prog, "uScene");
        uPrev = GLES20.glGetUniformLocation(prog, "uPrev");
        uMix = GLES20.glGetUniformLocation(prog, "uMix");
        quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
    }

    @Override public void onSurfaceChanged(GL10 gl, int w, int h) { GLES20.glViewport(0, 0, w, h); rw = w; rh = h; }

    @Override public void onDrawFrame(GL10 gl) {
        long now = SystemClock.elapsedRealtime();
        float dt = lastFrame == 0 ? 0.033f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        dim += (dimTarget - dim) * (1 - (float) Math.exp(-dt * 1.2));
        if (sceneTarget != scene) { prev = scene; scene = sceneTarget; mix = 0f; }
        if (mix < 1f) mix = Math.min(1f, mix + dt / 1.6f);
        long t0 = System.nanoTime();
        GLES20.glUseProgram(prog);
        GLES20.glUniform2f(uRes, rw, rh);
        GLES20.glUniform1f(uTime, (float) (((now - start) / 1000.0) % 3600.0));
        GLES20.glUniform2f(uSun, sunX, sunY);
        GLES20.glUniform1f(uDim, dim);
        GLES20.glUniform1f(uScene, scene);
        GLES20.glUniform1f(uPrev, prev);
        GLES20.glUniform1f(uMix, mix * mix * (3 - 2 * mix));
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
        "uniform vec2 uRes; uniform float uTime; uniform vec2 uSun; uniform float uDim, uScene, uPrev, uMix;\n" +
        "float hash(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }\n" +
        "float noise(vec2 p){ vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.-2.*f);\n" +
        "  return mix(mix(hash(i),hash(i+vec2(1,0)),u.x), mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x), u.y); }\n" +
        "const mat2 ROT = mat2(1.6,1.2,-1.2,1.6);\n" +
        "float fbm(vec2 p){ float v=0., a=.5; for(int i=0;i<4;i++){ v+=a*noise(p); p=ROT*p+vec2(1.7,9.2); a*=.5; } return v; }\n" +
        "float stars(vec2 uv, float asp){ vec2 g = uv*vec2(asp,1.)*180.; vec2 id=floor(g); float h=hash(id);\n" +
        "  vec2 f=fract(g)-.5; float s = step(.985,h)*smoothstep(.12,.0,length(f)); return s*(.4+.6*hash(id+3.)); }\n" +
        // 0: an eclipse-like horizon: a dark disc with a burning rim, over slow dunes
        "vec3 horizon(vec2 uv, float asp, float t){\n" +
        "  vec2 c = vec2(.765,.575); vec2 d = (uv-c)*vec2(asp,1.); float r = length(d), R = .305;\n" +
        "  float ang = atan(d.y,d.x);\n" +
        "  vec3 col = mix(vec3(.030,.034,.050), vec3(.075,.060,.070), smoothstep(.0,.9,uv.y)*.6);\n" +
        "  col += stars(uv,asp)*.5;\n" +
        "  float lightDir = .55 + .45*cos(ang-2.2);\n" +   // brighter toward the upper left
        "  float flick = .85 + .15*fbm(vec2(ang*3., t*.15));\n" +
        "  float rim = exp(-pow((r-R)/.010,2.)) * lightDir * flick;\n" +
        "  float corona = exp(-max(r-R,0.)*13.) * step(R-.002,r) * (.35+.65*lightDir) * flick;\n" +
        "  float haze = exp(-max(r-R,0.)*3.2) * step(R,r) * .35;\n" +
        "  col += vec3(1.,.55,.22)*corona*.55 + vec3(.9,.42,.25)*haze*.35 + vec3(1.,.86,.66)*rim*1.25;\n" +
        "  col = mix(col, vec3(.012,.013,.018), smoothstep(R+.002,R-.012,r));\n" +   // the disc itself
        // three dune ridges, nearest last
        "  for(int i=0;i<3;i++){ float fi=float(i);\n" +
        "    float base = .40 - fi*.085;\n" +
        "    float y = base + .10*fbm(vec2(uv.x*(1.3+fi*.6)+fi*7.+t*.004*(fi+1.), fi)) - .03*fi;\n" +
        "    float e = smoothstep(y+.002,y-.002,uv.y);\n" +
        "    float crest = exp(-pow((uv.y-y)/.006,2.)) * (.25+.75*exp(-pow((uv.x-.72)*2.2,2.)));\n" +
        "    vec3 sand = vec3(.030,.026,.030)*(1.-fi*.15) + vec3(.11,.060,.045)*smoothstep(y-.16,y,uv.y)*(1.-fi*.25)*(.5+.5*exp(-pow((uv.x-.7)*1.6,2.)));\n" +
        "    col = mix(col, sand, e);\n" +
        "    col += vec3(.95,.55,.3)*crest*(.22-fi*.05); }\n" +
        "  return col; }\n" +
        // 1: aurora curtains over a quiet night
        "vec3 aurora(vec2 uv, float asp, float t){\n" +
        "  vec3 col = mix(vec3(.018,.026,.040), vec3(.010,.012,.020), uv.y);\n" +
        "  col += stars(uv,asp)*.6;\n" +
        "  for(int i=0;i<3;i++){ float fi=float(i);\n" +
        "    float x = uv.x*asp*(1.1+fi*.3) + fi*3.1;\n" +
        "    float y0 = .55 + .12*sin(x*.9 + t*.05 + fi) + .10*fbm(vec2(x*.7, t*.03+fi));\n" +
        "    float band = exp(-pow((uv.y-y0)*(4.+fi*2.),2.));\n" +
        "    float rays = .55 + .45*fbm(vec2(x*9., t*.08+fi*4.));\n" +
        "    float up = smoothstep(y0-.02, y0+.35, uv.y) * smoothstep(y0+.55, y0+.1, uv.y);\n" +
        "    vec3 tint = mix(vec3(.25,.85,.62), vec3(.85,.42,.62), smoothstep(.0,.5,(uv.y-y0)));\n" +
        "    col += tint * (band*.55 + up*.25) * rays * (.6-fi*.15); }\n" +
        "  return col; }\n" +
        // 2: deep space for the Earth to hang in
        "vec3 deep(vec2 uv, float asp, float t){\n" +
        "  vec3 col = vec3(.012,.014,.022) + vec3(.03,.04,.07)*fbm(uv*vec2(asp,1.)*2.+t*.004);\n" +
        "  vec2 sd=(uv-uSun)*vec2(asp,1.); col += vec3(.9,.55,.35)*exp(-dot(sd,sd)*2.)*.10;\n" +
        "  return col + stars(uv,asp)*.8; }\n" +
        // 3: slow volumetric smoke, warm from the sun's side
        "vec3 smoke(vec2 uv, float asp, float t){\n" +
        "  vec2 p = uv*vec2(asp,1.)*1.35;\n" +
        "  vec2 q = vec2(fbm(p + vec2(0., t*.016)), fbm(p + vec2(5.2,1.3) - t*.013));\n" +
        "  vec2 r = vec2(fbm(p + 2.4*q + vec2(1.7,9.2) + t*.010), fbm(p + 2.4*q + vec2(8.3,2.8) - t*.008));\n" +
        "  float f = fbm(p + 2.8*r); float dens = smoothstep(.25,.95,f);\n" +
        "  vec3 col = mix(vec3(.020,.024,.036), vec3(.065,.085,.135), dens*dens*1.6);\n" +
        "  col = mix(col, vec3(.12,.145,.2), clamp(dot(r,r)*.55,0.,1.)*dens*.75);\n" +
        "  vec2 sd=(uv-uSun)*vec2(asp,1.); float d2=dot(sd,sd);\n" +
        "  col += mix(vec3(.85,.42,.24), vec3(1.,.78,.55), exp(-d2*9.)) * exp(-d2*1.05) * (.05+.75*dens*dens);\n" +
        "  return col * mix(.55,1.,smoothstep(.1,.6,f)); }\n" +
        "vec3 scene(float s, vec2 uv, float asp, float t){\n" +
        "  if (s < .5) return horizon(uv,asp,t);\n" +
        "  if (s < 1.5) return aurora(uv,asp,t);\n" +
        "  if (s < 2.5) return deep(uv,asp,t);\n" +
        "  return smoke(uv,asp,t); }\n" +
        "void main(){\n" +
        "  vec2 uv = gl_FragCoord.xy/uRes; float asp = uRes.x/uRes.y; float t = uTime;\n" +
        "  vec3 col = scene(uScene,uv,asp,t);\n" +
        "  if (uMix < 1.) col = mix(scene(uPrev,uv,asp,t), col, uMix);\n" +
        "  col *= uDim;\n" +
        "  col += (hash(gl_FragCoord.xy + fract(t*13.))-.5)/255.;\n" +
        "  gl_FragColor = vec4(col,1.);\n" +
        "}\n";
}
