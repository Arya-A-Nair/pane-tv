"""Original environment stills: volumetric smoke lit from a 'light well' in the upper right.
usage: gen.py outdir [name]"""
import sys, numpy as np
from PIL import Image
W, H = 1920, 1080
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
u, v = xx / W, yy / H

def vnoise(rng, cx, cy):
    g = (rng.random((cy + 3, cx + 3)).astype(np.float32) * 255)
    im = Image.fromarray(g, 'F').resize((W + 2 * W // cx, H + 2 * H // cy), Image.BICUBIC)
    a = np.asarray(im)[H // cy:H // cy + H, W // cx:W // cx + W] / 255.0
    return a

def fbm(rng, cx, cy, octs=5, gain=0.5):
    out = np.zeros((H, W), np.float32); amp = 1.0; tot = 0
    for i in range(octs):
        out += amp * vnoise(rng, cx * 2 ** i, cy * 2 ** i); tot += amp; amp *= gain
    return out / tot

def sample(a, x, y):
    x = np.clip(x, 0, W - 1.001); y = np.clip(y, 0, H - 1.001)
    x0 = x.astype(np.int32); y0 = y.astype(np.int32); fx = x - x0; fy = y - y0
    return (a[y0, x0] * (1 - fx) + a[y0, x0 + 1] * fx) * (1 - fy) + (a[y0 + 1, x0] * (1 - fx) + a[y0 + 1, x0 + 1] * fx) * fy

def lin(h):
    c = np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], np.float32) / 255
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)

def srgb(l):
    l = np.clip(l, 0, 1)
    return np.where(l <= 0.0031308, l * 12.92, 1.055 * l ** (1 / 2.4) - 0.055)

def lum(rgb): return rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722

LIGHT = (0.77, 0.26)   # where the light sits, in screen fractions
SPOT = None            # optional bright spot for a photograph: (cx, cy, rx, ry, fade, keep_zone)
def ss(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t)

def well():
    """1 where the picture may be bright (upper right), 0 where bare text can sit (left 40%, bottom 32%)."""
    f = ss(u, 0.40, 0.56) * (1 - ss(v, 0.56, 0.68))
    if SPOT:
        cx, cy, rx, ry, fade, keep = SPOT
        spot = 1 - ss(np.sqrt(((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2), 1.0, fade)
        f = np.maximum(f, spot) if keep else spot
    return f

def grade(rgb, lo=0.085, hi=0.42):
    """Soft-cap luminance: lo everywhere text can sit, hi inside the light well. Hue is kept."""
    cap = lo + (hi - lo) * well()
    L = lum(rgb) + 1e-6
    L2 = cap * np.tanh(L / cap)
    return rgb * (L2 / L)[..., None]

def finish(rgb, path, seed=1):
    rng = np.random.default_rng(seed)
    s = srgb(rgb) * 255 + rng.normal(0, 1.1, (H, W, 1)).astype(np.float32)  # film grain, kills banding
    Image.fromarray(np.clip(s + 0.5, 0, 255).astype(np.uint8)).save(path, quality=95, subsampling=0)

def soft(a, k):
    """Cheap wide blur: down and back up with bicubic."""
    im = Image.fromarray(a.astype(np.float32), 'F')
    return np.asarray(im.resize((W // k, H // k), Image.BILINEAR).resize((W, H), Image.BICUBIC))

def smoke(seed, shadow, mid, hi, key):
    rng = np.random.default_rng(seed)
    wx, wy = fbm(rng, 2, 2, 3) - .5, fbm(rng, 2, 2, 3) - .5
    fx, fy = fbm(rng, 5, 3, 3) - .5, fbm(rng, 5, 3, 3) - .5
    far = sample(fbm(rng, 3, 2, 4, 0.5), xx + 520 * wx, yy + 340 * wy)        # slow body of the cloud
    near = sample(fbm(rng, 7, 5, 6, 0.56), xx + 620 * wx + 150 * fx, yy + 400 * wy + 120 * fy)  # wisps
    far = (far - far.min()) / (far.max() - far.min()); near = (near - near.min()) / (near.max() - near.min())
    # the cloud lives on a diagonal from the light well down to the lower left, and thins out away from it
    cx, cy = LIGHT
    band = np.exp(-(((v - cy) - 0.55 * (cx - u)) ** 2) / 0.085) * (0.35 + 0.65 * ss(u, 0.0, 0.8))
    body = ss(far, 0.30, 0.85)
    d = np.clip(body * (0.45 + 0.75 * ss(near, 0.25, 0.8)) * (0.25 + 0.95 * band), 0, 1)
    d = np.clip(0.75 * d + 0.25 * soft(d, 24), 0, 1)                                          # scatter: soft halo round the wisps
    r2 = ((u - cx) * 16 / 9) ** 2 + (v - cy) ** 2
    light = np.exp(-r2 / 0.22)
    core = np.exp(-r2 / 0.03)
    S, M, Hc, K = lin(shadow), lin(mid), lin(hi), lin(key)
    rgb = S[None, None] + M[None, None] * (d * (0.30 + 1.6 * light))[..., None] \
        + Hc[None, None] * (d ** 1.8 * light * 1.1)[..., None] + K[None, None] * (core * d * 0.8)[..., None]
    rgb += M[None, None] * (light * 0.05)[..., None]                           # air glow around the light
    vig = 1 - 0.5 * np.clip(((u - .55) ** 2 + (v - .45) ** 2) * 1.5, 0, 1)
    return rgb * vig[..., None]

STILLS = {
    'nocturne': (3, '#05070b', '#22357a', '#a4b8ee', '#e8d2b4'),
    'tide':     (55, '#04080a', '#0c5560', '#86d8d2', '#dcefe2'),
    'ember':    (13, '#0a0605', '#7c2c16', '#f0a878', '#ffe2b8'),
    'dune':     (21, '#090806', '#5a4a36', '#d9c5a8', '#f4e8d4'),
    'orchid':   (34, '#08060b', '#4a2670', '#c9a2e6', '#f2cdd6'),
    'graphite': (89, '#07080a', '#343a46', '#c9d0dc', '#eef1f6'),
}
if __name__ == '__main__':
    out = sys.argv[1]
    for n, (seed, *cols) in STILLS.items():
        if len(sys.argv) > 2 and sys.argv[2] not in (n, 'all'): continue
        finish(grade(smoke(seed, *cols)), f'{out}/{n}.jpg', seed)
        print(n)

def photo(src, out, seed, spot=None, hi=0.42):
    global SPOT
    SPOT = spot
    a = np.asarray(src.convert('RGB'), np.float32) / 255
    a = np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)
    finish(grade(a, 0.085, hi), out, seed); SPOT = None

def photos(env, src, out):
    photo(Image.open(f'{env}/earthset.jpg'), f'{out}/earthset.jpg', 1, (0.475, 0.455, 0.09, 0.07, 1.5, True), 0.8)
    photo(Image.open(f'{env}/sunrise.jpg'), f'{out}/sunrise.jpg', 2, (0.80, 0.10, 0.22, 0.24, 2.4, False), 0.5)
    photo(Image.open(f'{env}/aurora.jpg'), f'{out}/aurora.jpg', 3)
    e = Image.open(f'{src}/GSFC_20171208_Archive_e001788.jpg').convert('RGB').resize((1220, 1220), Image.LANCZOS)
    c = Image.new('RGB', (W, H)); c.paste(e, (1470 - 610, 330 - 610))
    photo(c, f'{out}/marble.jpg', 4)

if __name__ == '__main__' and len(sys.argv) > 3:   # environments.py out all <photo-src-dir> <old-env-dir>
    photos(sys.argv[4], sys.argv[3], sys.argv[1])
