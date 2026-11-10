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
    # steel, dark to light
    "0": "1F2228", "1": "30343C", "2": "454A55", "3": "5D6370",
    "4": "7A808D", "5": "9BA1AD", "6": "C2C7CF", "7": "E4E7EB",
    "x": "16181D",
    # safety yellow
    "a": "B8862B", "b": "E3B341", "c": "F6D878",
    # red, green indicators
    "r": "8E2A24", "R": "C8372D", "g": "2E7D3A", "G": "4CC95E", "h": "A6F0A8",
    # handrail rubber
    "k": "141518", "K": "22242A", "l": "33363D",
    # motor housing paint
    "p": "274B5E", "P": "356A82", "q": "4B8BA6", "Q": "7FB4CB",
    # circuit board and contacts
    "d": "1E5B3A", "D": "2E8B57", "e": "58C283", "u": "F2B872",
    # indicator blue
    "I": "58A6F0", "j": "B8DCFF",
}

T = {}

# ---------------------------------------------------------------- elevator car

# Tread plate: raised dashes in alternating directions inside a bevelled frame.
T["block/car_floor"] = [
    "2222222222222222",
    "2555555555555532",
    "2544545444445432",
    "2545444544454432",
    "2554444454544432",
    "2544444444444432",
    "2554444454544432",
    "2545444544454432",
    "2544545444445432",
    "2544444444444432",
    "2544545444445432",
    "2545444544454432",
    "2554444454544432",
    "2544444444444432",
    "2533333333333332",
    "2222222222222222",
]

# Brushed steel for the posts and the edges of floor and roof.
T["block/car_frame"] = [
    "6666666666666665",
    "6545545455454543",
    "6575545455457543",
    "6525545455452543",
    "6545545455454543",
    "6545545455454543",
    "6545545455454543",
    "6545545455454543",
    "6545545455454543",
    "6545545455454543",
    "6545545455454543",
    "6545545455454543",
    "6575545455457543",
    "6525545455452543",
    "6545545455454543",
    "5333333333333332",
]

# Roof seen from above, with the escape hatch.
T["block/car_roof"] = [
    "3333333333333333",
    "3555555555555542",
    "3544444444444432",
    "3544444444444432",
    "3544222222224432",
    "3544255555524432",
    "3544254444324432",
    "3544254444324432",
    "3544254444324432",
    "3544254444324432",
    "3544253333324432",
    "3544222222224432",
    "3544444444444432",
    "3544444444444432",
    "3433333333333332",
    "3222222222222222",
]

# Ceiling seen from inside, with a light panel.
T["block/car_ceiling"] = [
    "2222222222222222",
    "2444444444444442",
    "2433333333333342",
    "2434444444444342",
    "2434111111114342",
    "2434177777714342",
    "24341c7777c14342",
    "2434177777714342",
    "2434177777714342",
    "24341c7777c14342",
    "2434177777714342",
    "2434111111114342",
    "2434444444444342",
    "2433333333333342",
    "2444444444444442",
    "2222222222222222",
]

# ---------------------------------------------------------------- motor

# Heavy plate for the winch frame.
T["block/motor_frame"] = [
    "4444444444444443",
    "4333333333333321",
    "4353333333335321",
    "4313333333331321",
    "4333333333333321",
    "4333333333333321",
    "4333333333333321",
    "4322222222222221",
    "4344444444444321",
    "4333333333333321",
    "4333333333333321",
    "4333333333333321",
    "4353333333335321",
    "4313333333331321",
    "4222222222222221",
    "3111111111111111",
]

# Painted motor housing: cooling fins, a nameplate and two status lamps.
T["block/motor_casing"] = [
    "QQQQQQQQQQQQQQQq",
    "QqqqqqqqqqqqqqPp",
    "QqPPPPPPPPPPPPPp",
    "QqppppppppppppPp",
    "QqPPPPPPPPPPPPPp",
    "QqppppppppppppPp",
    "QqPPPPPPPPPPPPPp",
    "QqppppppppppppPp",
    "QqPPPPPPPPPPPPPp",
    "QqPP6666666PPPPp",
    "QqPP6262626PPPPp",
    "QqPP6666666PPPPp",
    "QqPPPPPPPPPPGRPp",
    "QqPPPPPPPPPPPPPp",
    "QPppppppppppppPp",
    "pppppppppppppppp",
]

# Cable wound on the drum: each wrap is a light strand beside a dark one.
T["block/motor_drum"] = [
    "5353535353535353",
    "5343534353435343",
] * 8

# ---------------------------------------------------------------- call button

def call_button(lit):
    arrow = "G" if lit else "2"
    ring = "b" if lit else "3"
    core = "c" if lit else "4"
    panel = [
        "66666664",
        "655AA553",
        "65AAAA53",
        "65555553",
        "65AAAA53",
        "655AA553",
        "65555553",
        "655BB553",
        "65BCCB53",
        "65BCCB53",
        "655BB553",
        "43333332",
    ]
    rows = ["." * 16] * 2
    for row in panel:
        rows.append("...." + row.replace("A", arrow).replace("B", ring).replace("C", core) + "....")
    return rows + ["." * 16] * 2

T["block/call_button"] = call_button(False)
T["block/call_button_lit"] = call_button(True)

# ---------------------------------------------------------------- escalator

# Balustrade panel: polished sheet with slanted glints, tiles along the incline.
T["block/escalator_panel"] = [
    "2222222222222222",
    "4444444444444444",
    "6666666666666666",
    "6666676666666766",
    "6666766666667666",
    "6667666666676666",
    "6676666666766666",
    "6766666667666666",
    "6666666666666666",
    "6666666666666666",
    "6666665666666656",
    "6666656666666566",
    "6666566666665666",
    "6666666666666666",
    "5555555555555555",
    "3333333333333333",
]

# Top half: the skirt beside the steps. Bottom half: the truss underneath.
T["block/escalator_skirt"] = [
    "3333333333333333",
    "4444444444444444",
    "3333333333333333",
    "3333333333333333",
    "3323333333332333",
    "3333333333333333",
    "2222222222222222",
    "1111111111111111",
    "1111111111111111",
    "1211111221111121",
    "1121112112111211",
    "1112121111212111",
    "1111211111121111",
    "1112121111212111",
    "1121112112111211",
    "1211111221111121",
]

T["block/escalator_handrail"] = [
    "llllllllllllllll",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
    "kkkkkkkkkkkkkkkk",
] * 4

# Top half: grooved tread with the yellow nosing. Bottom half: the cleated riser.
T["block/escalator_step"] = [
    "3232323232323232",
    "2121212121212121",
    "2121212121212121",
    "2121212121212121",
    "2121212121212121",
    "2121212121212121",
    "2121212121212121",
    "babababababababa",
    "1111111111111111",
    "1010101010101010",
    "1010101010101010",
    "1010101010101010",
    "1010101010101010",
    "1010101010101010",
    "1010101010101010",
    "0000000000000000",
]

# ---------------------------------------------------------------- hoist cable

# Two pixels wide; the shading swaps sides down the length so it reads as twisted strands.
T["entity/cable"] = ["64", "53", "35", "46"] * 4

# ---------------------------------------------------------------- items

T["item/cable"] = [
    "................",
    ".....xxxxxx.....",
    "...xx556655xx...",
    "..x5665445665x..",
    ".x565xxxxxx565x.",
    ".x56x......x65x.",
    "x565x......x565x",
    "x464x......x464x",
    "x464x......x464x",
    "x353x......x353x",
    ".x35x......x53x.",
    ".x353xxxxxx353x.",
    "..x3532332353x..",
    "...xx322223xx...",
    ".....xxxxxx.....",
    "................",
]

def upgrade_card(symbol):
    rows = [
        "................",
        "..xxxxxxxxxxxx..",
        ".xeDDDDDDDDDDex.",
        ".xDDDDDDDDDDDDx.",
    ]
    rows += [".x" + row + "x." for row in symbol]
    rows += [
        ".xDDDDDDDDDDDDx.",
        ".xdDuDuDuDuDDdx.",
        ".xddududududddx.",
        "..xxxxxxxxxxxx..",
        "................",
    ]
    return rows

# Two chevrons pointing up.
T["item/speed_upgrade"] = upgrade_card([
    "DDDDDccDDDDD",
    "DDDDcbbcDDDD",
    "DDDbbDDbbDDD",
    "DDDDDccDDDDD",
    "DDDDcbbcDDDD",
    "DDDbbDDbbDDD",
    "DDDDDDDDDDDD",
])

# Three floors, each with its indicator lamp.
T["item/stop_upgrade"] = upgrade_card([
    "DDDDDDDDDDDD",
    "DDjjDIIIIIDD",
    "DDDDDDDDDDDD",
    "DDjjDIIIIIDD",
    "DDDDDDDDDDDD",
    "DDjjDIIIIIDD",
    "DDDDDDDDDDDD",
])

T["item/elevator_car"] = [
    "......xxxx......",
    "......x56x......",
    "..xxxxxxxxxxxx..",
    "..x6666666665x..",
    "..x5xxxxxxxx3x..",
    "..x5x222222x3x..",
    "..x5x222222x3x..",
    "..x5x222222x3x..",
    "..x5x444444x3x..",
    "..x5x222222x3x..",
    "..x5x222222x3x..",
    "..x5x222222x3x..",
    "..x5xxxxxxxx3x..",
    "..x4444444443x..",
    "..xxxxxxxxxxxx..",
    "................",
]

T["item/large_elevator_car"] = [
    "......xxxx......",
    "......x56x......",
    "xxxxxxxxxxxxxxxx",
    "x66666666666665x",
    "x5xxxxxxxxxxxx3x",
    "x5x2222222222x3x",
    "x5x2222222222x3x",
    "x5x2222222222x3x",
    "x5x4444444444x3x",
    "x5x2222222222x3x",
    "x5x2222222222x3x",
    "x5x2222222222x3x",
    "x5xxxxxxxxxxxx3x",
    "x44444444444443x",
    "xxxxxxxxxxxxxxxx",
    "................",
]


# ---------------------------------------------------------------- writing

def pixels(rows):
    width = len(rows[0])
    out = []
    for y, row in enumerate(rows):
        if len(row) != width:
            raise ValueError(f"row {y} is {len(row)} wide, expected {width}: {row!r}")
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
    grids = {name: pixels(rows) for name, rows in T.items()}
    for name, grid in grids.items():
        write_png(TEXTURES / f"{name}.png", grid)
    print(f"wrote {len(grids)} textures")
    if len(sys.argv) > 1:
        preview(sys.argv[1], grids)
        print("order:", ", ".join(grids))


if __name__ == "__main__":
    main()
