"""Convert the staff Excel sheet into app/src/main/assets/staff.json.

Usage:  python3 tools/xlsx_to_json.py "<staff file>.xlsx"

Every row of the list is one post. Rows named "Vacant" are kept as vacant posts, so
Sanctioned = number of posts, Filled = posts with an officer, Vacant = the rest.
Posts starting with "Deputy" belong to the DSTO cadre, all others to STO.
"""
import json
import re
import sys
from pathlib import Path

import openpyxl

STO = "State Tax Officer"
DSTO = "Deputy State Tax Officer"

src = Path(sys.argv[1])
out = Path(__file__).resolve().parent.parent / "app/src/main/assets/staff.json"

ws = openpyxl.load_workbook(src, data_only=True).worksheets[0]
rows = list(ws.iter_rows(values_only=True))

office = (rows[0][0] or "").strip()
division = (rows[1][0] or "").strip()
m = re.search(r"as on\s+(\d{2}\.\d{2}\.\d{4})", rows[2][0] or "", re.I)
as_on = m.group(1) if m else ""

staff = []
for r in rows:
    if isinstance(r[0], (int, float)) and r[1] and r[2]:
        post = str(r[2]).strip()
        staff.append({
            "sl": len(staff) + 1,
            "group": str(r[1]).strip(),
            "cadre": DSTO if post.lower().startswith("deputy") else STO,
            "post": post,
            "name": str(r[3]).strip(),
            "mobile": str(r[4]).strip() if r[4] else "",
        })

cadres = [{"name": STO, "short": "STO"}, {"name": DSTO, "short": "DSTO"}]
out.write_text(json.dumps(
    {"office": office, "division": division, "asOn": as_on, "cadres": cadres, "staff": staff},
    ensure_ascii=False, indent=1), encoding="utf-8")

for c in cadres:
    mine = [s for s in staff if s["cadre"] == c["name"]]
    filled = sum(1 for s in mine if s["name"].lower() != "vacant")
    print(f'{c["short"]}: sanctioned {len(mine)}, filled {filled}, vacant {len(mine) - filled}')
print(f"{len(staff)} posts saved")
