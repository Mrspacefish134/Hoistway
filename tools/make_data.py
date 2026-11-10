"""Writes Hoistway's models, blockstates, recipes, loot tables, tags and language file.

The shapes are easier to keep right as a few lines of Python than as hand-edited JSON,
especially the car (tiled floor and roof) and the escalator (parts tilted 45 degrees).
Run `python tools/make_data.py`.
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hoistway"
DATA = ROOT / "src/main/resources/data"
SIDES = ("north", "south", "west", "east")
ALL = SIDES + ("up", "down")
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def face(texture, uv=None, rotation=None):
    out = {"texture": texture}
    if uv is not None:
        out["uv"] = uv
    if rotation is not None:
        out["rotation"] = rotation
    return out


def element(start, end, faces, rotation=None):
    out = {"from": start, "to": end, "faces": faces}
    if rotation is not None:
        out["rotation"] = rotation
    return out


def every(texture):
    return {side: face(texture) for side in ALL}


def model(textures, elements, parent=None, display=None):
    out = {}
    if parent:
        out["parent"] = parent
    out["ambientocclusion"] = False
    out["textures"] = textures
    out["elements"] = elements
    if display:
        out["display"] = display
    return out


# ------------------------------------------------------------------ motor

def motor_models():
    frame, casing = "#frame", "#casing"
    # Both broad faces of a bracket show the bearing.
    bracket = every(frame)
    bracket["west"] = face("#bracket", [3, 2, 13, 14])
    bracket["east"] = face("#bracket", [3, 2, 13, 14])
    housing = [
        element([0, 0, 0], [16, 2, 16], every(frame)),      # bed plate
        element([0, 2, 3], [2, 14, 13], bracket),           # bearing brackets
        element([14, 2, 3], [16, 14, 13], bracket),
        element([0, 14, 6], [16, 16, 10], every(frame)),    # tie beam
        element([3, 2, 13], [13, 9, 16], every(casing)),    # electric motor
    ]
    write(ASSETS / "models/block/elevator_motor.json", model(
        {"frame": "hoistway:block/motor_frame", "bracket": "hoistway:block/motor_bracket",
         "casing": "hoistway:block/motor_casing", "particle": "hoistway:block/motor_frame"},
        housing, parent="minecraft:block/block"))

    # The drum is drawn spinning by the block entity renderer. Two square sections a
    # quarter turn apart read as round.
    turn = {"origin": [8, 8, 8], "axis": "x", "angle": 45}
    wound = {side: face("#drum") for side in ("north", "south", "up", "down")}
    drum = []
    for rotation in (None, turn):
        drum.append(element([3, 5, 5], [13, 11, 11], wound, rotation))
        drum.append(element([2, 4, 4], [3, 12, 12], every("#frame"), rotation))
        drum.append(element([13, 4, 4], [14, 12, 12], every("#frame"), rotation))
    write(ASSETS / "models/part/motor_drum.json", model(
        {"drum": "hoistway:block/motor_drum", "frame": "hoistway:block/motor_frame",
         "particle": "hoistway:block/motor_drum"}, drum))

    write(ASSETS / "blockstates/elevator_motor.json", {"variants": {
        f"facing={name}": ({"model": "hoistway:block/elevator_motor", "y": y} if y
                           else {"model": "hoistway:block/elevator_motor"})
        for name, y in FACINGS.items()}})
    write(ASSETS / "models/item/elevator_motor.json", {"parent": "hoistway:block/elevator_motor"})


# ------------------------------------------------------------------ call button

def call_button_models():
    faces = {
        "north": face("#panel", [4, 2, 12, 14]),
        "south": face("#panel", [4, 2, 12, 14]),
        "west": face("#panel", [4, 2, 5, 14]),
        "east": face("#panel", [11, 2, 12, 14]),
        "up": face("#panel", [4, 2, 12, 3]),
        "down": face("#panel", [4, 13, 12, 14]),
    }
    variants = {}
    for lit in (False, True):
        name = "call_button_lit" if lit else "call_button"
        write(ASSETS / f"models/block/{name}.json", model(
            {"panel": f"hoistway:block/{name}", "particle": f"hoistway:block/{name}"},
            [element([4, 2, 15], [12, 14, 16], faces)], parent="minecraft:block/block"))
        for facing, y in FACINGS.items():
            variant = {"model": f"hoistway:block/{name}"}
            if y:
                variant["y"] = y
            variants[f"facing={facing},lit={'true' if lit else 'false'}"] = variant
    write(ASSETS / "blockstates/call_button.json", {"variants": variants})
    write(ASSETS / "models/item/call_button.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": "hoistway:block/call_button"}})


# ------------------------------------------------------------------ escalator

def escalator_static():
    """Balustrades, handrails and the truss, laid along the incline (rising to the north)."""
    half = 8 * math.sqrt(2)
    z1, z2 = round(8 - half, 4), round(8 + half, 4)
    tilt = {"origin": [8, 8, 8], "axis": "x", "angle": 45, "rescale": False}
    # Heights below are measured square to the incline: 8 is the line through the block's centre.
    parts = [element([1, -1, z1], [15, 2.34, z2], {
        "up": face("#skirt", [0, 8, 16, 16]),
        "down": face("#skirt", [0, 8, 16, 16]),
        "north": face("#skirt", [1, 8, 15, 11.34]),
        "south": face("#skirt", [1, 8, 15, 11.34]),
    }, tilt)]
    for x1, x2 in ((0, 1), (15, 16)):
        parts.append(element([x1, -1, z1], [x2, 7, z2], {
            "west": face("#skirt", [0, 0, 16, 8]),
            "east": face("#skirt", [0, 0, 16, 8]),
            "north": face("#skirt", [0, 0, 1, 8]),
            "south": face("#skirt", [0, 0, 1, 8]),
            "down": face("#skirt", [0, 7, 16, 8], 90),
        }, tilt))
        parts.append(element([x1, 7, z1], [x2, 23, z2], {
            "west": face("#panel", [0, 0, 16, 16]),
            "east": face("#panel", [0, 0, 16, 16]),
            "north": face("#panel", [0, 0, 1, 16]),
            "south": face("#panel", [0, 0, 1, 16]),
        }, tilt))
        parts.append(element([x1 - 0.5, 23, z1], [x2 + 0.5, 25, z2], {
            "west": face("#handrail", [0, 0, 16, 2]),
            "east": face("#handrail", [0, 0, 16, 2]),
            "up": face("#handrail", [0, 0, 16, 2], 90),
            "down": face("#handrail", [0, 2, 16, 4], 90),
            "north": face("#handrail", [0, 0, 2, 2]),
            "south": face("#handrail", [0, 0, 2, 2]),
        }, tilt))
    return parts


def step(start, end):
    return element(start, end, {
        "up": face("#step", [1, 0, 15, 8]),
        "down": face("#step", [1, 8, 15, 16]),
        "north": face("#step", [1, 8, 15, 16]),
        "south": face("#step", [1, 8, 15, 16]),
        "west": face("#step", [0, 8, 8, 16]),
        "east": face("#step", [0, 8, 8, 16]),
    })


def escalator_models():
    textures = {
        "panel": "hoistway:block/escalator_panel",
        "skirt": "hoistway:block/escalator_skirt",
        "handrail": "hoistway:block/escalator_handrail",
        "step": "hoistway:block/escalator_step",
        "particle": "hoistway:block/escalator_skirt",
    }
    write(ASSETS / "models/block/escalator.json",
          model(textures, escalator_static(), parent="minecraft:block/block"))

    # The plate the steps slide under at the head of a run.
    write(ASSETS / "models/block/escalator_comb.json", model(textures, [
        element([1, 14, 0], [15, 16, 8], {
            "up": face("#step", [1, 0, 15, 8]),
            "down": face("#skirt", [1, 8, 15, 16]),
            "north": face("#skirt", [1, 0, 15, 2]),
            "south": face("#skirt", [1, 0, 15, 2]),
            "west": face("#skirt", [0, 0, 8, 2]),
            "east": face("#skirt", [0, 0, 8, 2]),
        })]))

    # One moving step, drawn by the block entity renderer.
    write(ASSETS / "models/part/escalator_step.json", model(textures, [step([1, 0, 8], [15, 8, 16])]))

    # The item shows the steps parked in place.
    write(ASSETS / "models/item/escalator.json", model(
        textures,
        escalator_static() + [step([1, 0, 8], [15, 8, 16]), step([1, 8, 0], [15, 16, 8])],
        parent="minecraft:block/block",
        display={"gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.5, 0.5, 0.5]}}))

    multipart = []
    for facing, y in FACINGS.items():
        for name, when in (("escalator", {"facing": facing}),
                           ("escalator_comb", {"facing": facing, "top": "true"})):
            apply = {"model": f"hoistway:block/{name}"}
            if y:
                apply["y"] = y
            multipart.append({"when": when, "apply": apply})
    write(ASSETS / "blockstates/escalator.json", {"multipart": multipart})


# ------------------------------------------------------------------ elevator car

def car_model(size):
    """The cage. Authored 16 pixels low so its 46 pixel height fits the -16..32 model range."""
    low, high = (0, 16) if size == 1 else (-16, 32)
    parts = []
    for x in range(low, high, 16):
        for z in range(low, high, 16):
            outer = [side for side, on_edge in (("north", z == low), ("south", z + 16 == high),
                                                ("west", x == low), ("east", x + 16 == high)) if on_edge]
            sill = {side: face("#trim", [0, 0, 16, 3]) for side in outer}      # hazard-striped floor edge
            fascia = {side: face("#trim", [0, 4, 16, 7]) for side in outer}    # roof edge
            parts.append(element([x, -16, z], [x + 16, -13, z + 16],
                                 {"up": face("#floor", [0, 0, 16, 16]), "down": face("#frame", [0, 0, 16, 16]), **sill}))
            parts.append(element([x, 25, z], [x + 16, 28, z + 16],
                                 {"up": face("#roof", [0, 0, 16, 16]), "down": face("#ceiling", [0, 0, 16, 16]), **fascia}))
    for x in (low, high - 2):
        for z in (low, high - 2):
            for y1, y2 in ((-13, 3), (3, 19), (19, 25)):
                parts.append(element([x, y1, z], [x + 2, y2, z + 2],
                                     {side: face("#frame", [0, 0, 2, y2 - y1]) for side in SIDES}))
    # Floor panel on the inside of the north side, beside the north-east post.
    panel_edge = face("#panel", [4, 2, 5, 14])
    parts.append(element([high - 8, 4, low], [high - 3, 12, low + 0.5], {
        "south": face("#panel", [4, 2, 12, 14]),
        "north": face("#frame", [0, 0, 5, 8]),
        "west": panel_edge, "east": panel_edge,
        "up": face("#panel", [4, 2, 12, 3]), "down": face("#panel", [4, 13, 12, 14]),
    }))
    # Crosshead: where the hoist cables are made fast.
    crosshead = {side: face("#frame", [0, 0, 6, 2]) for side in SIDES}
    crosshead["up"] = face("#frame", [0, 0, 6, 6])
    parts.append(element([5, 28, 5], [11, 30, 11], crosshead))
    return model({
        "floor": "hoistway:block/car_floor",
        "frame": "hoistway:block/car_frame",
        "trim": "hoistway:block/car_trim",
        "roof": "hoistway:block/car_roof",
        "ceiling": "hoistway:block/car_ceiling",
        "panel": "hoistway:block/call_button",
        "particle": "hoistway:block/car_frame",
    }, parts)


def car_models():
    write(ASSETS / "models/part/car_small.json", car_model(1))
    write(ASSETS / "models/part/car_large.json", car_model(3))


# ------------------------------------------------------------------ items, lang

def item_models():
    for name in ("cable", "speed_upgrade", "stop_upgrade", "creative_upgrade", "elevator_car", "large_elevator_car"):
        write(ASSETS / f"models/item/{name}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"hoistway:item/{name}"}})


def lang():
    write(ASSETS / "lang/en_us.json", {
        "itemGroup.hoistway": "Hoistway",
        "block.hoistway.elevator_motor": "Elevator Motor",
        "block.hoistway.call_button": "Call Button",
        "block.hoistway.escalator": "Escalator",
        "item.hoistway.cable": "Hoist Cable",
        "item.hoistway.speed_upgrade": "Speed Upgrade",
        "item.hoistway.stop_upgrade": "Stop Upgrade",
        "item.hoistway.creative_upgrade": "Creative Elevator Upgrade",
        "hoistway.car.floor": "Floor %s",
        "hoistway.panel.title": "Elevator",
        "hoistway.car.passing_up": "▲ %s",
        "hoistway.car.passing_down": "▼ %s",
        "item.hoistway.elevator_car": "Elevator Car",
        "item.hoistway.large_elevator_car": "Large Elevator Car",
        "entity.hoistway.elevator_car": "Elevator Car",
        "hoistway.call.no_motor": "No elevator motor above this button",
        "hoistway.call.too_high": "This floor is too close to the motor",
        "hoistway.call.no_cable": "Not enough cable to reach this floor",
        "hoistway.call.too_many_stops": "The motor needs a Stop Upgrade to serve this floor",
        "hoistway.car.no_motor": "There is no elevator motor above",
        "hoistway.car.occupied": "That motor already has a car",
        "hoistway.car.no_room": "Not enough room for the car",
        "hoistway.car.no_cable": "The motor needs more cable to reach this car",
    })


# ------------------------------------------------------------------ data

def shaped(name, pattern, key, result, count=1):
    write(DATA / f"hoistway/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": pattern,
        "key": {symbol: {"item": item} for symbol, item in key.items()},
        "result": {"id": result, "count": count},
    })


def recipes():
    shaped("cable", ["I", "I", "I"], {"I": "minecraft:iron_ingot"}, "hoistway:cable", 16)
    shaped("elevator_motor", ["IRI", "CPC", "IBI"], {
        "I": "minecraft:iron_ingot", "R": "minecraft:redstone_block", "C": "hoistway:cable",
        "P": "minecraft:piston", "B": "minecraft:iron_block"}, "hoistway:elevator_motor")
    shaped("elevator_car", ["ICI", "I I", "PPP"], {
        "I": "minecraft:iron_ingot", "C": "hoistway:cable",
        "P": "minecraft:heavy_weighted_pressure_plate"}, "hoistway:elevator_car")
    shaped("large_elevator_car", ["IPI", "PEP", "IPI"], {
        "I": "minecraft:iron_ingot", "P": "minecraft:heavy_weighted_pressure_plate",
        "E": "hoistway:elevator_car"}, "hoistway:large_elevator_car")
    shaped("call_button", ["I", "B", "R"], {
        "I": "minecraft:iron_ingot", "B": "minecraft:stone_button", "R": "minecraft:redstone"},
        "hoistway:call_button", 3)
    shaped("speed_upgrade", ["GRG", "RSR", "GRG"], {
        "G": "minecraft:gold_ingot", "R": "minecraft:redstone", "S": "minecraft:sugar"},
        "hoistway:speed_upgrade")
    shaped("stop_upgrade", ["GRG", "RLR", "GRG"], {
        "G": "minecraft:gold_ingot", "R": "minecraft:redstone", "L": "minecraft:lapis_lazuli"},
        "hoistway:stop_upgrade")
    shaped("escalator", ["I  ", "SI ", "RSI"], {
        "I": "minecraft:iron_ingot", "S": "minecraft:smooth_stone", "R": "minecraft:redstone"},
        "hoistway:escalator", 4)


def loot_and_tags():
    blocks = ("elevator_motor", "call_button", "escalator")
    for name in blocks:
        write(DATA / f"hoistway/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"hoistway:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    write(DATA / "minecraft/tags/block/mineable/pickaxe.json",
          {"replace": False, "values": [f"hoistway:{name}" for name in blocks]})


def main():
    motor_models()
    call_button_models()
    escalator_models()
    car_models()
    item_models()
    lang()
    recipes()
    loot_and_tags()
    print("models and data written")


if __name__ == "__main__":
    main()
