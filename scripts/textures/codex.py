"""Codex texture: slate bound field guide with copper corner caps, a brass cog emblem with a cyan eye and a ribbon.
Run: python3 scripts/textures/codex.py"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import MATERIALS, Canvas, write_item  # noqa: E402

P = {'k': '#1E1A1A', 'S': '#2E3638', 's': '#1F2527', 'T': '#46565A',      # slate cover: mid, shadow, light
     'p': '#E9EDEA', 'P': '#B9C4C0', 'r': '#C9302A', 'R': '#FF6B5E'}
P.update(dict(zip('12345', MATERIALS['copper'])))
P.update({'6': MATERIALS['brass'][1], '7': MATERIALS['brass'][2], '8': MATERIALS['brass'][3]})
P.update({'y': MATERIALS['cyan'][2], 'z': MATERIALS['cyan'][4]})

c = Canvas()
c.rect(1, 1, 12, 14, 'S')
c.rect(13, 2, 2, 13, 'p').rect(14, 2, 1, 13, 'P')                    # page block
for y in range(4, 15, 3):
    c.set(13, y, 'P')
c.auto_shade({'S': ('s', 'T')})
c.rect(3, 1, 1, 14, 's')                                             # spine hinge
for x, y in ((1, 1), (11, 1), (1, 13), (11, 13)):                    # copper corner caps
    c.rect(x, y, 2, 2, '3')
c.set(1, 1, '5').set(11, 1, '4').set(1, 13, '4').set(12, 14, '2')
c.draw(5, 4, ['.7.7.', '78887', '.8z8.', '78y87', '.7.7.'])           # cog with a cyan eye
c.rect(5, 10, 5, 1, '6')                                             # title plate
c.rect(9, 13, 1, 3, 'r').set(9, 15, 'R')                             # ribbon
c.outline('k')
write_item('codex', c.rows(), P)
