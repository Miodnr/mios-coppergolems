"""Writes blockstates, models, item definitions, textures, loot tables, recipes, tags, advancements and language files
for the Habour add-on.

Usage: python gen_habour_resources.py habour/src/main/resources
"""
import json
import os
import struct
import sys
import zlib

ROOT = sys.argv[1]
NS = "mioscoppergolems_habour"
A = os.path.join(ROOT, "assets", NS)
D = os.path.join(ROOT, "data", NS)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# --- tiny PNG writer ---

def write_png(path, pixels):
    """pixels: list of rows of (r, g, b, a) tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def rgb(value, a=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, a)


WOOD = [rgb(0x8A5A2B), rgb(0x7C4F25), rgb(0x9A6732), rgb(0x6B4420)]
COPPER = [rgb(0xC77B58), rgb(0xA9623F), rgb(0xE19A72), rgb(0x7E4630)]
TEAL = rgb(0x3FA796)
PARCHMENT = rgb(0xE8D9A8)
INK = rgb(0x3B2A1A)
ROPE = [rgb(0xC9B27A), rgb(0xA8915A), rgb(0xD9C58E)]


def planks(seed):
    """Horizontal boards: 4 rows of 4 pixels, each a little different."""
    rows = []
    for y in range(16):
        board = y // 4
        row = []
        for x in range(16):
            shade = WOOD[(board + seed) % 3]
            if y % 4 == 3:
                shade = WOOD[3]
            elif (x * 7 + y * 13 + seed * 5) % 11 == 0:
                shade = WOOD[(board + seed + 1) % 3]
            row.append(shade)
        rows.append(row)
    return rows


def trim_copper(rows, top=True, bottom=True, sides=False):
    for x in range(16):
        if top:
            rows[0][x] = COPPER[2]
            rows[1][x] = COPPER[0]
            rows[2][x] = COPPER[1]
        if bottom:
            rows[15][x] = COPPER[3]
            rows[14][x] = COPPER[1]
            rows[13][x] = COPPER[0]
    if sides:
        for y in range(16):
            for x in (0, 1, 14, 15):
                rows[y][x] = COPPER[0] if x in (0, 15) else COPPER[1]
    return rows


def rect(rows, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            rows[y][x] = color


def kontor_side():
    rows = trim_copper(planks(0), top=True, bottom=True)
    return rows


def kontor_front():
    rows = trim_copper(planks(1), top=True, bottom=True)
    # A ledger window with a copper frame and an anchor plaque.
    rect(rows, 2, 4, 13, 12, COPPER[3])
    rect(rows, 3, 5, 12, 11, rgb(0x2B1D12))
    rect(rows, 3, 9, 12, 11, rgb(0x4A3320))
    # Parchment sheets on the shelf and a teal ribbon.
    rect(rows, 4, 6, 6, 8, PARCHMENT)
    rect(rows, 8, 7, 11, 8, PARCHMENT)
    for x in range(4, 7):
        rows[7][x] = INK if x == 5 else PARCHMENT
    rect(rows, 9, 6, 9, 8, TEAL)
    # Anchor on the sill.
    for (x, y) in [(7, 9), (7, 10), (6, 11), (8, 11), (5, 10), (9, 10)]:
        rows[y][x] = COPPER[2]
    return rows


def kontor_top():
    rows = planks(2)
    for x in range(16):
        rows[0][x] = COPPER[0]
        rows[15][x] = COPPER[0]
    for y in range(16):
        rows[y][0] = COPPER[0]
        rows[y][15] = COPPER[0]
    rect(rows, 3, 3, 12, 12, PARCHMENT)
    # A little chart: coast line and a route.
    for (x, y) in [(4, 4), (5, 4), (5, 5), (6, 6), (7, 6), (8, 7), (9, 8), (10, 8), (10, 9), (11, 10)]:
        rows[y][x] = TEAL
    rows[11][4] = INK
    rows[5][11] = INK
    return rows


def post_texture():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = WOOD[3] if (x + y * 3) % 7 == 0 else WOOD[(x // 4) % 3]
            row.append(shade)
        rows.append(row)
    return rows


def rope_texture():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            row.append(ROPE[(x + y) % 3])
        rows.append(row)
    return rows


IRON = [rgb(0x8C8C94), rgb(0x76767E), rgb(0xA6A6AE), rgb(0x5E5E66)]


def panels(seed):
    """Copper plates riveted together."""
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = COPPER[(x // 8 + y // 8 + seed) % 2]
            if x % 8 == 7 or y % 8 == 7:
                shade = COPPER[3]
            elif (x % 8, y % 8) in ((1, 1), (5, 1), (1, 5), (5, 5)):
                shade = COPPER[2]
            row.append(shade)
        rows.append(row)
    return rows


def station_side():
    return panels(0)


def station_front():
    rows = panels(1)
    # A signal window with a clock face and rails underneath.
    rect(rows, 2, 3, 13, 10, COPPER[3])
    rect(rows, 3, 4, 12, 9, rgb(0x2B1D12))
    for (x, y) in [(7, 5), (8, 5), (7, 6), (8, 6)]:
        rows[y][x] = PARCHMENT
    rows[7][7] = INK
    rows[5][5] = TEAL
    rows[5][10] = TEAL
    for x in range(2, 14):
        rows[12][x] = IRON[0]
        rows[13][x] = IRON[3]
    for x in range(3, 14, 3):
        rows[11][x] = WOOD[1]
        rows[14][x] = WOOD[1]
    return rows


def station_top():
    rows = panels(2)
    for x in range(16):
        rows[7][x] = IRON[0]
        rows[8][x] = IRON[3]
    for x in range(0, 16, 3):
        rows[6][x] = WOOD[1]
        rows[9][x] = WOOD[1]
    return rows


write_png(os.path.join(A, "textures", "block", "station_side.png"), station_side())
write_png(os.path.join(A, "textures", "block", "station_front.png"), station_front())
write_png(os.path.join(A, "textures", "block", "station_top.png"), station_top())
write_png(os.path.join(A, "textures", "block", "kontor_side.png"), kontor_side())
write_png(os.path.join(A, "textures", "block", "kontor_front.png"), kontor_front())
write_png(os.path.join(A, "textures", "block", "kontor_top.png"), kontor_top())
write_png(os.path.join(A, "textures", "block", "mooring_post.png"), post_texture())
write_png(os.path.join(A, "textures", "block", "mooring_rope.png"), rope_texture())

# --- models, blockstates, item definitions ---

write_json(
    os.path.join(A, "models", "block", "kontor.json"),
    {
        "parent": "minecraft:block/orientable",
        "textures": {
            "top": f"{NS}:block/kontor_top",
            "front": f"{NS}:block/kontor_front",
            "side": f"{NS}:block/kontor_side",
        },
    },
)


write_json(
    os.path.join(A, "models", "block", "station.json"),
    {
        "parent": "minecraft:block/orientable",
        "textures": {
            "top": f"{NS}:block/station_top",
            "front": f"{NS}:block/station_front",
            "side": f"{NS}:block/station_side",
        },
    },
)


def box(frm, to, texture):
    faces = {face: {"texture": texture} for face in ["north", "south", "east", "west", "up", "down"]}
    return {"from": frm, "to": to, "faces": faces}


write_json(
    os.path.join(A, "models", "block", "mooring_post.json"),
    {
        "parent": "minecraft:block/block",
        "textures": {"post": f"{NS}:block/mooring_post", "rope": f"{NS}:block/mooring_rope", "particle": f"{NS}:block/mooring_post"},
        "elements": [
            box([5, 0, 5], [11, 12, 11], "#post"),
            box([4, 12, 4], [12, 14, 12], "#post"),
            box([4.5, 4, 4.5], [11.5, 6, 11.5], "#rope"),
            box([4.5, 8, 4.5], [11.5, 10, 11.5], "#rope"),
        ],
        "display": {
            "gui": {"rotation": [30, 45, 0], "scale": [0.7, 0.7, 0.7]},
            "ground": {"scale": [0.5, 0.5, 0.5], "translation": [0, 3, 0]},
        },
    },
)

variants = {}
for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
    variant = {"model": f"{NS}:block/kontor"}
    if y:
        variant["y"] = y
    variants[f"facing={facing}"] = variant
write_json(os.path.join(A, "blockstates", "kontor.json"), {"variants": variants})
station_variants = {}
for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
    variant = {"model": f"{NS}:block/station"}
    if y:
        variant["y"] = y
    station_variants[f"facing={facing}"] = variant
write_json(os.path.join(A, "blockstates", "station.json"), {"variants": station_variants})
write_json(os.path.join(A, "blockstates", "mooring_post.json"), {"variants": {"": {"model": f"{NS}:block/mooring_post"}}})
for name in ("kontor", "station", "mooring_post"):
    write_json(os.path.join(A, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})

# --- data ---

for name in ("kontor", "station", "mooring_post"):
    entry = {"type": "minecraft:item", "name": f"{NS}:{name}"}
    if name in ("kontor", "station"):
        entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity", "include": ["minecraft:custom_name"]}]
    write_json(
        os.path.join(D, "loot_table", "blocks", f"{name}.json"),
        {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        },
    )

write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "axe.json"), {"values": [f"{NS}:kontor", f"{NS}:mooring_post"]})
write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"), {"values": [f"{NS}:station"]})
write_json(
    os.path.join(D, "recipe", "station.json"),
    {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"C": "minecraft:copper_ingot", "B": "minecraft:book", "R": "minecraft:rail", "A": "minecraft:barrel"},
        "pattern": ["CBC", "RAR", "CCC"],
        "result": {"id": f"{NS}:station", "count": 1},
    },
)

write_json(
    os.path.join(D, "recipe", "kontor.json"),
    {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"C": "minecraft:copper_ingot", "B": "minecraft:book", "P": "#minecraft:planks", "R": "minecraft:barrel"},
        "pattern": ["CBC", "PRP", "PPP"],
        "result": {"id": f"{NS}:kontor", "count": 1},
    },
)
write_json(
    os.path.join(D, "recipe", "mooring_post.json"),
    {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"C": "minecraft:copper_ingot", "P": "#minecraft:planks", "S": "minecraft:string"},
        "pattern": ["S", "P", "C"],
        "result": {"id": f"{NS}:mooring_post", "count": 2},
    },
)

# --- guide advancements ---

STEPS = [
    ("kontor", "root", f"{NS}:kontor"),
    ("post", "kontor", f"{NS}:mooring_post"),
    ("captain", "post", "minecraft:spyglass"),
    ("dockhand", "captain", "minecraft:string"),
    ("station", "kontor", f"{NS}:station"),
    ("conductor", "station", "minecraft:clock"),
    ("train", "conductor", "minecraft:chest_minecart"),
    ("route", "captain", "minecraft:oak_chest_boat"),
    ("voyage", "route", "minecraft:oak_boat"),
]
write_json(
    os.path.join(D, "advancement", "guide", "root.json"),
    {
        "criteria": {"joined": {"trigger": "minecraft:tick"}},
        "display": {
            "icon": {"id": f"{NS}:kontor"},
            "title": {"translate": f"advancements.{NS}.guide.root.title"},
            "description": {"translate": f"advancements.{NS}.guide.root.description"},
            "frame": "task",
            "background": "minecraft:block/exposed_copper",
            "show_toast": False,
            "announce_to_chat": False,
        },
        "requirements": [["joined"]],
    },
)
for step, parent, icon in STEPS:
    write_json(
        os.path.join(D, "advancement", "guide", f"{step}.json"),
        {
            "parent": f"{NS}:guide/{parent}",
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancements.{NS}.guide.{step}.title"},
                "description": {"translate": f"advancements.{NS}.guide.{step}.description"},
                "frame": "goal" if step == "voyage" else "task",
            },
            "requirements": [["done"]],
        },
    )

# --- language ---

EN = {
    f"block.{NS}.kontor": "Harbour Kontor",
    f"block.{NS}.mooring_post": "Mooring Post",
    f"container.{NS}.kontor": "Kontor",
    f"container.{NS}.routes": "Routes: %s",
    f"job.{NS}.captain": "Captain",
    f"message.{NS}.needs_spyglass": "Only a captain (a golem holding a spyglass) or a dockhand (holding string) can be linked to a kontor",
    f"message.{NS}.captain_at_sea": "This captain is out on a voyage",
    f"message.{NS}.captain_linked": "This captain now works for %s",
    f"message.{NS}.captain_ready": "Captain ready: it sails whenever a route of its harbour needs a boat",
    f"message.{NS}.captain_needs_harbour": "Now link the captain to a kontor: sneak + right-click it with an empty hand, then right-click the kontor",
    f"screen.{NS}.no_routes": "No routes yet",
    f"screen.{NS}.no_routes_hint": "Press + to add one (needs a second kontor)",
    f"screen.{NS}.route": "Route %s / %s",
    f"screen.{NS}.to": "Destination",
    f"screen.{NS}.runs": "Runs: %s",
    f"screen.{NS}.forever": "forever",
    f"screen.{NS}.loop": "Loop",
    f"screen.{NS}.every": "Every: %s",
    f"screen.{NS}.cargo": "Cargo",
    f"screen.{NS}.cargo_line": "%s x %s",
    f"screen.{NS}.cargo_pick": "click a slot",
    f"screen.{NS}.cargo_everything": "everything in the kontor",
    f"screen.{NS}.all": "all",
    f"screen.{NS}.remove": "Remove",
    f"screen.{NS}.run_now": "Run now",
    f"screen.{NS}.stop": "Stop",
    f"screen.{NS}.active": "Active",
    f"screen.{NS}.paused": "Paused",
    f"screen.{NS}.trigger.timer": "Start: timer",
    f"screen.{NS}.trigger.redstone": "Start: redstone",
    f"screen.{NS}.wait.yes": "Full cargo",
    f"screen.{NS}.wait.no": "Any cargo",
    f"screen.{NS}.stats": "%s trips, %s items",
    f"screen.{NS}.interval.0": "at once",
    f"screen.{NS}.interval.1": "1 min",
    f"screen.{NS}.interval.2": "3 min",
    f"screen.{NS}.interval.3": "5 min",
    f"screen.{NS}.interval.4": "10 min",
    f"screen.{NS}.interval.5": "20 min (a day)",
    f"screen.{NS}.interval.6": "60 min (3 days)",
    f"screen.{NS}.status.paused": "Paused",
    f"screen.{NS}.status.waiting_timer": "Waiting for the timer",
    f"screen.{NS}.status.waiting_redstone": "Waiting for redstone",
    f"screen.{NS}.status.finished": "All runs done",
    f"screen.{NS}.status.no_destination": "No destination harbour",
    f"screen.{NS}.status.no_stock": "Waiting for goods",
    f"screen.{NS}.status.no_captain": "No idle captain",
    f"screen.{NS}.status.no_boat": "No free chest boat",
    f"screen.{NS}.status.no_berth": "No berth free",
    f"screen.{NS}.status.loading": "Loading a boat",
    f"screen.{NS}.status.sailing": "Boat at sea",
    f"advancements.{NS}.guide.root.title": "Habour: Harbours & Railways",
    f"advancements.{NS}.guide.root.description": "Harbours, kontors and golem captains: goods by water",
    f"advancements.{NS}.guide.kontor.title": "Open for Business",
    f"advancements.{NS}.guide.kontor.description": "Place a Harbour Kontor",
    f"advancements.{NS}.guide.post.title": "Tie Up Here",
    f"advancements.{NS}.guide.post.description": "Place a Mooring Post at the water's edge, near your kontor",
    f"advancements.{NS}.guide.captain.title": "Captain on Deck",
    f"advancements.{NS}.guide.captain.description": "Give a golem a spyglass and link it to a kontor",
    f"advancements.{NS}.guide.route.title": "Cast Off",
    f"advancements.{NS}.guide.route.description": "Set up a route and watch a captain get to work",
    f"advancements.{NS}.guide.voyage.title": "Land Ahoy",
    f"advancements.{NS}.guide.voyage.description": "See a captain moor a loaded boat at another harbour",
    f"handbook.{NS}.harbour.title": "Harbours and Kontors",
    f"handbook.{NS}.harbour.text": (
        "The Habour add-on lets golems ship goods between harbours.\n\n"
        "# Kontor\n"
        "The kontor is the warehouse and office of a harbour. Right-click it to open its 54 slots (hoppers, funnels and chutes work on it). "
        "Sneak + right-click opens the route book. Rename it in an anvil to name the harbour.\n\n"
        "# Mooring posts\n"
        "Place mooring posts at the water's edge within 24 blocks of the kontor. Boats moor in the water next to them. "
        "Without any post, boats lie in the water around the kontor itself.\n\n"
        "# Boats\n"
        "Put chest boats on the water at the harbour. Every boat needs its own captain, so a big shipment needs several boats and several captains."
    ),
    f"handbook.{NS}.routes.title": "Routes",
    f"handbook.{NS}.routes.text": (
        "A route sends goods from this kontor to another one. Open the route book (sneak + right-click the kontor) and press +.\n\n"
        "# Settings\n"
        "Destination: any other kontor in the same dimension. Cargo: click a slot with an item in your hand to set what is shipped, then use the buttons to set the amount. "
        "Right-click a slot to remove it. With no cargo set, everything in the kontor is shipped.\n\n"
        "Runs: once, a number of times or forever. Every: the time between two runs. Start: by timer or by a redstone pulse on the kontor. "
        "Wait for full cargo: only start when all the amounts are in stock; otherwise send what is there.\n\n"
        "Run now starts a run at once. Stop calls the current run off.\n\n"
        "Routes also work while you are far away: the harbour wakes up now and then to look at its routes."
    ),
    f"handbook.{NS}.captains.title": "Captains",
    f"handbook.{NS}.captains.text": (
        "A captain is a copper golem holding a spyglass. Right-click a golem with a spyglass, then sneak + right-click the golem with an empty hand and right-click the kontor of its harbour.\n\n"
        "# A voyage\n"
        "The captain never carries cargo: the dockhands of the harbour load the chest boat. Then the captain climbs in, sails to the other harbour and moors at a free berth, and the dockhands there carry the cargo into the other kontor. "
        "The empty boat sails home.\n\n"
        "Captains tire and rust like other working golems. They only start a voyage by day, but finish it at night.\n\n"
        "If the other kontor stays full, the captain brings the rest home again."
    ),
    f"handbook.{NS}.sailing.title": "Sailing",
    f"handbook.{NS}.sailing.text": (
        "Captains look for a way over the water on their own and sail around headlands and bays that lead nowhere. "
        "The water must be one level: they cannot pass locks, ice or bridges lower than three blocks. "
        "While a boat is at sea, the chunks around it stay loaded.\n\n"
        "If a captain finds no way, it turns back and brings the cargo home.\n\n"
        "Ask the server admin for /harbour list and /harbour info to see what the harbours are up to."
    ),
}

DE = {
    f"block.{NS}.kontor": "Hafenkontor",
    f"block.{NS}.mooring_post": "Poller",
    f"container.{NS}.kontor": "Kontor",
    f"container.{NS}.routes": "Routen: %s",
    f"job.{NS}.captain": "Kapitän",
    f"message.{NS}.needs_spyglass": "Nur ein Kapitän (ein Golem mit Fernrohr) kann mit einem Kontor verbunden werden",
    f"message.{NS}.captain_at_sea": "Dieser Kapitän ist auf Fahrt",
    f"message.{NS}.captain_linked": "Dieser Kapitän arbeitet jetzt für %s",
    f"message.{NS}.captain_ready": "Kapitän bereit: Er segelt, sobald eine Route seines Hafens ein Boot braucht",
    f"message.{NS}.captain_needs_harbour": "Verbinde den Kapitän jetzt mit einem Kontor: Mit leerer Hand Schleichen + Rechtsklick auf ihn, dann Rechtsklick auf das Kontor",
    f"screen.{NS}.no_routes": "Noch keine Routen",
    f"screen.{NS}.no_routes_hint": "Mit + eine anlegen (braucht ein zweites Kontor)",
    f"screen.{NS}.route": "Route %s / %s",
    f"screen.{NS}.to": "Ziel",
    f"screen.{NS}.runs": "Fahrten: %s",
    f"screen.{NS}.forever": "endlos",
    f"screen.{NS}.loop": "Endlos",
    f"screen.{NS}.every": "Alle: %s",
    f"screen.{NS}.cargo": "Fracht",
    f"screen.{NS}.cargo_line": "%s x %s",
    f"screen.{NS}.cargo_pick": "Slot anklicken",
    f"screen.{NS}.cargo_everything": "alles im Kontor",
    f"screen.{NS}.all": "alle",
    f"screen.{NS}.remove": "Löschen",
    f"screen.{NS}.run_now": "Jetzt",
    f"screen.{NS}.stop": "Stopp",
    f"screen.{NS}.active": "Aktiv",
    f"screen.{NS}.paused": "Pausiert",
    f"screen.{NS}.trigger.timer": "Start: Zeit",
    f"screen.{NS}.trigger.redstone": "Start: Redstone",
    f"screen.{NS}.wait.yes": "Ganz voll",
    f"screen.{NS}.wait.no": "Teilfracht",
    f"screen.{NS}.stats": "%s Fahrten, %s Items",
    f"screen.{NS}.interval.0": "sofort",
    f"screen.{NS}.interval.1": "1 Min.",
    f"screen.{NS}.interval.2": "3 Min.",
    f"screen.{NS}.interval.3": "5 Min.",
    f"screen.{NS}.interval.4": "10 Min.",
    f"screen.{NS}.interval.5": "20 Min. (ein Tag)",
    f"screen.{NS}.interval.6": "60 Min. (3 Tage)",
    f"screen.{NS}.status.paused": "Pausiert",
    f"screen.{NS}.status.waiting_timer": "Wartet auf die Zeit",
    f"screen.{NS}.status.waiting_redstone": "Wartet auf Redstone",
    f"screen.{NS}.status.finished": "Alle Fahrten erledigt",
    f"screen.{NS}.status.no_destination": "Kein Zielhafen",
    f"screen.{NS}.status.no_stock": "Wartet auf Waren",
    f"screen.{NS}.status.no_captain": "Kein freier Kapitän",
    f"screen.{NS}.status.no_boat": "Kein freies Truhenboot",
    f"screen.{NS}.status.no_berth": "Kein Liegeplatz frei",
    f"screen.{NS}.status.loading": "Ein Boot wird beladen",
    f"screen.{NS}.status.sailing": "Boot auf See",
    f"advancements.{NS}.guide.root.title": "Habour: Harbours & Railways",
    f"advancements.{NS}.guide.root.description": "Häfen, Kontore und Golem-Kapitäne: Waren auf dem Wasserweg",
    f"advancements.{NS}.guide.kontor.title": "Geöffnet",
    f"advancements.{NS}.guide.kontor.description": "Platziere ein Hafenkontor",
    f"advancements.{NS}.guide.post.title": "Hier festmachen",
    f"advancements.{NS}.guide.post.description": "Platziere einen Poller am Wasser, nahe deinem Kontor",
    f"advancements.{NS}.guide.captain.title": "Kapitän an Deck",
    f"advancements.{NS}.guide.captain.description": "Gib einem Golem ein Fernrohr und verbinde ihn mit einem Kontor",
    f"advancements.{NS}.guide.route.title": "Leinen los",
    f"advancements.{NS}.guide.route.description": "Lege eine Route an und sieh einem Kapitän bei der Arbeit zu",
    f"advancements.{NS}.guide.voyage.title": "Land in Sicht",
    f"advancements.{NS}.guide.voyage.description": "Sieh, wie ein Kapitän ein beladenes Boot in einem anderen Hafen festmacht",
    f"handbook.{NS}.harbour.title": "Häfen und Kontore",
    f"handbook.{NS}.harbour.text": (
        "Das Habour-Add-on lässt Golems Waren zwischen Häfen verschiffen.\n\n"
        "# Kontor\n"
        "Das Kontor ist Lager und Büro eines Hafens. Rechtsklick öffnet die 54 Slots (Trichter, Funnels und Chutes funktionieren). "
        "Schleichen + Rechtsklick öffnet das Routenbuch. Benenne es im Amboss, um dem Hafen einen Namen zu geben.\n\n"
        "# Poller\n"
        "Platziere Poller am Wasser, höchstens 24 Blöcke vom Kontor entfernt. Boote liegen im Wasser neben ihnen. "
        "Ohne Poller liegen die Boote im Wasser rund ums Kontor.\n\n"
        "# Boote\n"
        "Setze Truhenboote ins Wasser des Hafens. Jedes Boot braucht einen eigenen Kapitän: Für große Lieferungen brauchst du mehrere Boote und Kapitäne."
    ),
    f"handbook.{NS}.routes.title": "Routen",
    f"handbook.{NS}.routes.text": (
        "Eine Route schickt Waren von diesem Kontor zu einem anderen. Öffne das Routenbuch (Schleichen + Rechtsklick aufs Kontor) und drücke +.\n\n"
        "# Einstellungen\n"
        "Ziel: jedes andere Kontor in derselben Dimension. Fracht: Klicke einen Slot mit einem Item in der Hand an, um festzulegen, was verschifft wird, und stelle die Menge mit den Knöpfen ein. "
        "Rechtsklick auf einen Slot entfernt ihn. Ohne Fracht wird alles im Kontor verschifft.\n\n"
        "Fahrten: einmal, mehrmals oder endlos. Alle: die Zeit zwischen zwei Fahrten. Start: per Zeit oder per Redstone-Impuls am Kontor. "
        "Auf volle Fracht warten: nur starten, wenn alle Mengen vorrätig sind; sonst wird geschickt, was da ist.\n\n"
        "Jetzt startet sofort eine Fahrt. Stopp bricht die laufende ab.\n\n"
        "Routen laufen auch, wenn du weit weg bist: Der Hafen wacht von Zeit zu Zeit auf und schaut nach seinen Routen."
    ),
    f"handbook.{NS}.captains.title": "Kapitäne",
    f"handbook.{NS}.captains.text": (
        "Ein Kapitän ist ein Kupfergolem mit Fernrohr. Gib einem Golem ein Fernrohr (Rechtsklick), dann Schleichen + Rechtsklick mit leerer Hand auf den Golem und Rechtsklick aufs Kontor seines Hafens.\n\n"
        "# Eine Fahrt\n"
        "Der Kapitän geht zum Kontor, nimmt die Fracht je 16 Items und trägt sie zu einem Truhenboot. Dann steigt er ein, segelt zum anderen Hafen, legt an einem freien Liegeplatz an und trägt die Fracht ins andere Kontor. "
        "Das leere Boot segelt heim.\n\n"
        "Kapitäne ermüden und rosten wie andere arbeitende Golems. Sie starten eine Fahrt nur tagsüber, beenden sie aber auch nachts.\n\n"
        "Bleibt das andere Kontor voll, bringt der Kapitän den Rest wieder heim."
    ),
    f"handbook.{NS}.sailing.title": "Segeln",
    f"handbook.{NS}.sailing.text": (
        "Kapitäne suchen selbst einen Weg übers Wasser und umsegeln Landzungen und Buchten, die nirgendwohin führen. "
        "Das Wasser muss auf einer Höhe liegen: Schleusen, Eis oder Brücken, die niedriger als drei Blöcke sind, können sie nicht passieren. "
        "Solange ein Boot auf See ist, bleiben die Chunks um es herum geladen.\n\n"
        "Findet ein Kapitän keinen Weg, kehrt er um und bringt die Fracht heim.\n\n"
        "Server-Admins sehen mit /harbour list und /harbour info, was die Häfen treiben."
    ),
}


EN.update({
    f"block.{NS}.station": "Railway Station",
    f"job.{NS}.conductor": "Conductor",
    f"message.{NS}.conductor_linked": "This conductor now works for %s",
    f"message.{NS}.conductor_ready": "Conductor ready: it drives the trains of its station",
    f"message.{NS}.conductor_needs_harbour": "Now link the conductor to a station: sneak + right-click it with an empty hand, then right-click the station",
    f"message.{NS}.captain_not_station": "A captain sails from a kontor; a station needs a conductor (a golem holding a clock)",
    f"message.{NS}.conductor_not_harbour": "A conductor works at a station; a kontor needs a captain (a golem holding a spyglass)",
    f"advancements.{NS}.guide.station.title": "All Aboard",
    f"advancements.{NS}.guide.station.description": "Place a Railway Station next to a track",
    f"advancements.{NS}.guide.conductor.title": "Ticket Please",
    f"advancements.{NS}.guide.conductor.description": "Give a golem a clock and link it to a station",
    f"advancements.{NS}.guide.train.title": "Coupled Up",
    f"advancements.{NS}.guide.train.description": "See a conductor chain more carts to a train",
    f"handbook.{NS}.railways.title": "Railways",
    f"handbook.{NS}.railways.text": (
        "Goods can travel by rail too. Place a **Railway Station** next to your track (it works like the kontor: right-click for storage, sneak + right-click for the route book) and put copper chest minecarts on the rails within 24 blocks of it.\n\n"
        "# Conductors\n"
        "A conductor is a copper golem holding a clock, linked to a station like a captain is to a kontor. It sits in the lead cart and drives the train along the track to the other station. Dockhands load and unload the carts.\n\n"
        "# Trains\n"
        "A train is a lead cart with other carts chained behind it. If a route has more cargo than one cart holds, the conductor couples free carts that stand close by, using copper chains from the station's storage: keep some chains in the station. A train takes up to five carts.\n\n"
        "# Track\n"
        "Trains follow the rails, around curves and over slopes. Junctions are not switched: a train only takes the way the rails point. At the far end the conductor turns the train round by chaining the last cart to the front. Keep the track clear of other carts."
    ),
    f"screen.{NS}.phase.0": "coupling carts",
    f"screen.{NS}.phase.1": "loading",
    f"screen.{NS}.phase.2": "on the way out",
    f"screen.{NS}.phase.3": "unloading",
    f"screen.{NS}.phase.4": "on the way home",
    f"screen.{NS}.phase.5": "unloading at home",
    f"screen.{NS}.status.no_dockhand": "No dockhand to carry the cargo",
    f"screen.{NS}.status.no_conductor": "No idle conductor",
    f"screen.{NS}.status.no_train": "No free chest minecart",
    f"screen.{NS}.status.no_track": "No track free",
    f"job.{NS}.dockhand": "Dockhand",
    f"message.{NS}.dockhand_linked": "This dockhand now works for %s",
    f"message.{NS}.dockhand_ready": "Dockhand ready: it loads and unloads the boats at its harbour",
    f"message.{NS}.dockhand_needs_harbour": "Now link the dockhand to a kontor: sneak + right-click it with an empty hand, then right-click the kontor",
    f"message.{NS}.locate": "Captain at %s %s %s: %s blocks %s of you, %s (seen %s s ago). It glows for 30 seconds",
    f"message.{NS}.locate_none": "No captain of this route is out at sea",
    f"screen.{NS}.locate": "Locate",
    f"screen.{NS}.fleet": "Captain:",
    f"screen.{NS}.fleet_none": "none out at sea",
    f"screen.{NS}.fleet_place": "%s blocks %s of the kontor",
    f"screen.{NS}.fleet_old": "%s, seen %s s ago",
    f"screen.{NS}.fleet_more": "%s (+%s more)",
    f"screen.{NS}.dir.0": "north",
    f"screen.{NS}.dir.1": "north-east",
    f"screen.{NS}.dir.2": "east",
    f"screen.{NS}.dir.3": "south-east",
    f"screen.{NS}.dir.4": "south",
    f"screen.{NS}.dir.5": "south-west",
    f"screen.{NS}.dir.6": "west",
    f"screen.{NS}.dir.7": "north-west",
    f"advancements.{NS}.guide.dockhand.title": "Many Hands",
    f"advancements.{NS}.guide.dockhand.description": "Give a golem a piece of string and link it to a kontor: a dockhand",
    f"handbook.{NS}.dockhands.title": "Dockhands",
    f"handbook.{NS}.dockhands.text": (
        "A dockhand is a copper golem holding a piece of string. Make one like a captain: right-click a golem with string, then sneak + right-click it with an empty hand and right-click the kontor of its harbour.\n\n"
        "# Work\n"
        "Dockhands do all the carrying at their harbour: they load the boats that wait for a captain and unload the boats that moor there, 16 items at a time. Without a dockhand no boat is sent. "
        "The captain waits by its boat until they are done. Several dockhands can work on one boat. A dockhand never sails.\n\n"
        "Dockhands tire and rust like other working golems.\n\n"
        "# Where is my captain?\n"
        "The route book shows where the captain of the selected route is. The Locate button makes it glow and tells you where it is."
    ),
})

DE.update({
    f"block.{NS}.station": "Bahnhof",
    f"job.{NS}.conductor": "Schaffner",
    f"message.{NS}.conductor_linked": "Dieser Schaffner arbeitet jetzt für %s",
    f"message.{NS}.conductor_ready": "Schaffner bereit: Er fährt die Züge seines Bahnhofs",
    f"message.{NS}.conductor_needs_harbour": "Verbinde den Schaffner jetzt mit einem Bahnhof: Mit leerer Hand Schleichen + Rechtsklick auf ihn, dann Rechtsklick auf den Bahnhof",
    f"message.{NS}.captain_not_station": "Ein Kapitän fährt von einem Kontor aus; ein Bahnhof braucht einen Schaffner (Golem mit Uhr)",
    f"message.{NS}.conductor_not_harbour": "Ein Schaffner arbeitet an einem Bahnhof; ein Kontor braucht einen Kapitän (Golem mit Fernrohr)",
    f"advancements.{NS}.guide.station.title": "Einsteigen bitte",
    f"advancements.{NS}.guide.station.description": "Stelle einen Bahnhof neben ein Gleis",
    f"advancements.{NS}.guide.conductor.title": "Die Fahrkarten, bitte",
    f"advancements.{NS}.guide.conductor.description": "Gib einem Golem eine Uhr und verbinde ihn mit einem Bahnhof",
    f"advancements.{NS}.guide.train.title": "Angekoppelt",
    f"advancements.{NS}.guide.train.description": "Sieh zu, wie ein Schaffner weitere Loren an einen Zug kettet",
    f"handbook.{NS}.railways.title": "Eisenbahn",
    f"handbook.{NS}.railways.text": (
        "Waren reisen auch auf Schienen. Stelle einen **Bahnhof** neben dein Gleis (er funktioniert wie das Kontor: Rechtsklick für das Lager, Schleichen + Rechtsklick für das Routenbuch) und setze Kupfer-Truhenloren auf die Schienen im Umkreis von 24 Blöcken.\n\n"
        "# Schaffner\n"
        "Ein Schaffner ist ein Kupfergolem mit einer Uhr, der wie ein Kapitän mit einem Bahnhof verbunden wird. Er sitzt in der vordersten Lore und fährt den Zug über das Gleis zum anderen Bahnhof. Hafenarbeiter be- und entladen die Loren.\n\n"
        "# Züge\n"
        "Ein Zug ist eine vordere Lore mit weiteren Loren dahinter, die angekettet sind. Hat eine Route mehr Fracht als eine Lore fasst, kettet der Schaffner freie Loren in der Nähe mit Kupferketten aus dem Lager des Bahnhofs an: Leg also einige Ketten in den Bahnhof. Ein Zug hat bis zu fünf Loren.\n\n"
        "# Gleis\n"
        "Züge folgen den Schienen, durch Kurven und über Steigungen. Weichen werden nicht gestellt: Ein Zug nimmt nur den Weg, den die Schienen zeigen. Am Ende dreht der Schaffner den Zug, indem er die letzte Lore nach vorn kettet. Halte das Gleis frei von anderen Loren."
    ),
    f"screen.{NS}.phase.0": "koppelt Loren",
    f"screen.{NS}.phase.1": "lädt",
    f"screen.{NS}.phase.2": "unterwegs",
    f"screen.{NS}.phase.3": "entlädt",
    f"screen.{NS}.phase.4": "fährt heim",
    f"screen.{NS}.phase.5": "entlädt zu Hause",
    f"screen.{NS}.status.no_dockhand": "Kein Hafenarbeiter zum Tragen",
    f"screen.{NS}.status.no_conductor": "Kein freier Schaffner",
    f"screen.{NS}.status.no_train": "Keine freie Truhenlore",
    f"screen.{NS}.status.no_track": "Kein Gleis frei",
    f"job.{NS}.dockhand": "Hafenarbeiter",
    f"message.{NS}.dockhand_linked": "Dieser Hafenarbeiter arbeitet jetzt für %s",
    f"message.{NS}.dockhand_ready": "Hafenarbeiter bereit: Er be- und entlädt die Boote seines Hafens",
    f"message.{NS}.dockhand_needs_harbour": "Verbinde den Hafenarbeiter jetzt mit einem Kontor: Mit leerer Hand Schleichen + Rechtsklick auf ihn, dann Rechtsklick auf das Kontor",
    f"message.{NS}.needs_spyglass": "Nur ein Kapitän (Golem mit Fernrohr) oder ein Hafenarbeiter (mit Faden) kann mit einem Kontor verbunden werden",
    f"message.{NS}.locate": "Kapitän bei %s %s %s: %s Blöcke %s von dir, %s (vor %s s gesehen). Er leuchtet 30 Sekunden lang",
    f"message.{NS}.locate_none": "Kein Kapitän dieser Route ist auf See",
    f"screen.{NS}.locate": "Orten",
    f"screen.{NS}.fleet": "Kapitän:",
    f"screen.{NS}.fleet_none": "keiner auf See",
    f"screen.{NS}.fleet_place": "%s Blöcke %s des Kontors",
    f"screen.{NS}.fleet_old": "%s, vor %s s gesehen",
    f"screen.{NS}.fleet_more": "%s (+%s weitere)",
    f"screen.{NS}.dir.0": "nördlich",
    f"screen.{NS}.dir.1": "nordöstlich",
    f"screen.{NS}.dir.2": "östlich",
    f"screen.{NS}.dir.3": "südöstlich",
    f"screen.{NS}.dir.4": "südlich",
    f"screen.{NS}.dir.5": "südwestlich",
    f"screen.{NS}.dir.6": "westlich",
    f"screen.{NS}.dir.7": "nordwestlich",
    f"advancements.{NS}.guide.dockhand.title": "Viele Hände",
    f"advancements.{NS}.guide.dockhand.description": "Gib einem Golem einen Faden und verbinde ihn mit einem Kontor: ein Hafenarbeiter",
    f"handbook.{NS}.dockhands.title": "Hafenarbeiter",
    f"handbook.{NS}.dockhands.text": (
        "Ein Hafenarbeiter ist ein Kupfergolem mit einem Faden. Mach einen wie einen Kapitän: Rechtsklick mit Faden auf einen Golem, dann mit leerer Hand Schleichen + Rechtsklick auf ihn und Rechtsklick auf das Kontor seines Hafens.\n\n"
        "# Arbeit\n"
        "Hafenarbeiter erledigen das ganze Tragen in ihrem Hafen: Sie beladen Boote, die auf einen Kapitän warten, und entladen Boote, die dort anlegen, je 16 Items. "
        "Der Kapitän wartet an seinem Boot, bis sie fertig sind. Mehrere Hafenarbeiter können an einem Boot arbeiten. Ein Hafenarbeiter segelt nie.\n\n"
        "Ohne Hafenarbeiter wird kein Boot losgeschickt. Hafenarbeiter ermüden und rosten wie andere Arbeitsgolems.\n\n"
        "# Wo ist mein Kapitän?\n"
        "Das Routenbuch zeigt, wo der Kapitän der gewählten Route ist. Mit dem Knopf Orten leuchtet er und du erfährst, wo er ist."
    ),
})

write_json(os.path.join(A, "lang", "en_us.json"), EN)
write_json(os.path.join(A, "lang", "de_de.json"), DE)
print("done")
