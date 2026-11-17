# Cinematic Spatialism

The design language of Pane, a home screen for Fire TV. Everything in `src/tv/nova` implements what
is written here; if the two disagree, this file is right and the code has a bug.

The screen is 1920 x 1080 px at density 2, so all measures below are in dp on a
**960 x 540** canvas. It is watched from about 3 m and driven by a remote with a D-pad, OK,
Back and Menu. The people using it are a family, parents included.

---

## 1. The idea

**A dark room, lit by what you are about to watch. Glass keeps its light; everything else steps back.**

Three rules follow from that sentence, and every screen obeys them.

1. **The environment is the light source.** Behind everything is one dark, cinematic still —
   smoke lit from the upper right, or a NASA photograph. On Home it is re-lit in the colour of
   the app under focus, so moving along the row changes the mood of the whole room.
2. **Glass is for grouped information only.** A surface is glass when it holds several facts
   that belong together (a weather reading, a list of settings, the navigation). Text that
   stands alone stands on the environment. Pictures are never wrapped in glass.
3. **One thing is large.** Each screen has exactly one large typographic moment in Manrope
   Light — the focused app's mark on Home, the page title elsewhere, the time when idle.
   Everything else is quiet Inter.

What this is deliberately **not**: no accent-colour outlines or glows, no gradient washes
behind text, no brackets or HUD marks, no tracked ALL-CAPS eyebrow labels, no icon set for
weather, no mono type except one block of system facts, no card around every element.

---

## 2. Colour

### Palette

| Name | Hex | Relative luminance | Role |
|---|---|---|---|
| Obsidian | `#090B10` | 0.003 | The base of every environment; text on Porcelain; launch veil |
| Graphite | `#20242C` | 0.018 | Secondary text on Porcelain |
| Porcelain | `#E9ECF2` | 0.837 | All primary text; the focus colour (rings and focused controls) |
| Mist | `#CED4DF` | 0.655 | Secondary text: labels, captions, unfocused names |
| Glacier | `#A4B8EE` | 0.483 | A mark that says "in use" (current background, apps that are on Home); the moon's path at night |
| Champagne | `#B4A28C` | 0.374 | Warmth and daytime: the sun's path, the colon of the idle clock (as `#E0D2BF`) |

Air-quality steps, good to hazardous — **marks only, never text**:
`#8FCBA8 #D9C58A #E0A66E #DE8266 #C97B9E #A97FB5`.

### Rules

- **Text is Porcelain or Mist. Nothing else.** Hierarchy comes from size and weight, then from
  the Porcelain/Mist pair. Glacier, Champagne and the air colours are only ever dots, lines
  and bars. (This is what removes "peach text on pink glass": the AQI figure is Porcelain; its
  severity is a coloured step on a scale beside it.)
- **Focus is Porcelain.** There is no accent-coloured focus. See section 6.
- **Colour comes from the room, not from the interface.** The interface itself is neutral; the
  environment — and on Home, the focused app — supplies all the colour, and glass picks it up.

### Contrast — the guarantee and how it is kept

Every text/background pair must reach **4.5:1**. Porcelain needs a background luminance of
0.147 or less; Mist needs 0.106 or less. Three mechanisms make that true by construction
rather than by luck:

1. **Environments are graded.** `tools/environments.py` soft-caps luminance at 0.085 wherever
   bare text can sit on Home — the left 40 % of the screen and the bottom 32 % — and lets the
   picture be bright (up to about 0.42) only in the upper-right "light well". Hue is preserved.
   (One exception by design: the Earth in *Earthset* sits just left of centre, at a height
   where Home has no text.)
2. **Away from Home the room dims.** Other pages replace the sharp environment with its
   blurred, darkened copy, so bare text is safe anywhere on them.
3. **Glass is tone-mapped.** `Env.tone` caps the frosted layer at luminance 0.068 (hue
   preserved); the sheen and grain on top add about 0.02.

Measured on the device, with `tools/contrast.py` on screenshots of every page in every environment
(worst case over all ten environments, Mist unless noted):

| Page | Lowest contrast found | Where |
|---|---|---|
| Home | 4.7:1 | Date beside the time, on the Orbital sunrise photograph |
| Home, any app focused (Nocturne) | 5.0:1 | Widgets, with "All apps" focused (room in its own colours) |
| Apps | 5.2:1 | Names under icons, Aurora |
| Today | 5.0:1 | Second lines on glass |
| Backgrounds | 5.2:1 | Names under pictures, Aurora |
| Settings | 5.2:1 | Values on the glass list |
| Idle | 14.5:1 | Porcelain on the lower-left of the environment |

The measure is deliberately harsh: Mist against the *brightest* 0.5 % of the background in
each text region, after a 2 px blur.

Solid Porcelain controls: Obsidian on Porcelain is 16.6:1, Graphite on Porcelain 13.1:1.

---

## 3. Typography

Three families, each with one job.

| Role | Face | Size (sp) | Tracking | Colour | Used for |
|---|---|---|---|---|---|
| Display | Manrope Light | 132 | -0.02 | Porcelain | The idle clock |
| Hero | Manrope Light | 72 | -0.02 | Porcelain | Focused app's name on Home (when its own mark is too small to show) |
| Title | Manrope Light | 40 | -0.02 | Porcelain | Page titles |
| Figure | Manrope Light | 58 / 46 | 0 | Porcelain | Temperature, air index |
| Value | Manrope Light | 34 / 30 | 0 | Porcelain | Time on Home; daylight left, moon phase |
| Strong | Inter Medium | 16 | 0 | Porcelain | The line beside a figure; settings labels |
| Label | Inter Medium | 15 / 14 | 0 | Porcelain or Mist | Navigation; names under icons and pictures |
| Body | Inter Regular | 16 / 15 | 0 | Mist | Hero context line; page context line; setting values |
| Caption | Inter Regular | 14 | 0 | Mist | Panel labels, second lines |
| System | JetBrains Mono | 13 | +0.01 | Mist / Porcelain | "This TV" facts in Settings — nowhere else |

- **Floor: 13 sp** (26 px). Anything a parent must read to use the TV is 14 sp or more.
- Sentence case everywhere. No all-caps, no letter-spaced labels.
- Numerals in Manrope Light are the "instrument" voice: every measured quantity
  (time, temperature, index, duration) is set in it, and nothing else is.
- **Nothing is ever truncated.** No ellipsis is allowed anywhere. App names are shortened at
  the source (`"Fawesome: Watch Free Movies & TV"` becomes `"Fawesome"`), and names under
  icons may wrap to two lines.
- Text standing directly on the environment carries a wide, soft shadow
  (8 dp blur, 45 % black). It is insurance against a busy photograph, not an effect.

---

## 4. Space

- **Safe area:** nothing is drawn within **48 dp** of any edge, including under focus scale.
- **Margins:** 56 dp left and right; first baseline block starts at 48 dp; the navigation's
  bottom edge is at 492 dp (48 dp from the bottom).
- **Grid:** 8 dp. Gaps between panels 16 dp (12 dp inside a stack); panel padding 24 dp
  horizontal, 20 dp vertical.
- **Columns:** content is 848 dp wide. Apps use 6 columns of 141 dp; backgrounds 5 columns
  with 20 dp gutters; Today is two rows of panels.
- **Asymmetry on purpose:** type and actions hang from the left margin; light and glanceable
  information sit on the right. The centre is allowed to be empty.
- **Corner radii:** panels 28 dp; pictures 12 dp; controls are full capsules; apps are circles.
  Those four and no others.

---

## 5. Materials

### Environment

A 1920 x 1080 still, never stretched. Six are original artwork generated by
`tools/environments.py` (domain-warped fractal smoke, lit from the light well, film grain
added so dark gradients do not band): **Nocturne** (default — Glacier smoke with a Champagne
core, i.e. the palette itself), **Tide**, **Ember**, **Dune**, **Orchid**, **Graphite**. Four
are NASA photographs graded by the same script: **Earthset**, **Orbital sunrise**, **Aurora**,
**Blue Marble**.

On Home the artwork environments are *re-lit*: brightness is kept and 66 % of the colour is
replaced by the focused app's own colour, cross-fading over 700 ms as focus moves. The light
colour sits halfway between "same brightness" and the pure hue, so red stays crimson instead
of turning pink. Glass is re-lit with it. Photographs are never re-lit (a pink Earth is
wrong), and on every other page, and when idle, the room is in its own colours.

### Glass

Fire OS 7 has no backdrop blur, so glass is built honestly from the known scene:

| Layer | Recipe |
|---|---|
| 1. Frost | The environment at 1/8 size, box-blurred three times (radius 7 → about 110 px at full size), drawn at the panel's own screen position so it lines up with what is behind |
| 2. Tone | Saturation x1.35, lift x1.7, plus a floor of the scene's own light colour (so glass over a black corner is tinted, never grey), then luminance soft-capped at 0.068 |
| 3. Sheen | White 5 % at the top edge fading to nothing by 55 % of the height |
| 4. Grain | A 96 px tile of black/white noise at up to 7 % — gives the surface tooth and hides banding |
| 5. Rim | 0.75 dp hairline: white 44 % along the top, 11 % down the sides, 15 % along the bottom |
| 6. Shadow | 10 dp elevation, 55 % |

Edges are anti-aliased (`drawRoundRect` with a bitmap shader, not a clip path).

**Glass is used for:** the navigation, the two widgets on Home, the four readings on Today,
the settings list. **Glass is not used for:** app icons, background thumbnails, page titles,
the hero, the idle clock, or anything that is a single line of text.

### Solid Porcelain

The one opaque material. It appears only as the focus state of a control.

---

## 6. Focus

There is always exactly one focused thing and it must be findable from across the room in
under a second. Two forms, chosen by what the thing *is*:

| The thing is… | Under focus it… | Used by |
|---|---|---|
| **an object** (an app, a picture) | comes forward 1.1x (pictures 1.07x), gains a 2.5 dp Porcelain ring standing 4 dp off its edge, lifts its shadow; its name turns from Mist to Porcelain and drops 7 dp to make room | App tiles, background thumbnails |
| **a control** (a word you press) | becomes a solid Porcelain capsule with Obsidian text | Navigation items, settings rows |

The page you are on is marked in the navigation by a faint Porcelain capsule (15 %); it is
visibly not the focus.

---

## 7. Iconography

- **Apps are circles**, 92 dp on Home and 80 dp in Apps, cut from each app's own
  highest-density icon. A mark on a plain colour is fitted whole inside the circle on that
  colour; a full-bleed tile is cropped to fill it. A 0.75 dp white 20 % hairline seats each
  icon on the dark.
- **App marks in the hero** come from the app's TV banner at its highest density, lifted off
  the banner's background. A mark is only shown when it can be large (at least 220 dp wide or
  64 dp tall) without being enlarged past its own pixels; otherwise the app's name is set in
  Manrope Light instead. Never a blurry logo.
- **System glyphs:** there is exactly one — the 2 x 2 grid on the "All apps" tile. Weather
  uses words, not pictograms. The moon is the real moon, rendered from NASA's LRO map at
  tonight's phase.
- **Marks:** a Glacier dot before a name means "in use" (the current background; an app that
  is on Home). An air-colour dot or scale step shows severity.

---

## 8. Motion

Restrained, damped, never bouncing. All durations x0.6 when Motion is set to Reduced.

| Motion | Duration | Curve |
|---|---|---|
| Focus (scale, lift, name) | 300 ms | Spring, damping 0.86 (`Motion.FOCUS`) — settles with no visible overshoot |
| Focus ring / control fill | immediate to 160 ms | linear |
| Hero text change | 110 ms out, 380 ms in, 10 dp slide | `Motion.GLIDE` (damping 0.98) |
| Room re-light | 700 ms | ease-out quint (`Motion.EASE`) |
| Page change | 420 ms, 12 dp rise; room dims over 450 ms | `GLIDE` / `EASE` |
| Launch | 520 ms: the icon glides to centre at 112 dp, the name appears beneath in Manrope Light, the room fades to Obsidian; the app opens 260 ms later | `GLIDE` / `EASE` |
| Return | 600 ms fade from 1.02x | `GLIDE` |
| Idle | interface fades over 1.4 s; the environment drifts in by 8 % over three minutes | linear |
| First appearance | each block rises 14 dp, 80 ms apart, 800 ms | `GLIDE` |

Nothing loops, pulses or glows while the screen is in use.

---

## 9. Navigation model

- A glass bar at the bottom holds the five pages: **Home, Apps, Today, Backgrounds, Settings**.
- **Down** from any page's content reaches the bar. On the bar, **Left/Right** change page at
  once (like tabs); **Up** or **OK** go into the page.
- **Back** always returns to Home, focus on what was last watched.
- **Menu** opens Settings — except in Apps, where it adds the focused app to Home or removes
  it, and the page says so in words at the top right ("Menu · Add to Home").
- **Today** has nothing to select: it is for reading, so focus stays on the bar.
- There are no long-presses, double-presses or hidden gestures.

---

## 10. Pages

### Home

The room is lit by the focused app. Its mark is the one large thing. Focus starts on the app
last watched, so OK continues it.

```
 48 ┌──────────────────────────────────────────────────────────────────────────────┐
    │ 18:19  Tuesday 17 November                           ╭───────────────────────╮│
    │                                                      │ 29°  Clear night      ││
    │                                                      │      Mumbai           ││
128 │ N E T F L I X          <- app mark or name, 72sp     │ ● Air 210 · Very unh. ││
    │                                                      ╰───────────────────────╯│
226 │ Netflix · Continue · Opened 2 hours ago              ╭───────────────────────╮│
    │                                                      │ ◐  First quarter      ││
    │                                                      │    Sunrise 06:47      ││
294 │  (◯)   ( ◉ )   (◯)   (◯)   (◯)   (◯)   (⠿)           ╰───────────────────────╯│
    │ Netflix  Prime  Hotstar YouTube Zee 5  Sony  All apps                         │
452 │               ╭ Home  Apps  Today  Backgrounds  Settings ╮                    │
492 └──────────────────────────────────────────────────────────────────────────────┘
    56                                                   624                  904
```

- Row: up to six favourites and "All apps", 92 dp discs on a 121 dp pitch. If the app last
  watched is not a favourite it takes the first place.
- Hero: the app's own mark when its banner is sharp enough to be large, otherwise its name
  in Manrope Light 72. It never leaves the left 340 dp, where the picture is graded for
  type: a long name steps down through 60, 50 and 42 sp, then takes two lines at 32. It changes with a 10 dp slide as focus moves.
- Hero line: `Continue · Opened 2 hours ago` for the last-watched app, `Opened yesterday`
  for others, `Press OK to open` for one never opened, the app count for "All apps".
- Widgets (280 dp wide, not focusable): weather with the air reading; tonight's moon with the
  next sunrise or sunset. They show "No reading yet / Waiting for the network" when offline.

### Apps

```
    │ Apps                                                  Menu · Add to Home     │
116 │   (◯)      (◯)      (◯)      (◯)      (◯)      (◯)                          │
    │ ● Netflix ● Prime  ● Hotstar ● YouTube ● Zee 5  ● Sony LIV                    │
268 │   (◯)      (◯)      (◯)      (◯)      (◯)      (◯)                          │
    │  JioCinema MX Player  ...                                                     │
452 │               ╭ Home  Apps  Today  Backgrounds  Settings ╮                    │
```

Six columns, two rows on show, scrolling a row at a time; rows out of view fade rather than
clip. Apps that are on Home come first, marked with a Glacier dot. No glass.

### Today

```
    │ Today                                        Tuesday 17 November · 18:19      │
112 │ ╭ Mumbai ──────────────────────────────────╮ ╭ Air quality ─────────────────╮ │
    │ │ 29°  Clear night        28° 27° 26° ...  │ │ 210  Very unhealthy          │ │
    │ │      34° / 22°          •──•──•──•──•   │ │      Stay indoors if you can │ │
    │ │                         19  21  23  01   │ │ ▁▁ ▁▁ ▁▁ ▁▁ ██ ▁▁            │ │
264 │ ╰──────────────────────────────────────────╯ ╰──────────────────────────────╯ │
280 │ ╭ Daylight ───────────────────╮ ╭ ◐  Moon ────────────────────────────────╮  │
    │ │ 5 h 12 min        ╭──☀··╮   │ │     First quarter                       │  │
    │ │ of daylight left 06:47 17:59│ │     52% lit · Full on 24 November       │  │
420 │ ╰─────────────────────────────╯ ╰─────────────────────────────────────────╯  │
```

Every reading has the same anatomy: a Mist label, one Manrope figure, two lines beside it,
and one small drawing that is specific to it (a curve of the next twelve hours, a six-step
air scale, the sun's path, the moon itself).

The sky panel follows the real sky. By day it is the sun on its path, in Champagne, with the
daylight left. From sunset to sunrise it becomes the moon's own path from moonrise to
moonset, in Glacier, with the moon at tonight's phase where it is now — computed on the
device for the TV's location (`MoonPath`, within about two minutes of a full ephemeris). It
says "Moon sets at 00:40", "Moonrise at 23:40", "Moon set at 23:48" or "No moon", always
with the next sunrise beneath. The two states share one layout and cross-fade.

### Backgrounds

Two rows of five pictures, 154 x 86 dp, radius 12. The picture is the tile. The one in use
has a Glacier dot before its name; the context line gives the credit of the focused one and
"OK to use".

### Settings

```
    │ Settings                                                                      │
112 │ ╭─────────────────────────────────────────╮     This TV                       │
    │ │ Fire TV settings                   Open │     Model    Amazon AFTMM         │
    │ │▐ Wi-Fi                             Open▌│     System   Android 7.1.2        │
    │ │ Manage apps                        Open │     Address  192.168.1.16         │
    │ │ About this TV                      Open │     Storage  1.6 GB free          │
    │ │ Screensaver after             3 minutes │     Memory   482 MB free          │
    │ │ Motion                             Full │     Apps     18                   │
424 │ ╰─────────────────────────────────────────╯                                   │
```

One glass list, 440 dp wide, rows 50 dp. A row says in words what OK will do ("Open") or
shows its current value, which OK changes. The system facts are the only mono type.

### Launch

The focused icon glides to the centre, its name appears beneath it in Manrope Light 30, and
the room goes to Obsidian around it. Then the app opens with a cross-fade.

### Idle

After the chosen time with no input the interface fades away. The environment — in its own
colours, at full strength — is the picture, drifting slowly inward. Bottom left: the time in
Manrope Light 132 with a Champagne colon, and one line with the date and weather. Any key
brings everything back.

---

## 11. Where things live

| File | What it is |
|---|---|
| `Ui` | The tokens above (colour, space, type) and the helpers every screen builds with |
| `Nova` | The activity: focus, the remote, launching, going idle |
| `Page` and `HomePage`, `AppsPage`, `TodayPage`, `BackgroundsPage`, `SettingsPage` | One class per page |
| `BottomBar`, `AppTile`, `Screensaver`, `SkyArc` | Components |
| `Room`, `Env`, `GlassPanel` | Materials: the environment and its light, the frost, glass |
| `Motion` | The curves and the Reduced setting |
| `Apps`, `Weather`, `Moon`, `MoonPath` | Data. `MoonPath` has a self-check: `java -cp out/classes tv.nova.MoonPath` |
| `Watcher`, `Boot` | Keeps the Home button pointing here on Fire OS |
| `tools/environments.py`, `tools/contrast.py` | Generate and grade the environments; measure contrast on a screenshot |

## 12. Checking a change

- `python3 tools/environments.py` regenerates and regrades the environments;
  `python3 tools/contrast.py shot.png home` measures a screenshot.
- `adb shell am start -n tv.nova/.Nova --ez audit true` logs (tag `AUDIT`) any text that does
  not fit, any text under 13 sp, and any view outside the safe area.
- Test states without touching the family's apps: `--ez dry true` (launches open nothing),
  `--ei aqi 40`, `--ei now 600` (minutes since midnight, for the daylight arc),
  `--ez noweather true`, `--ez ambient true`, `--es env tide`, `--ei page 2`, `--ei sel 3`.
