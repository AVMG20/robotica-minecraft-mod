"""Survey Rig textures: teal-trimmed steel panels, a dark scan window with a bright scan line, the scanner dish and
the glowing receiver. Run: python3 scripts/textures/survey_rig.py

Same 8-pixel-period panel style as scripts/textures/automation.py, so the default UVs of the model's small elements
still read as plating. The model (scripts/data/automation_survey_rig.py) marks the scan line and receiver faces
emissive (neoforge_data block_light / sky_light 15).
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, write_anim, write_block  # noqa: E402

STEEL = {'m': '#8D9599', 'M': '#C4CBCE', 'n': '#5A6266', 'r': '#E8EDEF'}
TEAL = {'t': '#2E8F7F', 'T': '#5AD6BE', 'u': '#145247'}
SCREEN = {'q': '#0C1B1E', 'Q': '#173236', 'y': '#5FE3F0', 'Y': '#D8FBFF', 'z': '#2A7F8A'}


def pal(*dicts):
    out = dict(PALETTE)
    for d in dicts:
        out.update(d)
    return out


def panel():
    """Steel plates with a teal seam every 8 pixels and a small vent grille: the rig's own plating."""
    c = Canvas()
    for ox in (0, 8):
        for oy in (0, 8):
            c.rect(ox, oy, 8, 8, 'm').bevel(ox, oy, 8, 8, 'M', 'n')
            c.set(ox + 1, oy + 1, 'r')
    for oy in (0, 8):
        c.rect(0, oy + 7, 16, 1, 'u')
        c.rect(0, oy + 6, 16, 1, 't')
    for x in (3, 5, 11, 13):
        c.rect(x, 2, 1, 3, 'n')
        c.rect(x, 10, 1, 3, 'n')
    return c.rows()


def scan_window():
    """Dark glass with a faint grid, a bright scan line across the middle and a few ore blips."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'q')
    for x in range(0, 16, 4):
        c.rect(x, 0, 1, 16, 'Q')
    for y in range(1, 16, 4):
        c.rect(0, y, 16, 1, 'Q')
    c.rect(0, 7, 16, 1, 'z').rect(0, 8, 16, 1, 'y').rect(0, 9, 16, 1, 'z')
    for x in range(1, 16, 5):
        c.set(x, 8, 'Y')
    for x, y in ((5, 5), (11, 11), (3, 12), (12, 4)):
        c.set(x, y, 'o').set(x + 1, y, 'O')
    c.frame(0, 0, 16, 16, 'k')
    return c.rows()


def dish():
    """Scanner dish seen from above: concentric rings around a dark feed hole."""
    c = Canvas()

    def inside(x, y):
        return math.hypot(x - 7.5, y - 7.5) <= 7.8

    def fill(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        if r <= 1.2:
            return 'k'
        if r <= 2.2:
            return 'T'
        ring = int(r) % 3
        light = (x + y) < 15
        return ('M' if light else 'm') if ring else 'n'

    c.rect(0, 0, 16, 16, 'n')
    c.shape(inside, fill)
    return c.rows()


def glow():
    """Receiver and scan band: bright cyan, lighter in the middle."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'y').rect(2, 2, 12, 12, 'Y').rect(0, 7, 16, 2, 'Y')
    return c.rows()


def scan_sweep(frame_no):
    """Working scan window: the scan line sweeps down and the ore blips flash as it passes."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'q')
    for x in range(0, 16, 4):
        c.rect(x, 0, 1, 16, 'Q')
    for y in range(1, 16, 4):
        c.rect(0, y, 16, 1, 'Q')
    line = (4, 5, 6, 7, 7, 6, 5, 4)[frame_no % 8]      # the window shows rows 4-7
    for dy, ch in ((-1, 'z'), (0, 'y'), (1, 'z')):
        if 1 <= line + dy <= 14:
            c.rect(1, line + dy, 14, 1, ch)
    for x, y in ((5, 5), (11, 11), (3, 12), (12, 4)):
        near = abs(y - line) <= 1
        c.set(x, y, 'O' if near else 'o').set(x + 1, y, 'Y' if near else 'O')
    c.frame(0, 0, 16, 16, 'k')
    return c.rows()


def glow_pulse(frame_no):
    """Working band and receiver: a bright pulse runs along the band."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'y').rect(2, 2, 12, 12, 'Y').rect(0, 7, 16, 2, 'Y')
    x = (frame_no * 2) % 16
    c.rect(x, 6, 2, 4, 'r')
    return c.rows()


def glow_idle():
    """Idle band and receiver: a dimmer teal."""
    c = Canvas()
    c.rect(0, 0, 16, 16, 'z').rect(2, 2, 12, 12, 'y').rect(0, 7, 16, 2, 'y')
    return c.rows()


def main():
    write_block('survey_rig_panel', panel(), pal(STEEL, TEAL))
    write_block('survey_rig_scan', scan_window(), pal(SCREEN))
    write_block('survey_rig_dish', dish(), pal(STEEL, TEAL))
    write_block('survey_rig_glow', glow_idle(), pal(SCREEN))
    write_anim('block', 'survey_rig_scan_on', [scan_sweep(i) for i in range(8)], pal(SCREEN), frametime=3)
    write_anim('block', 'survey_rig_glow_on', [glow_pulse(i) for i in range(8)], pal(SCREEN, {'r': '#F0FFFF'}), frametime=2)


if __name__ == '__main__':
    main()
