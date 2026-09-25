"""Gera um DER legível diretamente de INFORMATION_SCHEMA do banco saep_db."""
import os
import re
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "entrega" / "DER.png"
MYSQL = os.environ.get("SAEP_MYSQL_CLIENT", r"C:\xampp\mysql\bin\mysql.exe")
FONT_DIR = Path(r"C:\Windows\Fonts")

def query(sql):
    env = os.environ.copy()
    if "SAEP_DB_PASSWORD" in env:
        env["MYSQL_PWD"] = env["SAEP_DB_PASSWORD"]
    result = subprocess.run(
        [MYSQL, "--protocol=TCP", "--host=127.0.0.1", "--port=3306",
         "--user=" + env.get("SAEP_DB_USER", "root"), "--batch", "--skip-column-names", "--raw", "--execute=" + sql],
        check=True, capture_output=True, text=True, encoding="utf-8", env=env,
    )
    return [line.split("\t") for line in result.stdout.strip().splitlines() if line]

columns = query("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,COLUMN_KEY,IS_NULLABLE "
                "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='saep_db' "
                "ORDER BY TABLE_NAME,ORDINAL_POSITION")
foreign = query("SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME "
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='saep_db' "
                "AND REFERENCED_TABLE_NAME IS NOT NULL")
if len(columns) != 26 or len(foreign) != 3:
    raise SystemExit("Esquema inesperado: confira se saep_db.sql foi importado antes de gerar o DER.")
fk = {(table, column): (ref_table, ref_column) for table, column, ref_table, ref_column in foreign}
tables = {}
for table, column, kind, key, nullable in columns:
    tables.setdefault(table, []).append((column, kind, key, nullable, (table, column) in fk))

W, H = 2200, 1650
img = Image.new("RGB", (W, H), "#F3F7F8")
d = ImageDraw.Draw(img)
font_title = ImageFont.truetype(str(FONT_DIR / "arialbd.ttf"), 49)
font_sub = ImageFont.truetype(str(FONT_DIR / "arial.ttf"), 25)
font_heading = ImageFont.truetype(str(FONT_DIR / "arialbd.ttf"), 32)
font_body = ImageFont.truetype(str(FONT_DIR / "arial.ttf"), 26)
font_type = ImageFont.truetype(str(FONT_DIR / "arial.ttf"), 23)
font_tag = ImageFont.truetype(str(FONT_DIR / "arialbd.ttf"), 16)
font_label = ImageFont.truetype(str(FONT_DIR / "arialbd.ttf"), 23)

NAVY, TEAL, LINE, TEXT, MUTED = "#15384F", "#087D76", "#D8E3E9", "#243B4A", "#6A7B88"
d.text((75, 62), "Diagrama Entidade-Relacionamento", font=font_title, fill=NAVY)
d.text((78, 133), "saep_db  ·  esquema consultado no INFORMATION_SCHEMA  ·  MariaDB 10.4.32 / MySQL", font=font_sub, fill=MUTED)
d.line((78, 203, 2122, 203), fill=LINE, width=3)

def nice_type(value):
    if value == "tinyint(1)": return "BOOLEAN"
    return re.sub(r"^(bigint|int)\(\d+\)$", lambda m: m.group(1).upper(), value).upper()

def box(table, x, y, width):
    rows = tables[table]
    height = 84 + 64 * len(rows)
    d.rectangle((x+9, y+11, x+width+9, y+height+11), fill="#DFE8EC")
    d.rectangle((x, y, x+width, y+height), fill="white", outline=LINE, width=3)
    d.rectangle((x, y, x+width, y+76), fill=NAVY if table != "movimentacoes" else TEAL)
    d.rectangle((x, y+50, x+width, y+76), fill=NAVY if table != "movimentacoes" else TEAL)
    d.text((x+27, y+18), table.upper(), font=font_heading, fill="white")
    for i, (name, kind, key, nullable, is_fk) in enumerate(rows):
        top = y+83+i*64
        if i > 0: d.line((x+25, top-3, x+width-25, top-3), fill="#EDF2F4", width=2)
        tag = "PK/FK" if key == "PRI" and is_fk else "PK" if key == "PRI" else "FK" if is_fk else "UQ" if key == "UNI" else ""
        if tag:
            color = TEAL if tag == "PK" else "#2B6796" if tag == "FK" else "#84569B"
            d.rectangle((x+25, top+12, x+82, top+42), fill=color)
            d.text((x+29, top+16), tag, font=font_tag, fill="white")
        d.text((x+88, top+12), name, font=font_body, fill=TEXT)
        dtype = nice_type(kind)
        bbox = d.textbbox((0, 0), dtype, font=font_type)
        d.text((x+width-25-(bbox[2]-bbox[0]), top+16), dtype, font=font_type, fill=MUTED)
    return (x, y, x+width, y+height)

users = box("usuarios", 75, 345, 540)
movements = box("movimentacoes", 735, 310, 730)
products = box("produtos", 1585, 345, 540)
profiles = box("perfis", 75, 915, 700)

def relationship(left, right, y, source, target):
    color = "#2B6796"
    d.line((left, y, right, y), fill=color, width=5)
    # Duas barras representam exatamente um registro pai.
    for x in (left+18, left+28) if left == users[2] else (right-18, right-28):
        d.line((x, y-17, x, y+17), fill=color, width=4)
    # Pé de galinha junto à tabela de movimentações: zero ou muitos filhos.
    if right == movements[0]:
        x = right-14
        d.line((x-25, y, x, y-20), fill=color, width=4)
        d.line((x-25, y, x, y+20), fill=color, width=4)
        d.ellipse((x-39, y-6, x-27, y+6), outline=color, width=3)
    else:
        x = left+14
        d.line((x+25, y, x, y-20), fill=color, width=4)
        d.line((x+25, y, x, y+20), fill=color, width=4)
        d.ellipse((x+27, y-6, x+39, y+6), outline=color, width=3)
    if source == "usuarios":
        d.text((left+14, y-55), "1", font=font_label, fill=color)
        d.text((right-70, y-55), "0..N", font=font_label, fill=color)
    else:
        d.text((left+8, y-55), "0..N", font=font_label, fill=color)
        d.text((right-35, y-55), "1", font=font_label, fill=color)

relationship(users[2], movements[0], 574, "usuarios", "movimentacoes")
relationship(movements[2], products[0], 574, "produtos", "movimentacoes")

# Um usuário pode ter zero ou um perfil; o perfil pertence a exatamente um usuário.
line_x = 345
d.line((line_x, users[3], line_x, profiles[1]), fill="#2B6796", width=5)
d.line((line_x-18, users[3]+16, line_x+18, users[3]+16), fill="#2B6796", width=4)
d.ellipse((line_x-18, profiles[1]-29, line_x+18, profiles[1]+7), outline="#2B6796", width=4)
d.line((line_x-18, profiles[1]-6, line_x+18, profiles[1]-6), fill="#2B6796", width=4)
d.text((390, 852), "1  :  0..1", font=font_label, fill="#2B6796")

d.rectangle((77, 1490, 2123, 1610), fill="#E7F3F1")
d.text((105, 1510), "Leitura do modelo", font=font_heading, fill=NAVY)
d.text((105, 1562), "PK = chave primária    FK = chave estrangeira    UQ = valor único    ·    Um usuário pode ter 0 ou 1 perfil.", font=font_sub, fill=TEXT)
OUTPUT.parent.mkdir(exist_ok=True)
img.save(OUTPUT)
print(f"DER gerado de {len(tables)} tabelas, {len(columns)} colunas e {len(foreign)} chaves estrangeiras: {OUTPUT}")
