#!/usr/bin/env python3
"""Turns the chosen showcase screenshots (run-showcase/screenshots, from scripts/showcase.sh) into docs/shots/*.jpg.

Scenes are scaled to 1600 px wide, GUIs cropped to a centred square and scaled to 1000 px. JPEG quality steps down
until a file fits MAX_BYTES. Uses macOS sips. Run: python3 scripts/shots.py
"""
import glob
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'run-showcase', 'screenshots')
OUT = os.path.join(ROOT, 'docs', 'shots')
MAX_BYTES = 300_000

# docs/shots name -> (screenshot glob, kind). GUI screenshots carry a block index that changes, hence the globs.
SHOTS = {
    '02_blocks_close_a': ('02_blocks_close_a.png', 'scene'),
    '07_robots': ('07_robots.png', 'scene'),
    '08_replicator': ('08_replicator.png', 'scene'),
    '09_gate_and_pad': ('09_gate_and_pad.png', 'scene'),
    '10_drill_hud_outline': ('10_drill_hud_outline.png', 'scene'),
    '11_tesla_network': ('11_tesla_network.png', 'scene'),
    '12_scrap_colossus': ('12_scrap_colossus.png', 'scene'),
    '13_rusted_foundry': ('13_rusted_foundry.png', 'scene'),
    '14_exo_frames': ('14_exo_frames.png', 'scene'),
    '15_industry_machines': ('15_industry_machines.png', 'scene'),
    '16_grinder_furnace': ('16_grinder_furnace.png', 'scene'),
    '17_item_pipes': ('17_item_pipes.png', 'scene'),
    '18_energy_multiblocks': ('18_energy_multiblocks.png', 'scene'),
    '19_core_reactor': ('19_core_reactor.png', 'scene'),
    '20_capacitor_bank': ('20_capacitor_bank.png', 'scene'),
    '21_tesla_spire': ('21_tesla_spire.png', 'scene'),
    '28_ring_collider': ('28_ring_collider.png', 'scene'),
    '22_wireless_charger': ('22_wireless_charger.png', 'scene'),
    '23_excavators_survey_rigs': ('23_excavators_survey_rigs.png', 'scene'),
    '24_spark_lamp_cave': ('24_spark_lamp_cave.png', 'scene'),
    '25_architect_build': ('25_architect_build.png', 'scene'),
    '26_solar_panels': ('26_solar_panels.png', 'scene'),
    '27_forge_tyrant': ('27_forge_tyrant.png', 'scene'),
    'rancher_pen': ('rancher_pen.png', 'scene'),
    'rancher_close': ('rancher_close.png', 'scene'),
    'hauler_drone': ('hauler_drone.png', 'scene'),
    'codex_01_start': ('codex_01_start.png', 'scene'),
    'codex_03_recipe': ('codex_03_recipe.png', 'scene'),
    'gui_stumpy': ('gui_*_stumpy.png', 'gui'),
    'gui_architect_demolish': ('gui_96_architect_demolish.png', 'gui'),
    'gui_tinkers_bench': ('gui_97_tinkers_bench.png', 'gui'),
    'gui_core_reactor_formed': ('gui_98_core_reactor_formed.png', 'gui'),
    'gui_bank_formed': ('gui_99_bank_formed.png', 'gui'),
    'gui_spire_formed': ('gui_100_spire_formed.png', 'gui'),
    'gui_collider_formed': ('gui_102_collider_formed.png', 'gui'),
    'gui_grinder': ('gui_*_grinder_mk2.png', 'gui'),
    'gui_wireless_charger': ('gui_*_wireless_charger.png', 'gui'),
    'gui_replicator_formed': ('gui_92_replicator_formed.png', 'gui'),
    'gui_storage_terminal': ('gui_95_storage_terminal_full.png', 'gui'),
    'gui_item_pipe': ('gui_101_item_pipe.png', 'gui'),
}


def sips(*args):
    subprocess.run(['sips', *args], check=True, capture_output=True)


def size(path):
    out = subprocess.run(['sips', '-g', 'pixelWidth', '-g', 'pixelHeight', path], check=True, capture_output=True, text=True).stdout
    vals = [int(line.split(':')[1]) for line in out.splitlines() if 'pixel' in line]
    return vals[0], vals[1]


def convert(src, dst, kind):
    with tempfile.TemporaryDirectory() as tmp:
        work = os.path.join(tmp, 'work.png')
        sips('-s', 'format', 'png', src, '--out', work)
        w, h = size(work)
        if kind == 'gui':
            side = min(w, h) * 7 // 8
            sips('-c', str(side), str(side), work)
            sips('--resampleWidth', '1000', work)
        else:
            sips('--resampleWidth', '1600', work)
        for quality in (82, 75, 68, 60, 52, 45):
            sips('-s', 'format', 'jpeg', '-s', 'formatOptions', str(quality), work, '--out', dst)
            if os.path.getsize(dst) <= MAX_BYTES:
                break


def main():
    missing = []
    for name, (pattern, kind) in SHOTS.items():
        found = sorted(glob.glob(os.path.join(SRC, pattern)))
        if not found:
            missing.append(pattern)
            continue
        dst = os.path.join(OUT, name + '.jpg')
        convert(found[0], dst, kind)
        print(f'{name}.jpg  {os.path.getsize(dst) // 1000} KB')
    if missing:
        print('missing screenshots: ' + ', '.join(missing), file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
