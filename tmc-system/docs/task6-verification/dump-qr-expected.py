#!/usr/bin/env python3
"""Dump QR matrices cho TẤT CẢ 8 mask với version/mask ép cứng, byte mode.
So sánh chéo từng ô với bộ sinh JS của Task 6."""
import json
import qrcode
from qrcode.constants import (ERROR_CORRECT_H, ERROR_CORRECT_L,
                              ERROR_CORRECT_M, ERROR_CORRECT_Q)
from qrcode.util import QRData, MODE_8BIT_BYTE

LEVELS = {"L": ERROR_CORRECT_L, "M": ERROR_CORRECT_M, "Q": ERROR_CORRECT_Q, "H": ERROR_CORRECT_H}

JWS_LIKE = (
    "eyJhbGciOiJFUzI1NiIsImtpZCI6InFyLWtleS0yMDI2LTExIn0."
    "eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTQwMDAtODAwMC0wMDAwMDAwMDAwMDExIiwiaXNzIjoidG1jLWNlbnRyYWwiLCJhdWQiOiJ0bWMtZ2F0ZSIsImlhdCI6MTc5NDg4MDc4NSwibmJmIjoxNzk0ODgwNzg1LCJleHAiOjE3OTQ4ODA4NDUsImp0aSI6IjAwMDAwMDAwLTAwMDAtNDAwMC04MDAwLTAwMDAwMDAwMDAwMTIifQ."
    "3Jc2Zk8d8zVhE9oYqOa9Xm1fK6oT0dRr7nQ1bVhC5mAeXwJpLlN2uS4gH8vYtZq"
)

# (text, level, version) — version None để python tự chọn (dùng làm minVersion tham chiếu)
CASES = [
    ("HELLO-TMC", "L", None),
    ("HELLO-TMC", "M", None),
    ("HELLO-TMC", "Q", None),
    ("HELLO-TMC", "H", None),
    (JWS_LIKE, "M", None),
    (JWS_LIKE, "H", None),
    (JWS_LIKE, "M", 15),
    (JWS_LIKE, "M", 16),
    (JWS_LIKE, "M", 17),
    ("00000000-0000-4000-8000-000000000011", "Q", 4),
    ("00000000-0000-4000-8000-000000000011", "Q", 7),
    ("00000000-0000-4000-8000-000000000011", "Q", 10),
    ("A" * 300, "L", None),
    ("A" * 300, "M", None),
    ("Trần Văn Tám chấm công IN 07:30", "Q", None),  # UTF-8 multibyte
]

out = []
for text, level, version in CASES:
    for mask in range(8):
        qr = qrcode.QRCode(version=version, error_correction=LEVELS[level],
                           mask_pattern=mask, border=0, box_size=1)
        qr.add_data(QRData(text.encode("utf-8"), mode=MODE_8BIT_BYTE), optimize=0)
        qr.make(fit=version is None)
        matrix = qr.get_matrix()
        out.append({
            "text": text, "level": level, "version": qr.version, "mask": mask,
            "size": len(matrix),
            "rows": ["".join("1" if v else "0" for v in row) for row in matrix],
        })

json.dump(out, open("/home/z/my-project/scripts/qr-expected.json", "w"))
print("cases:", len(out))
