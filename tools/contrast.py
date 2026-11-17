"""Measures text contrast on a real screenshot (see DESIGN.md, "Contrast").

For each region of a page where text sits, takes the brightest 0.5 % of the background
behind it (text itself masked out) and reports the contrast of the lightest text tone used
there. Anything under 4.5:1 is marked FAIL.

usage: adb exec-out screencap -p > shot.png
       python3 tools/contrast.py shot.png home|apps|today|bgs|settings|ambient
Needs numpy and Pillow.
"""
import sys, numpy as np
from PIL import Image, ImageFilter
def lin(c): return np.where(c <= .04045, c / 12.92, ((c + .055) / 1.055) ** 2.4)
def L(rgb): return lin(np.asarray(rgb, np.float32) / 255) @ np.array([.2126, .7152, .0722])
PORCELAIN, MIST, GLACIER, CHAMP = L([0xe9, 0xec, 0xf2]), L([0xce, 0xd4, 0xdf]), L([0xa4,0xb8,0xee]), L([0xe0,0xd2,0xbf])
# regions in dp (x0,y0,x1,y1), with the lightest text tone used inside each
R = {
 'home': [('time + date', 50, 40, 330, 70, MIST), ('hero line', 50, 222, 420, 248, MIST), ('weather widget', 646, 54, 900, 158, MIST), ('sky widget', 700, 182, 900, 246, MIST),
          ('app names', 56, 394, 900, 438, MIST), ('navigation', 330, 452, 630, 488, MIST)],
 'apps': [('title', 50, 40, 300, 90, PORCELAIN), ('aside', 600, 56, 904, 80, MIST), ('app names row 1', 56, 208, 904, 250, MIST), ('app names row 2', 56, 360, 904, 404, MIST), ('navigation', 330, 452, 630, 488, MIST)],
 'today': [('title', 50, 40, 300, 90, PORCELAIN), ('aside', 600, 56, 904, 80, MIST), ('weather panel', 60, 116, 560, 260, MIST), ('air panel', 590, 116, 900, 222, MIST),
           ('daylight panel', 60, 284, 250, 416, MIST), ('daylight times', 250, 380, 450, 404, PORCELAIN), ('moon panel text', 640, 290, 900, 410, MIST), ('navigation', 330, 452, 630, 488, MIST)],
 'bgs': [('title', 50, 40, 400, 90, PORCELAIN), ('aside', 560, 56, 904, 80, MIST), ('names row 1', 56, 220, 904, 246, MIST), ('names row 2', 56, 370, 904, 394, MIST), ('navigation', 330, 452, 630, 488, MIST)],
 'settings': [('title', 50, 40, 400, 90, PORCELAIN), ('list', 62, 170, 490, 418, MIST), ('this tv', 550, 130, 800, 330, MIST), ('navigation', 330, 452, 630, 488, MIST)],
 'ambient': [('clock', 50, 340, 420, 450, PORCELAIN), ('line', 50, 470, 520, 494, PORCELAIN)],
}
def check(path, page):
    im = Image.open(path).convert('RGB'); l = L(im); worst = 99
    l_s = L(im.filter(ImageFilter.BoxBlur(2)))      # what the eye sees behind a stroke, not single grains
    # text = bright pixels; grow that mask so anti-aliased edges are not mistaken for background
    mask = Image.fromarray(((l > 0.22) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))
    bgmask = np.asarray(mask) == 0
    out = []
    for name, x0, y0, x1, y1, tone in R[page]:
        reg = l_s[y0 * 2:y1 * 2, x0 * 2:x1 * 2]; ok = bgmask[y0 * 2:y1 * 2, x0 * 2:x1 * 2]
        if ok.sum() < 50: continue
        bg = np.percentile(reg[ok], 99.5)           # the brightest background in the region
        cr = (tone + .05) / (bg + .05); worst = min(worst, cr)
        out.append((name, bg, cr))
    return worst, out
if __name__ == '__main__':
    w, out = check(sys.argv[1], sys.argv[2])
    for name, bg, cr in out: print(f'  {name:22s} background L {bg:.3f}  contrast {cr:4.1f}:1 {"" if cr >= 4.5 else "  <-- FAIL"}')
