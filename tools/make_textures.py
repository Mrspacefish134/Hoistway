"""Hoistway's textures, drawn by hand as pixel grids.

Every texture below is a list of rows; each character is one pixel looked up in PALETTE.
Run `python tools/make_textures.py` to write the PNGs into src/main/resources.
Pass a folder as the first argument to also write an enlarged preview sheet there.
"""
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/hoistway/textures"

PALETTE = {
    ".": None,
    "x": "0E0F13",  # outline
    # steel: cool in the shadows, slightly warm in the lights
    "0": "17191F", "1": "252932", "2": "363C49", "3": "4B5261", "4": "636B7A",
    "5": "808894", "6": "A3AAB3", "7": "C6CBD0", "8": "E3E5E6", "9": "F7F7F4",
    # brass
    "A": "4F3217", "B": "7E5522", "C": "B08232", "D": "D9B04C", "E": "F3DA84",
    # machine paint (teal)
    "p": "14303A", "P": "1D4654", "q": "2A6272", "Q": "3C8493", "t": "5FA7B2", "T": "98CFD3",
    # safety yellow
    "y": "8F6212", "Y": "D49D1D", "z": "F5C842", "Z": "FFE88C",
    # rubber
    "k": "0D0E11", "K": "191B20", "l": "282B32", "L": "3A3E47",
    # lamps
    "r": "6B1B1B", "R": "B02D29", "s": "E25C49",
    "g": "1D6632", "G": "3DB659", "h": "9BEFA0",
    "i": "2B4F6E", "I": "4A7FAA", "j": "86B9DD", "J": "CBE7F8",
    # balustrade glass
    "w": "8AA9BF", "v": "A9C4D6", "V": "D4E4EE",
    # circuit board and gold contacts
    "m": "113B27", "M": "1D6740", "n": "2E915E", "N": "62C489",
    "u": "A85C24", "U": "E5964A",
}

T = {}


def framed(top, rows, bottom, left, right):
    """Wraps 12-pixel-wide rows in a two-pixel frame."""
    return [top[0], top[1]] + [left + row + right for row in rows] + [bottom[0], bottom[1]]


# ---------------------------------------------------------------- elevator car

# Tread plate. Each raised bar catches light on its upper edge and drops a shadow below;
# the bars alternate direction so the plate reads as a diamond lattice.
BAR_A = ["4464", "4653", "6534", "4344"]
BAR_B = ["4644", "3564", "4356", "4434"]


def tread_band(first, second):
    return [first[i] + second[i] + first[i] for i in range(4)]


T["block/car_floor"] = framed(
    ["1111111111111111", "1666666666666621"],
    tread_band(BAR_A, BAR_B) + tread_band(BAR_B, BAR_A) + tread_band(BAR_A, BAR_B),
    ["1222222222222221", "1111111111111111"],
    "16", "21")

# Brushed steel for the corner posts, the crosshead and the underside of the floor.
# The two left columns are what a post shows; the collars at rows 0 and 8 are its joints.
T["block/car_frame"] = [
    "8634444444444321",
    "7465565565565431",
    "7465565565565431",
    "7465565565565431",
    "7465565565565431",
    "7465565565565431",
    "7465565565565431",
    "5243343343343210",
] * 2

# Edge trim. Rows 0-2: the hazard-striped sill of the floor. Rows 4-6: the roof fascia.
T["block/car_trim"] = [
    "ZzKKZzKKZzKKZzKK",
    "zKKzzKKzzKKzzKKz",
    "kkYYkkYYkkYYkkYY",
    "1111111111111111",
    "8777777777777776",
    "5585555855558554",
    "3323333233332332",
    "1111111111111111",
] + ["4444444444444444"] * 8

# Roof seen from above: riveted plate with the escape hatch and its handle.
T["block/car_roof"] = [
    "2222222222222222",
    "2766666666666641",
    "2685555555555831",
    "2655555555555531",
    "2655333333335531",
    "2655377777735531",
    "2655376666535531",
    "2655376666535531",
    "2655376226535531",
    "2655376666535531",
    "2655375555535531",
    "2655333333335531",
    "2655555555555531",
    "2685555555555831",
    "2433333333333321",
    "2111111111111110",
]

# Ceiling seen from inside: pale panels, a warm light and a row of vent slots.
T["block/car_ceiling"] = [
    "3333333333333333",
    "3888888888888873",
    "3877777777777763",
    "3877777777777763",
    "3877222222227763",
    "38772ZZZZZZ27763",
    "38772Z9999Z27763",
    "38772Z9999Z27763",
    "38772Z9999Z27763",
    "38772Z9999Z27763",
    "38772ZZZZZZ27763",
    "3877222222227763",
    "3877777777777763",
    "3877565656567763",
    "3766666666666663",
    "3333333333333333",
]

# ---------------------------------------------------------------- motor

# Gunmetal plate for the winch. The bottom two rows are the hazard stripe round the bed plate;
# the slot in the middle is what you see turning on the drum's flanges.
T["block/motor_frame"] = [
    "5555555555555543",
    "5444444444444432",
    "5433333333333321",
    "5436333333336321",
    "5431333333331321",
    "5433333333333321",
    "5433333333333321",
    "5433222222223321",
    "5433211111123321",
    "5433244444423321",
    "5433333333333321",
    "5436333333336321",
    "5431333333331321",
    "5322222222222211",
    "zzKKzzKKzzKKzzKK",
    "YkkYYkkYYkkYYkkY",
]

# Outer face of a bearing bracket: bolted plate with a brass bearing and the axle end.
T["block/motor_bracket"] = [
    "3333333333333333",
    "3333333333333333",
    "3334444444444333",
    "3334333333332333",
    "3334363333632333",
    "333433CDDC332333",
    "33343CE77CB32333",
    "33343D7227B32333",
    "33343D7227B32333",
    "33343CB66BA32333",
    "333433BBAA332333",
    "3334363333632333",
    "3334333333332333",
    "3334222222222333",
    "3333333333333333",
    "3333333333333333",
]

# Painted motor housing. The art sits where the model samples it (rows 7-13, columns 3-12):
# cooling slots, a brass nameplate and the run/fault lamps. Rows 13-15 are the top of the case.
T["block/motor_casing"] = [
    "QQQQQQQQQQQQQQQQ",
    "QqQqQqQqQqQqQqQq",
    "QqQqQqQqQqQqQqQq",
    "QqQqQqQqQqQqQqQq",
    "QqQqQqQqQqQqQqQq",
    "QQQQQQQQQQQQQQQQ",
    "qqqqqqqqqqqqqqqq",
    "QQQTTTTTTTTTtQQQ",
    "QQQtQQQQQQQQqQQQ",
    "QQQtqPqPqPqQqQQQ",
    "QQQtqPqPqPqQqQQQ",
    "QQQtQQQQDEDQqQQQ",
    "QQQtQQQQQGRQqQQQ",
    "qqqqPPPPPPPPpqqq",
    "tttttttttttttttt",
    "TTTTTTTTTTTTTTTT",
]

# Cable wound on the drum. Each wrap is a light strand beside a dark one; the banding down the
# rows makes the turning visible.
T["block/motor_drum"] = [
    "7474747474747474",
    "6363636363636363",
    "5353535353535353",
    "6363636363636363",
] * 4

# ---------------------------------------------------------------- call button

def call_button(lit):
    swaps = {
        "U": "G" if lit else "3",   # arrows
        "O": "D" if lit else "B",   # button ring
        "W": "9" if lit else "7",   # button highlight
        "o": "Z" if lit else "6",   # button face
    }
    panel = [
        "99999997",
        "977UU775",
        "97UUUU75",
        "97777775",
        "97UUUU75",
        "977UU775",
        "97777775",
        "977OO775",
        "97OWoO75",
        "97OooO75",
        "977OO775",
        "75555554",
    ]
    rows = ["." * 16] * 2
    for row in panel:
        for key, value in swaps.items():
            row = row.replace(key, value)
        rows.append("...." + row + "....")
    return rows + ["." * 16] * 2


T["block/call_button"] = call_button(False)
T["block/call_button_lit"] = call_button(True)

# ---------------------------------------------------------------- escalator

# Balustrade: a tinted glass panel between aluminium trims, with slanted reflections.
# It repeats along the incline.
T["block/escalator_panel"] = [
    "3333333333333333",
    "8888888888888888",
    "5555555555555555",
    "VVVVVVVVVVVVVVVV",
    "vvvvvVvvvvvvvVvv",
    "vvvvVvvvvvvvVvvv",
    "vvvVvvvvvvvVvvvv",
    "vvVvvvvvvvVvvvvv",
    "vVvvvvvvvVvvvvvv",
    "vvvvvvvvvvvvvvvv",
    "vvvvvvwvvvvvvvwv",
    "vvvvvwvvvvvvvwvv",
    "vvvvwvvvvvvvwvvv",
    "wwwwwwwwwwwwwwww",
    "6666666666666666",
    "3333333333333333",
]

# Top half: the cladding below the glass. Bottom half: the cross-braced truss underneath.
T["block/escalator_skirt"] = [
    "7777777777777777",
    "5555555555555555",
    "5555455555554555",
    "5555555555555555",
    "5535555555553555",
    "5555555555555555",
    "3333333333333333",
    "2222222222222222",
    "2222222222222222",
    "1311111331111131",
    "1131113113111311",
    "1113131111313111",
    "1111311111131111",
    "1113131111313111",
    "1131113113111311",
    "1311111331111131",
]

T["block/escalator_handrail"] = [
    "LLLLLLLLLLLLLLLL",
    "llllllllllllllll",
    "KKKKKKKKKKKKKKKK",
    "kkkkkkkkkkkkkkkk",
] * 4

# Top half: the grooved tread with yellow edges and nosing. Bottom half: the cleated riser.
T["block/escalator_step"] = [
    "0Y313131313131Y0",
    "0Y202020202020Y0",
    "0Y202020202020Y0",
    "0Y202020202020Y0",
    "0Y202020202020Y0",
    "0Y202020202020Y0",
    "0Y202020202020Y0",
    "0zzYzYzYzYzYzYz0",
    "0111111111111110",
    "0101010101010100",
    "0101010101010100",
    "0101010101010100",
    "0101010101010100",
    "0101010101010100",
    "0101010101010100",
    "0000000000000000",
]

# ---------------------------------------------------------------- hoist cable

# Two pixels wide; the shading swaps sides down the length so it reads as twisted strands.
T["entity/cable"] = ["75", "64", "46", "57"] * 4

# ---------------------------------------------------------------- items

# A reel of cable lying on its side: brass flanges, steel wraps between them.
T["item/cable"] = [
    "................",
    "................",
    ".xxxx......xxxx.",
    ".xEDx......xDCx.",
    ".xEDxxxxxxxxDCx.",
    ".xDCx868686xCBx.",
    ".xDCx757575xCBx.",
    ".xDCx757575xCBx.",
    ".xDCx646464xCBx.",
    ".xCBx646464xBAx.",
    ".xCBx535353xBAx.",
    ".xCBxxxxxxxxBAx.",
    ".xCBx......xBAx.",
    ".xxxx......xxxx.",
    "................",
    "................",
]


def upgrade_card(emblem):
    rows = [
        "................",
        "..xxxxxxxxxxxx..",
        ".xNnnnnnnnnnnMx.",
        ".xnMMMMMMMMMMmx.",
    ]
    rows += [".xn" + row + "mx." for row in emblem]
    rows += [
        ".xnMMMMMMMMMMmx.",
        ".xmUmUmUmUmUmmx.",
        ".xmumumumumummx.",
        "..xxxxxxxxxxxx..",
        "................",
    ]
    return rows


# Two chevrons pointing up.
T["item/speed_upgrade"] = upgrade_card([
    "MMMMZZMMMM",
    "MMMzzzzMMM",
    "MMzzMMzzMM",
    "MzYMZZMYzM",
    "MMMzzzzMMM",
    "MMzzMMzzMM",
    "MzYMMMMYzM",
])

# Three floors with their indicator lamps; the car is at the bottom one.
T["item/stop_upgrade"] = upgrade_card([
    "MMMMMMMMMM",
    "MJjMIIIIMM",
    "MMMMMMMMMM",
    "MJjMIIIIMM",
    "MMMMMMMMMM",
    "MhGMIIIIMM",
    "MMMMMMMMMM",
])

# An infinity sign: no limit on floors.
T["item/creative_upgrade"] = upgrade_card([
    "MMMMMMMMMM",
    "MMMMMMMMMM",
    "MjJJMMjJJM",
    "JMMMJJMMMJ",
    "MjJJMMjJJM",
    "MMMMMMMMMM",
    "MMMMMMMMMM",
])

T["item/elevator_car"] = [
    ".......65.......",
    ".......65.......",
    "..xxxxxxxxxxxx..",
    "..x8777777776x..",
    "..x6555555554x..",
    "..x722ZZZZ224x..",
    "..x7233333324x..",
    "..x7233333324x..",
    "..x7255555524x..",
    "..x7233333324x..",
    "..x7222222224x..",
    "..x7222222224x..",
    "..x6666666665x..",
    "..xzzKKzzKKzzx..",
    "..xxxxxxxxxxxx..",
    "................",
]

T["item/large_elevator_car"] = [
    ".......65.......",
    ".......65.......",
    "xxxxxxxxxxxxxxxx",
    "x87777777777776x",
    "x65555555555554x",
    "x7222ZZZZZZ2224x",
    "x72" + "3" * 10 + "24x",
    "x72" + "3" * 10 + "24x",
    "x72" + "5" * 10 + "24x",
    "x72" + "3" * 10 + "24x",
    "x72" + "2" * 10 + "24x",
    "x72" + "2" * 10 + "24x",
    "x66666666666665x",
    "xzzKKzzKKzzKKzzx",
    "xxxxxxxxxxxxxxxx",
    "................",
]


# ---------------------------------------------------------------- writing

def pixels(name, rows):
    width = len(rows[0])
    out = []
    for y, row in enumerate(rows):
        if len(row) != width:
            raise ValueError(f"{name}: row {y} is {len(row)} wide, expected {width}: {row!r}")
        line = []
        for ch in row:
            colour = PALETTE[ch]
            if colour is None:
                line.append((0, 0, 0, 0))
            else:
                line.append((int(colour[0:2], 16), int(colour[2:4], 16), int(colour[4:6], 16), 255))
        out.append(line)
    return out


def write_png(path, grid):
    height, width = len(grid), len(grid[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in grid)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def preview(folder, grids, scale=14, columns=6, gap=10):
    cell = 16 * scale + gap
    names = list(grids)
    rows = (len(names) + columns - 1) // columns
    sheet = [[(40, 42, 48, 255)] * (columns * cell + gap) for _ in range(rows * cell + gap)]
    for index, name in enumerate(names):
        grid = grids[name]
        ox = gap + (index % columns) * cell
        oy = gap + (index // columns) * cell
        for y, row in enumerate(grid):
            for x, px in enumerate(row):
                for sy in range(scale):
                    for sx in range(scale):
                        yy, xx = oy + y * scale + sy, ox + x * scale + sx
                        if px[3] == 0:
                            light = ((xx // 7) + (yy // 7)) % 2 == 0
                            sheet[yy][xx] = (92, 94, 100, 255) if light else (74, 76, 82, 255)
                        else:
                            sheet[yy][xx] = px
    write_png(Path(folder) / "preview.png", sheet)


def main():
    grids = {name: pixels(name, rows) for name, rows in T.items()}
    for name, grid in grids.items():
        write_png(TEXTURES / f"{name}.png", grid)
    print(f"wrote {len(grids)} textures")
    if len(sys.argv) > 1:
        preview(sys.argv[1], grids)
        print("order:", ", ".join(grids))


if __name__ == "__main__":
    main()
