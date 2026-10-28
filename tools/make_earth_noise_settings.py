"""Builds Earth's noise settings from vanilla's overworld ones.

Everything stays vanilla except three inputs: continentalness (stevespace:earth_continents) and
the climate (stevespace:earth_climate temperature/humidity). All three read their seed from the
same vanilla noise so they describe the same planet. Run after a Minecraft update.
Source: vanilla data extracted to ~/.cache/stevespace-ref/vanilla/data/minecraft/worldgen
"""
import json
import os
import shutil

SRC = os.path.expanduser('~/.cache/stevespace-ref/vanilla/data/minecraft/worldgen')
DST = os.path.join(os.path.dirname(__file__), '../src/main/resources/data/stevespace/worldgen')


def rewrite(text):
    return text.replace('"minecraft:overworld/', '"stevespace:earth/')


df_src = os.path.join(SRC, 'density_function/overworld')
df_dst = os.path.join(DST, 'density_function/earth')
if os.path.isdir(df_dst):
    shutil.rmtree(df_dst)
for root, _, files in os.walk(df_src):
    for f in files:
        rel = os.path.relpath(os.path.join(root, f), df_src)
        out = os.path.join(df_dst, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(os.path.join(root, f)) as fh:
            text = rewrite(fh.read())
        with open(out, 'w') as fh:
            fh.write(text)

# Continents: our planet-scale continents (cached per column like vanilla's).
with open(os.path.join(df_dst, 'continents.json'), 'w') as fh:
    json.dump({"type": "minecraft:flat_cache",
               "argument": {"type": "stevespace:earth_continents", "seed_noise": "minecraft:continentalness"}},
              fh, indent=2)

with open(os.path.join(SRC, 'noise_settings/overworld.json')) as fh:
    settings = json.loads(rewrite(fh.read()))
router = settings['noise_router']
router['temperature'] = {"type": "stevespace:earth_climate", "kind": "temperature", "seed_noise": "minecraft:continentalness"}
router['vegetation'] = {"type": "stevespace:earth_climate", "kind": "humidity", "seed_noise": "minecraft:continentalness"}
os.makedirs(os.path.join(DST, 'noise_settings'), exist_ok=True)
with open(os.path.join(DST, 'noise_settings/earth.json'), 'w') as fh:
    json.dump(settings, fh, indent=2)
print('wrote', df_dst, 'and noise_settings/earth.json')
