"""Re-makes the 3840 x 2160 crops in this folder from the NASA Image Library originals
(public domain). Framing = full width, 16:9, vertical offset matched to the old 1920 crops.
usage: python3 tools/photographs/fetch.py     Needs Pillow; downloads ~100 MB once into $TMPDIR.
"""
import os, tempfile, urllib.request
from PIL import Image
Image.MAX_IMAGE_PIXELS = None
here = os.path.dirname(os.path.abspath(__file__))
cache = tempfile.gettempdir()
URL = 'https://images-assets.nasa.gov/image/{0}/{0}~orig.jpg'
# name: (nasa id, y offset of the 16:9 crop in the original)
CROPS = {'earthset': ('art002e021278', 355), 'sunrise': ('iss040e080833', 222), 'aurora': ('iss062e148363', 366)}

def get(nid):
    p = os.path.join(cache, nid + '.jpg')
    if not os.path.exists(p): urllib.request.urlretrieve(URL.format(nid), p)
    return Image.open(p).convert('RGB')

for name, (nid, y) in CROPS.items():
    im = get(nid); w = im.width
    im.crop((0, y, w, y + w * 9 // 16)).resize((3840, 2160), Image.Resampling.LANCZOS).save(f'{here}/{name}.jpg', quality=95, subsampling=0)
# the whole Earth, square; environments.py sizes and places it
get('GSFC_20171208_Archive_e001788').resize((2440, 2440), Image.Resampling.LANCZOS).save(f'{here}/blue-marble.jpg', quality=95, subsampling=0)
