#!/usr/bin/env python3
"""Generate QR tables (RS blocks, EC codewords, data codewords, alignment
positions) from the installed python-qrcode package (standard spec data),
emitted as JSON for frontend/src/lib/qrEncoder.js."""
import json
from qrcode.base import RS_BLOCK_TABLE, RS_BLOCK_OFFSET
from qrcode.util import PATTERN_POSITION_TABLE

# python-qrcode EC level code -> wire level name
LEVELS = [("L", 1), ("M", 0), ("Q", 3), ("H", 2)]

blocks = {}
data_cw = {}
ec_per_block = {}
for name, code in LEVELS:
    off = RS_BLOCK_OFFSET[code]
    rows = {}
    data_total = {}
    ecb = {}
    for v in range(1, 41):
        row = RS_BLOCK_TABLE[(v - 1) * 4 + off]
        groups = []
        total = 0
        data = 0
        ecs = []
        i = 0
        while i < len(row):
            count, totalPer, dataPer = row[i], row[i + 1], row[i + 2]
            groups.append([count, dataPer])
            total += count * totalPer
            data += count * dataPer
            ecs.append(totalPer - dataPer)
            i += 3
        assert len(set(ecs)) == 1, (name, v, ecs)
        rows[str(v)] = groups
        data_total[str(v)] = data
        ecb[str(v)] = ecs[0]
    blocks[name] = rows
    data_cw[name] = data_total
    ec_per_block[name] = ecb

alignment = {}
for idx, pos in enumerate(PATTERN_POSITION_TABLE):
    alignment[str(idx + 1)] = list(pos)

rem = {}
for v in range(1, 41):
    if v == 1: rem[str(v)] = 0
    elif 2 <= v <= 6: rem[str(v)] = 7
    elif 7 <= v <= 13: rem[str(v)] = 0
    elif 14 <= v <= 20: rem[str(v)] = 3
    elif 21 <= v <= 27: rem[str(v)] = 4
    elif 28 <= v <= 34: rem[str(v)] = 3
    else: rem[str(v)] = 0

out = {
    "RS_BLOCKS": blocks,
    "EC_PER_BLOCK": ec_per_block,
    "DATA_CODEWORDS": data_cw,
    "ALIGNMENT": alignment,
    "REMAINDER_BITS": rem,
}

with open("/home/z/my-project/scripts/qr-tables.json", "w") as f:
    json.dump(out, f, separators=(",", ":"))
print("written qr-tables.json")
print("v1 M data cw:", data_cw["M"]["1"], "ec:", ec_per_block["M"]["1"])
print("v40 M groups:", blocks["M"]["40"], "ec:", ec_per_block["M"]["40"])
print("alignment v7:", alignment["7"], "v1:", alignment["1"])
