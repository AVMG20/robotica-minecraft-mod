#!/usr/bin/env python3
"""Validates assets/robotica/sounds.json against the vanilla asset index.

Checks that every referenced vanilla sound file exists in the Minecraft asset index, that every "event" reference exists
in vanilla's sounds.json, that pitch/volume are sane, that every event has a subtitle key present in a lang fragment, and
that every event is registered in core/CoreSounds.java (and the other way round).

Usage: python3 scripts/check_sounds.py [path/to/asset-index.json]
The index defaults to the newest ~/.gradle/caches/neoformruntime/assets/indexes/*.json.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, 'src/main/resources/assets/robotica/sounds.json')
CORE_SOUNDS = os.path.join(ROOT, 'src/main/java/com/arno/robotica/core/CoreSounds.java')
LANG_GLOB = os.path.join(ROOT, 'src/main/fragments/*/assets/robotica/lang/en_us.json')
GRADLE_ASSETS = os.path.expanduser('~/.gradle/caches/neoformruntime/assets')


def find_index():
    if len(sys.argv) > 1:
        return sys.argv[1]
    candidates = glob.glob(os.path.join(GRADLE_ASSETS, 'indexes', '*.json'))
    if not candidates:
        sys.exit('no asset index found under ' + GRADLE_ASSETS)
    return max(candidates, key=os.path.getmtime)


def main():
    index_path = find_index()
    objects = json.load(open(index_path))['objects']
    files = {k[len('minecraft/sounds/'):-len('.ogg')] for k in objects
             if k.startswith('minecraft/sounds/') and k.endswith('.ogg')}

    vanilla_events = set()
    entry = objects.get('minecraft/sounds.json')
    if entry:
        h = entry['hash']
        p = os.path.join(os.path.dirname(os.path.dirname(index_path)), 'objects', h[:2], h)
        if os.path.exists(p):
            vanilla_events = set(json.load(open(p)).keys())
    if not vanilla_events:
        print('warning: vanilla sounds.json not found, event references are not checked')

    sounds = json.load(open(SOUNDS))
    lang = {}
    for f in glob.glob(LANG_GLOB):
        lang.update(json.load(open(f)))

    errors = []
    total = 0
    for name, event in sounds.items():
        sub = event.get('subtitle')
        if not sub:
            errors.append('%s: no subtitle' % name)
        elif sub not in lang:
            errors.append('%s: subtitle key %s missing in lang fragments' % (name, sub))
        entries = event.get('sounds', [])
        if not entries:
            errors.append('%s: no sounds' % name)
        for s in entries:
            total += 1
            obj = {'name': s} if isinstance(s, str) else s
            ref = obj['name']
            ns, _, path = ref.rpartition(':')
            ns = ns or 'minecraft'
            kind = obj.get('type', 'file')
            pitch = obj.get('pitch', 1.0)
            volume = obj.get('volume', 1.0)
            if not 0.5 <= pitch <= 2.0:
                errors.append('%s: %s pitch %s outside 0.5..2.0 (engine clamps)' % (name, ref, pitch))
            if not 0.0 < volume <= 1.0:
                errors.append('%s: %s volume %s outside 0..1' % (name, ref, volume))
            if kind == 'event':
                if ns == 'minecraft' and vanilla_events and path not in vanilla_events:
                    errors.append('%s: vanilla event %s does not exist' % (name, ref))
            elif ns == 'minecraft':
                if path not in files:
                    errors.append('%s: vanilla sound file %s does not exist' % (name, ref))
            elif ns == 'robotica':
                if not os.path.exists(os.path.join(ROOT, 'src/main/resources/assets/robotica/sounds', path + '.ogg')):
                    errors.append('%s: mod sound file %s does not exist' % (name, ref))
            else:
                errors.append('%s: unknown namespace in %s' % (name, ref))

    registered = set(re.findall(r'reg\("([a-z0-9_]+)"\)', open(CORE_SOUNDS).read()))
    for n in sorted(registered - set(sounds)):
        errors.append('%s: registered in CoreSounds but missing from sounds.json' % n)
    for n in sorted(set(sounds) - registered):
        errors.append('%s: in sounds.json but not registered in CoreSounds' % n)

    print('%d events, %d sound entries, index %s' % (len(sounds), total, os.path.basename(index_path)))
    for e in errors:
        print('ERROR', e)
    if errors:
        sys.exit(1)
    print('ok')


if __name__ == '__main__':
    main()
