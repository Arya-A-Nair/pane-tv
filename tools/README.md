# Tools

Python helpers (Python 3, Pillow 9.1+ and numpy). Run them from the repo root.

- `environments.py`: regenerates the ten backgrounds in `assets/env` (six drawn, four from the NASA crops).
- `photographs/fetch.py`: re-makes the 4K NASA crops in `tools/photographs` from the originals (downloads about 100 MB).
- `check_assets.py`: asserts the backgrounds are 3840 x 2160 and the moon map is 2048 x 1024.
- `contrast.py`: measures text contrast on a TV screenshot against DESIGN.md's 4.5:1 floor.
