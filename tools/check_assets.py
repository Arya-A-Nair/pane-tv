"""Asserts every background in assets/env is 4K and 16:9 (the NASA originals are all >= 3840 wide), and the moon map is 2048 x 1024."""
import glob, os
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__))
files = sorted(glob.glob(f'{here}/../assets/env/*.jpg'))
assert len(files) == 10, files
for f in files:
    w, h = Image.open(f).size
    assert w >= 3840 and w * 9 == h * 16, (f, w, h)
assert Image.open(f'{here}/../assets/moon.jpg').size == (2048, 1024)
print('ok:', len(files), 'backgrounds at 3840 x 2160, moon 2048 x 1024')
