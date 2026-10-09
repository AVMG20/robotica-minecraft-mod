"""Writes the gear module's block models and blockstates: the Spark Lamp.
Run: python3 scripts/data/gear_models.py
The lamp model is empty (the wisp is drawn in code); the blockstate keeps one variant per facing."""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from pixelart import ASSETS  # noqa: E402


def spark_lamp():
    """No geometry, only the particle texture: the wisp is drawn client side (gear.client.SparkWisps), so it can float,
    face the camera and glow additively. Textures: scripts/textures/gear.py (spark_wisp, spark_wisp_glow)."""
    return {'textures': {'particle': 'robotica:block/spark_lamp_core'}, 'elements': []}


def spark_lamp_state():
    return {'variants': {f'facing={f}': {'model': 'robotica:block/spark_lamp'} for f in ('up', 'down', 'north', 'east', 'south', 'west')}}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def main():
    write(ASSETS / 'models/block/spark_lamp.json', spark_lamp())
    write(ASSETS / 'blockstates/spark_lamp.json', spark_lamp_state())
    print('gear models written')


if __name__ == '__main__':
    main()
