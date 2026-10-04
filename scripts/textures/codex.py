"""Codex texture. Run: python3 scripts/textures/codex.py"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import PALETTE, Canvas, write_item  # noqa: E402

c = Canvas()
c.rect(2, 1, 12, 14, 'S').frame(1, 0, 14, 16, 'k')          # slate cover
c.rect(12, 1, 2, 14, 'P').rect(13, 1, 1, 14, 'p')           # page edges
c.rect(2, 1, 2, 2, 'c').rect(2, 13, 2, 2, 'c')               # copper corners
c.rect(10, 1, 2, 2, 'c').rect(10, 13, 2, 2, 'c')
c.rect(5, 5, 4, 4, 'c').frame(5, 5, 4, 4, 'C').set(6, 6, 'y').set(7, 6, 'y')  # gear plate with cyan eye
c.rect(5, 10, 4, 1, 'C')
write_item('codex', c.rows(), PALETTE | {'S': '#2E3638', 'P': '#E9EDEA', 'p': '#B9C4C0'})
