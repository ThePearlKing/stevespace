"""Generates block/item JSON for Steve's Space Program: blockstates, models, item models,
loot tables, recipes, tags and English names. Run after adding or renaming blocks.

    python3 tools/resources.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), '../src/main/resources')
ASSETS = os.path.join(ROOT, 'assets/stevespace')
DATA = os.path.join(ROOT, 'data/stevespace')
NS = 'stevespace'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as fh:
        json.dump(obj, fh, indent=2)
        fh.write('\n')


def tex(name):
    return f'{NS}:block/{name}'


# ---------------------------------------------------------------------------------------------
# Block definitions: id -> (english name, kind, textures/params)
# kinds: cube, column, translucent_cube, horizontal (front/side/top), engine, seat, pad
# ---------------------------------------------------------------------------------------------
BLOCKS = {
    'hull_white':        ('White Hull Plating', 'cube', {'all': 'hull_white'}),
    'hull_grey':         ('Grey Hull Plating', 'cube', {'all': 'hull_grey'}),
    'hull_dark':         ('Dark Hull Plating', 'cube', {'all': 'hull_dark'}),
    'hull_white_band':   ('Banded Hull Plating', 'cube', {'all': 'hull_white_band'}),
    'heat_shield':       ('Heat Shield Tiles', 'cube', {'all': 'heat_shield'}),
    'insulation_foil':   ('Insulation Foil', 'cube', {'all': 'insulation_foil'}),
    'aluminium_frame':   ('Aluminium Frame', 'cube', {'all': 'aluminium_frame'}),
    'porthole':          ('Porthole', 'translucent_cube', {'all': 'porthole'}),
    'spacecraft_window': ('Spacecraft Window', 'connected_window', {'glass': 'window_sheet_glass', 'frame': 'window_frame'}),
    'window_pane':       ('Spacecraft Window Pane', 'pane', {'pane': 'window_pane', 'edge': 'window_pane_edge'}),
    'propellant_tank':   ('Propellant Tank', 'column', {'side': 'propellant_tank_side', 'end': 'propellant_tank_top'}),
    'rocket_engine':     ('Rocket Engine', 'engine', {}),
    'vacuum_engine':     ('Vacuum Engine', 'engine', {'body': 'vacuum_engine_body', 'bell': 'vacuum_engine_bell'}),
    'nuclear_engine':    ('Nuclear Thermal Engine', 'engine', {'body': 'nuclear_engine_body'}),
    'carbon_hull':       ('Carbon Fibre Hull', 'cube', {'all': 'carbon_hull'}),
    'pilot_seat':        ('Pilot Seat', 'seat', {'fabric': 'seat_fabric'}),
    'passenger_seat':    ('Passenger Seat', 'seat', {'fabric': 'seat_fabric_grey'}),
    'flight_computer':   ('Flight Computer', 'horizontal', {'front': 'flight_computer_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'radio_rack':        ('Radio Rack', 'horizontal', {'front': 'radio_rack_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'remote_receiver':   ('Remote Control Receiver', 'horizontal', {'front': 'remote_receiver_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'remote_console':    ('Remote Control Console', 'horizontal', {'front': 'remote_console_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'rc_function':       ('Remote Function Relay', 'relay', {'side': 'instrument_side', 'top': 'instrument_top'}),
    'nuclear_device':    ('Nuclear Device', 'device', {'side': 'nuclear_device_side', 'top': 'nuclear_device_top'}),
    'gravity_generator': ('Gravity Generator', 'horizontal', {'front': 'gravity_generator_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'launch_pad':        ('Launch Pad', 'cube_bottom_top', {'top': 'launch_pad', 'side': 'launch_pad_side', 'bottom': 'launch_pad_side'}),
    'fuel_loader':       ('Fuel Loader', 'horizontal', {'front': 'fuel_loader_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'docking_port':      ('Docking Port', 'docking_port', {}),
    'rcs_thruster':      ('RCS Thruster', 'rcs', {}),
    'telescope':         ('Telescope', 'telescope', {}),
    'flight_display':    ('Flight Display', 'horizontal', {'front': 'flight_display_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'oxygen_generator':  ('Oxygen Generator', 'horizontal', {'front': 'oxygen_generator_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'power_pipe':        ('Power Pipe', 'pipe', {'pipe': 'power_pipe'}),
    'coal_generator':    ('Coal Generator', 'machine', {'front': 'coal_generator_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'small_battery':     ('Small Battery', 'horizontal', {'front': 'small_battery_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'solar_panel':       ('Solar Panel', 'solar_panel', {'cell': 'solar_cell', 'frame': 'solar_frame'}),
    'solar_controller':  ('Solar Controller', 'horizontal', {'front': 'solar_controller_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'rtg':               ('Radioisotope Generator', 'column', {'side': 'rtg_side', 'end': 'rtg_top'}),
    'battery':           ('Battery Bank', 'horizontal', {'front': 'battery_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'large_battery':     ('Large Battery', 'horizontal', {'front': 'large_battery_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'electric_furnace':  ('Electric Furnace', 'machine', {'front': 'electric_furnace_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'crusher':           ('Crusher', 'machine', {'front': 'crusher_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'creative_power':    ('Creative Power Source', 'horizontal', {'front': 'creative_power_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'magnetometer':      ('Magnetometer', 'horizontal', {'front': 'magnetometer_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'plasma_receiver':   ('Plasma Wave Receiver', 'horizontal', {'front': 'plasma_receiver_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'flag':              ('Flag', 'flag', {'cloth': 'flag_cloth', 'pole': 'flag_pole'}),
    'mission_control':   ('Mission Control Console', 'horizontal', {'front': 'mission_control_front', 'side': 'instrument_side', 'top': 'instrument_top'}),
    'regolith':          ('Regolith', 'cube', {'all': 'regolith'}),
    'mare_regolith':     ('Mare Regolith', 'cube', {'all': 'mare_regolith'}),
    'anorthosite':       ('Anorthosite', 'cube', {'all': 'anorthosite'}),
    'mars_regolith':     ('Martian Regolith', 'cube', {'all': 'mars_regolith'}),
    'mars_rock':         ('Martian Rock', 'cube', {'all': 'mars_rock'}),
    'basalt_sand':       ('Basalt Sand', 'cube', {'all': 'basalt_sand'}),
}

# Natural ground: dug with a shovel, never part of a vessel.
SHOVEL = ['regolith', 'mare_regolith', 'mars_regolith', 'basalt_sand']
TERRAIN = ['regolith', 'mare_regolith', 'anorthosite', 'mars_regolith', 'mars_rock', 'basalt_sand']

# Blocks that never become part of a vessel (in addition to natural terrain).
NOT_VESSEL_OWN = ['launch_pad', 'fuel_loader']


def blockstate_and_model(bid, kind, t):
    model = f'{NS}:block/{bid}'
    if kind == 'cube':
        write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(t['all'])}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {'': {'model': model}}})
    elif kind == 'cube_bottom_top':
        write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/cube_bottom_top',
                                                   'textures': {k: tex(v) for k, v in t.items()}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {'': {'model': model}}})
    elif kind == 'translucent_cube':
        write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:translucent',
                                                   'textures': {'all': tex(t['all'])}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {'': {'model': model}}})
    elif kind == 'pane':
        # Like a glass pane: a post, plus a side toward each neighbour it joins.
        parts = {'post': 'template_glass_pane_post', 'side': 'template_glass_pane_side', 'side_alt': 'template_glass_pane_side_alt',
                 'noside': 'template_glass_pane_noside', 'noside_alt': 'template_glass_pane_noside_alt'}
        for part, parent in parts.items():
            write(f'{ASSETS}/models/block/{bid}_{part}.json', {'parent': f'minecraft:block/{parent}', 'render_type': 'minecraft:translucent',
                                                              'textures': {'pane': tex(t['pane']), 'edge': tex(t['edge'])}})
        m = lambda part: f'{NS}:block/{bid}_{part}'
        write(f'{ASSETS}/blockstates/{bid}.json', {'multipart': [
            {'apply': {'model': m('post')}},
            {'when': {'north': 'true'}, 'apply': {'model': m('side')}},
            {'when': {'east': 'true'}, 'apply': {'model': m('side'), 'y': 90}},
            {'when': {'south': 'true'}, 'apply': {'model': m('side_alt')}},
            {'when': {'west': 'true'}, 'apply': {'model': m('side_alt'), 'y': 90}},
            {'when': {'north': 'false'}, 'apply': {'model': m('noside')}},
            {'when': {'east': 'false'}, 'apply': {'model': m('noside_alt')}},
            {'when': {'south': 'false'}, 'apply': {'model': m('noside_alt'), 'y': 90}},
            {'when': {'west': 'false'}, 'apply': {'model': m('noside'), 'y': 270}}]})
        write(f'{ASSETS}/models/item/{bid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': tex(t['pane'])}})
        return None
    elif kind == 'column':
        write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/cube_column',
                                                   'textures': {'side': tex(t['side']), 'end': tex(t['end'])}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {'': {'model': model}}})
    elif kind == 'horizontal':
        write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/orientable',
                                                   'textures': {'front': tex(t['front']), 'side': tex(t['side']), 'top': tex(t['top'])}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f}': {'model': model, **({'y': r} if r else {})}
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    elif kind == 'machine':
        # Like 'horizontal', with a lit front (<front>_on) while it runs.
        for lit in (False, True):
            front = t['front'] + ('_on' if lit else '')
            write(f'{ASSETS}/models/block/{bid}{"_on" if lit else ""}.json', {'parent': 'minecraft:block/orientable',
                  'textures': {'front': tex(front), 'side': tex(t['side']), 'top': tex(t['top'])}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f},lit={str(lit).lower()}': {'model': model + ('_on' if lit else ''), **({'y': r} if r else {})}
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270)) for lit in (False, True)}})
    elif kind == 'device':
        # Facing, the yield dial (0-2) and armed: the arming panel shows both.
        for y in range(3):
            for armed in (False, True):
                name = f'{bid}_front_{y}' + ('_armed' if armed else '')
                write(f'{ASSETS}/models/block/{bid}_{y}' + ('_armed' if armed else '') + '.json', {'parent': 'minecraft:block/orientable',
                      'textures': {'front': tex(name), 'side': tex(t['side']), 'top': tex(t['top'])}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'armed={str(a).lower()},facing={f},yield={y}': {'model': f'{NS}:block/{bid}_{y}' + ('_armed' if a else ''), **({'y': r} if r else {})}
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270)) for y in range(3) for a in (False, True)}})
        return f'{NS}:block/{bid}_1'
    elif kind == 'relay':
        # Facing, function 1-5 and powered: a front showing its number, lit while it pulses.
        for n in range(1, 6):
            for lit in (False, True):
                name = f'{bid}_{n}' + ('_on' if lit else '')
                write(f'{ASSETS}/models/block/{name}.json', {'parent': 'minecraft:block/orientable',
                      'textures': {'front': tex(name), 'side': tex(t['side']), 'top': tex(t['top'])}})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f},function={n},powered={str(lit).lower()}': {'model': f'{NS}:block/{bid}_{n}' + ('_on' if lit else ''),
                                                                    **({'y': r} if r else {})}
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270)) for n in range(1, 6) for lit in (False, True)}})
        return f'{NS}:block/{bid}_1'
    elif kind == 'pipe':
        # A core with an arm toward each joined side (PipeBlock, apothem 3/16).
        tx = {'particle': tex(t['pipe']), 'pipe': tex(t['pipe'])}
        all_faces = lambda: {d: {'texture': '#pipe'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
        write(f'{ASSETS}/models/block/{bid}_core.json', {'parent': 'minecraft:block/block', 'textures': tx,
              'elements': [{'from': [5, 5, 5], 'to': [11, 11, 11], 'faces': all_faces()}]})
        arm = {'from': [5, 5, 0], 'to': [11, 11, 5], 'faces': {d: {'texture': '#pipe', 'uv': [0, 5, 5, 11]} for d in ('east', 'west', 'up', 'down')}}
        arm['faces']['north'] = {'texture': '#pipe', 'uv': [5, 5, 11, 11]}
        write(f'{ASSETS}/models/block/{bid}_arm.json', {'parent': 'minecraft:block/block', 'textures': tx, 'elements': [arm]})
        core, armm = f'{NS}:block/{bid}_core', f'{NS}:block/{bid}_arm'
        rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
        write(f'{ASSETS}/blockstates/{bid}.json', {'multipart': [{'apply': {'model': core}}] + [
            {'when': {d: 'true'}, 'apply': {'model': armm, **r}} for d, r in rot.items()]})
        write(f'{ASSETS}/models/block/{bid}_inventory.json', {'parent': 'minecraft:block/block', 'textures': tx,
              'elements': [{'from': [5, 5, 0], 'to': [11, 11, 16], 'faces': all_faces()}]})
        return f'{NS}:block/{bid}_inventory'
    elif kind == 'connected_window':
        # Glass that joins its neighbours: a frame strip runs along an edge only where neither
        # side of that edge has another window, so a wall of them is framed round the outside.
        bounds = {'north': ('z', 0, 2), 'south': ('z', 14, 16), 'west': ('x', 0, 2), 'east': ('x', 14, 16),
                  'down': ('y', 0, 2), 'up': ('y', 14, 16)}
        axis_of = {d: b[0] for d, b in bounds.items()}

        # Each glass face is a flat sheet set 0.5 px into the block, so it never shares a plane with
        # the frame strips (coplanar translucent faces z-fight). Faces between windows are culled.
        def glass_face(d):
            ax, x0, x1 = bounds[d]
            k = 'xyz'.index(ax)
            lo, hi = [0, 0, 0], [16, 16, 16]
            lo[k] = hi[k] = 0.5 if x0 == 0 else 15.5
            return {'from': lo, 'to': hi, 'faces': {d: {'texture': '#glass', 'cullface': d}}}

        glass = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent',
                 'textures': {'particle': tex(t['frame']), 'glass': tex(t['glass'])},
                 'elements': [glass_face(d) for d in bounds]}
        write(f'{ASSETS}/models/block/{bid}.json', glass)
        edges = []
        dirs = list(bounds)
        for i, a in enumerate(dirs):
            for b in dirs[i + 1:]:
                if axis_of[a] != axis_of[b]:
                    edges.append((a, b))
        parts = [{'apply': {'model': model}}]
        inventory = [dict(e) for e in glass['elements']]
        for a, b in edges:
            lo, hi = [0, 0, 0], [16, 16, 16]
            for d in (a, b):
                ax, x0, x1 = bounds[d]
                k = 'xyz'.index(ax)
                lo[k], hi[k] = x0, x1
            element = {'from': lo, 'to': hi, 'faces': {d: {'texture': '#frame'} for d in bounds}}
            name = f'{bid}_edge_{a}_{b}'
            write(f'{ASSETS}/models/block/{name}.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent',
                                                        'textures': {'particle': tex(t['frame']), 'frame': tex(t['frame'])},
                                                        'elements': [element]})
            parts.append({'when': {a: 'false', b: 'false'}, 'apply': {'model': f'{NS}:block/{name}'}})
            inventory.append(element)
        write(f'{ASSETS}/blockstates/{bid}.json', {'multipart': parts})
        write(f'{ASSETS}/models/block/{bid}_inventory.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent',
              'textures': {'particle': tex(t['frame']), 'glass': tex(t['glass']), 'frame': tex(t['frame'])}, 'elements': inventory})
        return f'{NS}:block/{bid}_inventory'
    elif kind == 'solar_panel':
        # A thin plate at mid height; panels side by side make one array.
        faces = {d: {'texture': '#frame', 'cullface': d} for d in ('north', 'south', 'east', 'west')}
        faces['up'] = {'texture': '#cell'}
        faces['down'] = {'texture': '#cell'}  # double sided
        write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/block',
              'textures': {'particle': tex(t['cell']), 'cell': tex(t['cell']), 'frame': tex(t['frame'])},
              'elements': [{'from': [0, 7, 0], 'to': [16, 9, 16], 'faces': faces}]})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {'': {'model': model}}})
    elif kind == 'flag':
        # A pole with the cloth flying from its top, out to one side (the cloth is tinted by the dye).
        def faces(texture, uvs, tint=None):
            out = {}
            for d in ('north', 'south', 'east', 'west', 'up', 'down'):
                out[d] = {'texture': texture, 'uv': uvs[d]}
                if tint is not None:
                    out[d]['tintindex'] = tint
            return out
        pole_uv = {'north': [7, 0, 8, 16], 'south': [7, 0, 8, 16], 'east': [7, 0, 8, 16], 'west': [7, 0, 8, 16],
                   'up': [7, 7, 8, 8], 'down': [7, 7, 8, 8]}
        cloth_uv = {'north': [0, 3, 16, 13], 'south': [16, 3, 0, 13], 'east': [0, 3, 1, 13], 'west': [0, 3, 1, 13],
                    'up': [0, 3, 16, 4], 'down': [0, 12, 16, 13]}
        write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/block',
              'textures': {'particle': tex(t['cloth']), 'cloth': tex(t['cloth']), 'pole': tex(t['pole'])},
              'elements': [
                  {'from': [7.5, 0, 7.5], 'to': [8.5, 29, 8.5], 'faces': faces('#pole', pole_uv)},
                  {'from': [8.5, 18, 7.75], 'to': [24.5, 28, 8.25], 'faces': faces('#cloth', cloth_uv, 1)},
              ]})
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f}': {'model': model, **({'y': r} if r else {})}
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    elif kind == 'engine':
        write(f'{ASSETS}/models/block/{bid}.json', engine_model(t.get('body', 'engine_body'), t.get('bell', 'engine_bell')))
        # Model is built pointing down (nozzle at the bottom); rotate for the other facings.
        # x=270 turns a downward nozzle to point north; y then turns it around the vertical axis.
        rotations = {'down': {}, 'up': {'x': 180}, 'north': {'x': 270}, 'south': {'x': 270, 'y': 180},
                     'east': {'x': 270, 'y': 90}, 'west': {'x': 270, 'y': 270}}
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f}': {'model': model, **r} for f, r in rotations.items()}})
    elif kind == 'docking_port':
        write(f'{ASSETS}/models/block/{bid}.json', docking_port_model())
        # Sharing power: the same with a red collar.
        write(f'{ASSETS}/models/block/{bid}_sharing.json', docking_port_model('docking_ring_red'))
        rotations = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'south': {'x': 90, 'y': 180},
                     'east': {'x': 90, 'y': 90}, 'west': {'x': 90, 'y': 270}}
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f},sharing={s}': {'model': model + ('_sharing' if s == 'true' else ''), **r}
            for f, r in rotations.items() for s in ('false', 'true')}})
    elif kind == 'rcs':
        write(f'{ASSETS}/models/block/{bid}.json', rcs_model())
        rotations = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'south': {'x': 90, 'y': 180},
                     'east': {'x': 90, 'y': 90}, 'west': {'x': 90, 'y': 270}}
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f}': {'model': model, **r} for f, r in rotations.items()}})
    elif kind == 'telescope':
        write(f'{ASSETS}/models/block/{bid}.json', telescope_model())
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f}': {'model': model, **({'y': r} if r else {})}
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    elif kind == 'seat':
        write(f'{ASSETS}/models/block/{bid}.json', seat_model(t['fabric']))
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {
            f'facing={f}': {'model': model, **({'y': r} if r else {})}
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    return model


def box(frm, to, faces, texture='#body'):
    return {'from': frm, 'to': to, 'faces': {f: {'texture': texture} for f in faces}}


def engine_model(body='engine_body', bell='engine_bell'):
    """Engine mount block on top, stepped bell nozzle widening toward the bottom."""
    elements = [
        # mount / turbopump housing
        {'from': [3, 10, 3], 'to': [13, 16, 13], 'faces': {
            'up': {'texture': '#top'}, 'north': {'texture': '#body'}, 'south': {'texture': '#body'},
            'east': {'texture': '#body'}, 'west': {'texture': '#body'}, 'down': {'texture': '#top'}}},
    ]
    steps = [(6, 8, 10), (5, 6, 8), (4, 4, 6), (3, 2, 4), (2, 0, 2)]  # (inset, y0, y1)
    for inset, y0, y1 in steps:
        lo, hi = inset, 16 - inset
        elements.append({'from': [lo, y0, lo], 'to': [hi, y1, hi], 'faces': {
            'north': {'texture': '#bell', 'uv': [lo, 16 - y1, hi, 16 - y0]},
            'south': {'texture': '#bell', 'uv': [lo, 16 - y1, hi, 16 - y0]},
            'east': {'texture': '#bell', 'uv': [lo, 16 - y1, hi, 16 - y0]},
            'west': {'texture': '#bell', 'uv': [lo, 16 - y1, hi, 16 - y0]},
            'up': {'texture': '#bell', 'uv': [lo, lo, hi, hi]},
            'down': {'texture': '#inside', 'uv': [lo, lo, hi, hi]}}})
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
            'textures': {'particle': tex(body), 'body': tex(body), 'top': tex('propellant_tank_top'),
                         'bell': tex(bell), 'inside': tex('engine_bell_inside')},
            'elements': elements}


def docking_port_model(collar='hull_dark'):
    """Pointing up: a hull block with a raised collar round a hatch on top."""
    def box(frm, to, faces):
        return {'from': frm, 'to': to, 'faces': faces}
    all6 = ('north', 'south', 'east', 'west', 'up', 'down')
    hull = {f: {'texture': '#hull'} for f in all6}
    hull['up'] = {'texture': '#hatch'}
    ring = {f: {'texture': '#ring'} for f in all6}
    return {'parent': 'minecraft:block/block',
            'textures': {'particle': tex('hull_grey'), 'hull': tex('hull_grey'), 'ring': tex(collar),
                         'hatch': tex('docking_hatch')},
            'elements': [
                box([0, 0, 0], [16, 12, 16], hull),
                box([1, 12, 1], [15, 16, 3], ring),
                box([1, 12, 13], [15, 16, 15], ring),
                box([1, 12, 3], [3, 16, 13], ring),
                box([13, 12, 3], [15, 16, 13], ring),
            ]}


def rcs_model():
    """Pointing up: a plate on the hull, a small white housing, and five little nozzles (four
    round the sides, one on top) so it can push any way."""
    def uv(face, frm, to):
        x0, y0, z0 = frm
        x1, y1, z1 = to
        if face in ('north', 'south'):
            return [x0, 16 - y1, x1, 16 - y0]
        if face in ('east', 'west'):
            return [z0, 16 - y1, z1, 16 - y0]
        return [x0, z0, x1, z1]

    def box(frm, to, texture):
        return {'from': frm, 'to': to, 'faces': {f: {'texture': texture, 'uv': uv(f, frm, to)}
                                                for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
            'textures': {'particle': tex('hull_white'), 'plate': tex('hull_dark'), 'body': tex('hull_white'),
                         'nozzle': tex('engine_bell')},
            'elements': [
                box([4, 0, 4], [12, 2, 12], '#plate'),
                box([5.5, 2, 5.5], [10.5, 8, 10.5], '#body'),
                box([10.5, 5, 7], [13, 7, 9], '#nozzle'),
                box([3, 5, 7], [5.5, 7, 9], '#nozzle'),
                box([7, 5, 3], [9, 7, 5.5], '#nozzle'),
                box([7, 5, 10.5], [9, 7, 13], '#nozzle'),
                box([7, 8, 7], [9, 10, 9], '#nozzle'),
            ]}


def telescope_model():
    """
    A white refractor on a dark alt-az mount and tripod. The tube points south, tilted up; the
    eyepiece end faces north, toward whoever placed it. Every part touches or overlaps the next.
    """
    def uv(face, frm, to):
        # Explicit UVs kept inside the texture (parts reaching past the block would otherwise
        # sample their neighbours on the atlas).
        x0, y0, z0 = frm
        x1, y1, z1 = to
        if face in ('north', 'south'):
            u, v = (x0, x1), (16 - y1, 16 - y0)
        elif face in ('east', 'west'):
            u, v = (z0, z1), (16 - y1, 16 - y0)
        else:
            u, v = (x0, x1), (z0, z1)
        length_u, length_v = min(16, u[1] - u[0]), min(16, v[1] - v[0])
        u0 = min(max(u[0], 0), 16 - length_u)
        v0 = min(max(v[0], 0), 16 - length_v)
        return [round(u0, 3), round(v0, 3), round(u0 + length_u, 3), round(v0 + length_v, 3)]

    def box(frm, to, texture, rotation=None, faces=None):
        e = {'from': frm, 'to': to, 'faces': {f: {'texture': texture, 'uv': uv(f, frm, to)} for f in
                                              ('north', 'south', 'east', 'west', 'up', 'down')}}
        for face, tex_ in (faces or {}).items():
            e['faces'][face] = {'texture': tex_, 'uv': [0, 0, 16, 16]}
        if rotation:
            e['rotation'] = rotation
        return e

    hub_y = 9.5
    leg_len = round(hub_y / 0.92388, 3)          # 22.5 degree legs reach the floor exactly
    def leg(axis, angle):
        return box([7.5, hub_y - leg_len, 7.5], [8.5, hub_y, 8.5], '#metal',
                   {'origin': [8, hub_y, 8], 'axis': axis, 'angle': angle})
    tube = {'origin': [8, 13.5, 8], 'axis': 'x', 'angle': -22.5}
    return {'parent': 'minecraft:block/block',
            'textures': {'particle': tex('telescope_tube'), 'tube': tex('telescope_tube'),
                         'metal': tex('telescope_metal'), 'lens': tex('telescope_lens')},
            'elements': [
                # Tripod: three legs from one hub, feet on the floor.
                leg('x', 22.5),                                          # north
                leg('z', 22.5),                                          # east/west pair
                leg('z', -22.5),
                box([7.6, 3.6, 5.6], [8.4, 4.2, 8.4], '#metal'),         # spreader bars
                box([5.6, 3.6, 7.6], [10.4, 4.2, 8.4], '#metal'),
                box([6.75, 8.75, 6.75], [9.25, 10.25, 9.25], '#metal'),  # hub
                # Mount: post, fork head the tube sits in.
                box([7.25, 10.25, 7.25], [8.75, 11.75, 8.75], '#metal'),
                box([6.5, 11.5, 6.75], [9.5, 12.5, 9.25], '#metal'),
                # Optical tube, objective cell and dew shield, focuser, eyepiece, finder.
                box([6.25, 11.75, 1.5], [9.75, 15.25, 13.5], '#tube', tube),
                box([5.95, 11.45, 13.25], [10.05, 15.55, 16.25], '#tube', tube, {'south': '#lens'}),
                box([6.9, 12.4, -0.5], [9.1, 14.6, 1.75], '#metal', tube),
                box([7.35, 12.85, -2.5], [8.65, 14.15, -0.25], '#metal', tube),
                box([7.55, 15.0, 5.0], [8.45, 15.9, 10.0], '#metal', tube),
            ]}


def seat_model(fabric):
    """Seat facing north: low base, cushion, tall backrest at the south side (explicit UVs, since
    the backrest reaches above the block)."""
    def uv(face, frm, to):
        x0, y0, z0 = frm
        x1, y1, z1 = to
        if face in ('north', 'south'):
            u, v = (x0, x1), (16 - y1, 16 - y0)
        elif face in ('east', 'west'):
            u, v = (z0, z1), (16 - y1, 16 - y0)
        else:
            u, v = (x0, x1), (z0, z1)
        lu, lv = min(16, u[1] - u[0]), min(16, v[1] - v[0])
        u0 = min(max(u[0], 0), 16 - lu)
        v0 = min(max(v[0], 0), 16 - lv)
        return [u0, v0, u0 + lu, v0 + lv]

    def box(frm, to, texture):
        return {'from': frm, 'to': to, 'faces': {f: {'texture': texture, 'uv': uv(f, frm, to)} for f in
                                                ('north', 'south', 'east', 'west', 'up', 'down')}}
    return {'parent': 'minecraft:block/block',
            'textures': {'particle': tex(fabric), 'fabric': tex(fabric), 'frame': tex('seat_frame')},
            'elements': [
                box([3, 0, 3], [13, 4, 13], '#frame'),
                box([2, 4, 2], [14, 7, 14], '#fabric'),
                box([2, 7, 12], [14, 19, 15], '#fabric'),
                box([1, 7, 4], [2, 10, 12], '#frame'),
                box([14, 7, 4], [15, 10, 12], '#frame'),
            ]}


# ---------------------------------------------------------------------------------------------
# Recipes (survival). Kept close to what the real part would be made of.
# ---------------------------------------------------------------------------------------------
def shaped(result, count, pattern, key):
    return {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': pattern,
            'key': {k: {'item': v} for k, v in key.items()}, 'result': {'id': f'{NS}:{result}', 'count': count}}


def shapeless(result, count, items):
    return {'type': 'minecraft:crafting_shapeless', 'category': 'building',
            'ingredients': [{'item': i} for i in items], 'result': {'id': f'{NS}:{result}', 'count': count}}


RECIPES = {
    'hull_white': shaped('hull_white', 8, ['CCC', 'CIC', 'CCC'], {'C': 'minecraft:white_concrete', 'I': 'minecraft:iron_ingot'}),
    'hull_grey': shaped('hull_grey', 8, ['CCC', 'CIC', 'CCC'], {'C': 'minecraft:light_gray_concrete', 'I': 'minecraft:iron_ingot'}),
    'hull_dark': shaped('hull_dark', 8, ['CCC', 'CIC', 'CCC'], {'C': 'minecraft:gray_concrete', 'I': 'minecraft:iron_ingot'}),
    'hull_white_band': shapeless('hull_white_band', 2, [f'{NS}:hull_white', f'{NS}:hull_white', 'minecraft:black_dye']),
    'heat_shield': shaped('heat_shield', 4, ['BB', 'BB'], {'B': 'minecraft:polished_blackstone_bricks'}),
    'insulation_foil': shapeless('insulation_foil', 4, ['minecraft:gold_ingot', 'minecraft:paper', 'minecraft:paper', 'minecraft:paper']),
    'aluminium_frame': shaped('aluminium_frame', 4, ['IBI', 'B B', 'IBI'], {'I': 'minecraft:iron_ingot', 'B': 'minecraft:iron_bars'}),
    'porthole': shapeless('porthole', 1, [f'{NS}:hull_white', 'minecraft:glass_pane']),
    'propellant_tank': shaped('propellant_tank', 1, ['III', 'IBI', 'III'], {'I': 'minecraft:iron_ingot', 'B': 'minecraft:bucket'}),
    'rocket_engine': shaped('rocket_engine', 1, [' C ', 'IFI', 'I I'], {'C': 'minecraft:copper_block', 'I': 'minecraft:iron_ingot',
                                                                      'F': 'minecraft:blast_furnace'}),
    'passenger_seat': shaped('passenger_seat', 1, ['W  ', 'WWW', 'I I'], {'W': 'minecraft:light_gray_wool', 'I': 'minecraft:iron_ingot'}),
    'pilot_seat': shaped('pilot_seat', 1, ['W  ', 'WWW', 'I I'], {'W': 'minecraft:gray_wool', 'I': 'minecraft:iron_ingot'}),
    'flight_computer': shaped('flight_computer', 1, ['IGI', 'RCR', 'III'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane',
                                                                          'R': 'minecraft:redstone', 'C': 'minecraft:comparator'}),
    'gravity_generator': shaped('gravity_generator', 1, ['ICI', 'HEH', 'IRI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot',
                                                                             'H': f'{NS}:helium_3', 'E': 'minecraft:ender_pearl',
                                                                             'R': 'minecraft:redstone_block'}),
    'vacuum_engine': shaped('vacuum_engine', 1, ['TET', 'XTX'], {'T': f'{NS}:titanium', 'E': f'{NS}:rocket_engine', 'X': f'{NS}:tellurium'}),
    'nuclear_engine': shaped('nuclear_engine', 1, ['UMU', 'TET', 'T T'], {'U': f'{NS}:uraninite', 'M': f'{NS}:magnetite',
                                                                        'T': f'{NS}:titanium', 'E': f'{NS}:rocket_engine'}),
    'carbon_hull': shaped('carbon_hull', 8, ['FFF', 'F F', 'FFF'], {'F': f'{NS}:carbon_fibre'}),
    'heat_shielded_space_suit': shaped('heat_shielded_space_suit', 1, ['TGT', 'GSG', 'TGT'], {'T': f'{NS}:titanium', 'G': 'minecraft:gold_ingot',
                                                                                          'S': f'{NS}:space_suit'}),
    'insulated_space_suit': shaped('insulated_space_suit', 1, ['AWA', 'WSW', 'AWA'], {'A': f'{NS}:aerogel', 'W': 'minecraft:white_wool',
                                                                                  'S': f'{NS}:space_suit'}),
    'radiation_shielded_space_suit': shaped('radiation_shielded_space_suit', 1, ['MIM', 'ISI', 'MIM'], {'M': f'{NS}:magnetite',
                                                                                                   'I': 'minecraft:iron_ingot',
                                                                                                   'S': f'{NS}:space_suit'}),
    'carbon_fibre': shaped('carbon_fibre', 2, ['CSC', 'SCS', 'CSC'], {'C': f'{NS}:carbon', 'S': 'minecraft:string'}),
    'radio_rack': shaped('radio_rack', 1, ['ILI', 'CRC', 'III'], {'I': 'minecraft:iron_ingot', 'L': 'minecraft:lightning_rod',
                                                                'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone'}),
    'spacecraft_window': shaped('spacecraft_window', 4, ['IGI', 'GGG', 'IGI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass'}),
    'window_pane': shaped('window_pane', 16, ['WWW', 'WWW'], {'W': f'{NS}:spacecraft_window'}),
    'launch_pad': shaped('launch_pad', 4, ['SS', 'SS'], {'S': 'minecraft:smooth_stone'}),
    'mission_control': shaped('mission_control', 1, ['GGG', 'RCR', 'III'], {'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone',
                                                                         'C': 'minecraft:compass', 'I': 'minecraft:iron_ingot'}),
    'flight_display': shaped('flight_display', 1, ['III', 'GRG', 'III'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane',
                                                                         'R': 'minecraft:redstone'}),
    'oxygen_generator': shaped('oxygen_generator', 1, ['IGI', 'FBF', 'IRI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane',
                                                                             'F': 'minecraft:iron_bars', 'B': 'minecraft:bucket',
                                                                             'R': 'minecraft:redstone'}),
    'solar_panel': shaped('solar_panel', 4, ['GGG', 'LLL', 'III'], {'G': 'minecraft:glass_pane', 'L': 'minecraft:lapis_lazuli',
                                                                  'I': 'minecraft:iron_ingot'}),
    'solar_controller': shaped('solar_controller', 1, ['IRI', 'CQC', 'III'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone',
                                                                          'C': 'minecraft:copper_ingot', 'Q': 'minecraft:comparator'}),
    'rtg': shaped('rtg', 1, ['IUI', 'BTB', 'IUI'], {'I': 'minecraft:iron_ingot', 'U': f'{NS}:uraninite', 'B': 'minecraft:iron_bars',
                                                  'T': f'{NS}:titanium'}),
    'power_pipe': shaped('power_pipe', 16, ['CCC', 'RRR', 'CCC'], {'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone'}),
    'coal_generator': shaped('coal_generator', 1, ['III', 'IFI', 'CRC'], {'I': 'minecraft:iron_ingot', 'F': 'minecraft:furnace',
                                                                       'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone'}),
    'small_battery': shaped('small_battery', 1, ['CRC', 'CRC', 'CRC'], {'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone'}),
    'large_battery': shaped('large_battery', 1, ['TBT', 'BRB', 'TBT'], {'T': f'{NS}:titanium', 'B': f'{NS}:battery',
                                                                     'R': 'minecraft:redstone_block'}),
    'electric_furnace': shaped('electric_furnace', 1, ['ICI', 'CFC', 'IRI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot',
                                                                            'F': 'minecraft:furnace', 'R': 'minecraft:redstone'}),
    'crusher': shaped('crusher', 1, ['III', 'FPF', 'IRI'], {'I': 'minecraft:iron_ingot', 'F': 'minecraft:flint',
                                                          'P': 'minecraft:piston', 'R': 'minecraft:redstone'}),
    'battery': shaped('battery', 1, ['ICI', 'RRR', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot',
                                                         'R': 'minecraft:redstone_block'}),
    'magnetometer': shaped('magnetometer', 1, ['IGI', 'CMC', 'III'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane',
                                                                  'C': 'minecraft:copper_ingot', 'M': 'minecraft:compass'}),
    'plasma_receiver': shaped('plasma_receiver', 1, ['ILI', 'GNG', 'III'], {'I': 'minecraft:iron_ingot', 'L': 'minecraft:lightning_rod',
                                                                        'G': 'minecraft:glass_pane', 'N': 'minecraft:note_block'}),
    'flag': shaped('flag', 1, ['WW ', 'WWI', '  I'], {'W': 'minecraft:white_wool', 'I': 'minecraft:iron_ingot'}),
    'docking_port': shaped('docking_port', 1, [' I ', 'IHI', ' I '], {'I': 'minecraft:iron_ingot',
                                                                     'H': f'{NS}:hull_grey'}),
    'rcs_thruster': shaped('rcs_thruster', 2, [' I ', 'ICI', ' I '], {'I': 'minecraft:iron_ingot',
                                                                     'C': 'minecraft:copper_ingot'}),
    'telescope': shaped('telescope', 1, [' GI', ' I ', 'S S'], {'G': 'minecraft:glass_pane', 'I': 'minecraft:iron_ingot',
                                                             'S': 'minecraft:stick'}),
    'remote_receiver': shaped('remote_receiver', 1, ['ILI', 'CQC', 'IRI'], {'I': 'minecraft:iron_ingot', 'L': 'minecraft:lightning_rod',
                                                                        'C': 'minecraft:copper_ingot', 'Q': 'minecraft:comparator',
                                                                        'R': 'minecraft:redstone'}),
    'remote_console': shaped('remote_console', 1, ['IGI', 'LQL', 'IRI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane',
                                                                      'L': 'minecraft:lever', 'Q': 'minecraft:comparator',
                                                                      'R': 'minecraft:lightning_rod'}),
    'nuclear_device': shaped('nuclear_device', 1, ['TUT', 'URU', 'TCT'], {'T': f'{NS}:titanium', 'U': f'{NS}:uraninite',
                                                                        'R': 'minecraft:redstone_block', 'C': 'minecraft:comparator'}),
    'rc_function': shaped('rc_function', 2, ['IRI', 'RPR', 'IRI'], {'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone',
                                                                  'P': 'minecraft:repeater'}),
    'fuel_loader': shaped('fuel_loader', 1, ['IHI', 'IBI', 'III'], {'I': 'minecraft:iron_ingot', 'H': 'minecraft:hopper',
                                                                  'B': 'minecraft:bucket'}),
}


def main():
    lang = {'itemGroup.stevespace': "Steve's Space Program"}
    for bid, (name, kind, t) in BLOCKS.items():
        model = blockstate_and_model(bid, kind, t)
        if model is not None:
            write(f'{ASSETS}/models/item/{bid}.json', {'parent': model})
        write(f'{DATA}/loot_table/blocks/{bid}.json', {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{bid}'}],
                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        lang[f'block.{NS}.{bid}'] = name
    for rid, recipe in RECIPES.items():
        write(f'{DATA}/recipe/{rid}.json', recipe)
    # Crushed ore smelts (and blasts) back to ingots.
    for metal in ('iron', 'gold', 'copper'):
        dust = f'{metal}_dust'
        lang[f'item.{NS}.{dust}'] = f'{metal.capitalize()} Dust'
        write(f'{ASSETS}/models/item/{dust}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{dust}'}})
        for kind, time in (('smelting', 200), ('blasting', 100)):
            write(f'{DATA}/recipe/{dust}_{kind}.json', {'type': f'minecraft:{kind}', 'category': 'misc',
                  'ingredient': {'item': f'{NS}:{dust}'}, 'result': {'id': f'minecraft:{metal}_ingot'},
                  'experience': 0.7, 'cookingtime': time})

    write(f'{ROOT}/data/minecraft/tags/block/mineable/pickaxe.json',
          {'replace': False, 'values': [f'{NS}:{b}' for b in BLOCKS if b not in SHOVEL]})
    write(f'{ROOT}/data/minecraft/tags/block/mineable/shovel.json',
          {'replace': False, 'values': [f'{NS}:{b}' for b in SHOVEL]})
    write(f'{DATA}/tags/block/not_vessel.json', {'replace': False, 'values': [
        f'{NS}:{b}' for b in NOT_VESSEL_OWN + TERRAIN] + [
        '#minecraft:dirt', '#minecraft:sand', '#minecraft:base_stone_overworld', '#minecraft:base_stone_nether',
        '#minecraft:leaves', '#minecraft:logs', '#minecraft:ice', '#minecraft:snow', '#minecraft:terracotta',
        '#minecraft:coal_ores', '#minecraft:iron_ores', '#minecraft:copper_ores', '#minecraft:gold_ores',
        '#minecraft:redstone_ores', '#minecraft:lapis_ores', '#minecraft:diamond_ores', '#minecraft:emerald_ores',
        '#minecraft:flowers', '#minecraft:replaceable',
        'minecraft:grass_block', 'minecraft:gravel', 'minecraft:clay', 'minecraft:bedrock', 'minecraft:water',
        'minecraft:lava', 'minecraft:snow_block', 'minecraft:mud', 'minecraft:moss_block', 'minecraft:sandstone',
        'minecraft:red_sandstone', 'minecraft:calcite', 'minecraft:obsidian', 'minecraft:dripstone_block',
        'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern', 'minecraft:vine']})

    # Merge with any hand-written language entries (items, GUI text) kept in lang_extra.json.
    extra_path = os.path.join(os.path.dirname(__file__), 'lang_extra.json')
    if os.path.exists(extra_path):
        with open(extra_path) as fh:
            lang.update(json.load(fh))
    lang_path = f'{ASSETS}/lang/en_us.json'
    if os.path.exists(lang_path):
        with open(lang_path) as fh:
            existing = json.load(fh)
        existing.update(lang)
        lang = existing
    write(lang_path, dict(sorted(lang.items())))
    print(f'{len(BLOCKS)} blocks, {len(RECIPES)} recipes written')


if __name__ == '__main__':
    main()
    import worlds  # the other worlds' ground blocks share the tags and names written above
    worlds.build()
