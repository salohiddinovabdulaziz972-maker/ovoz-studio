#!/usr/bin/env python3
"""Butun manba kodini bitta matnli faylga yig'adi.

Egasi kodni ko'rib chiqib, kamchiliklarni aytishi uchun kerak: reponi
ochmasdan, bitta faylni o'qish yoki boshqa asbobga berish kifoya.

Fayl tartibi ataylab shunday: avval **nima bilan yig'iladi** (build
fayllari, manifest), keyin **kod** (paketlar bo'yicha), keyin **resurslar**,
keyin **testlar**, oxirida **asboblar**. Har bir fayl oldiga to'liq yo'l
yoziladi — shuning uchun fayl bo'laklarga bo'lib o'qilsa ham, parcha
qaysi fayldan olingani yo'qolmaydi.

Yurgizish:

    python3 bin/dump-source.py [chiqish-fayli]

Standart chiqish yo'li: `workspace/scratch/ovozstudio-kod.txt`. Binary
fayllar (ikonkalar, `.jar`) tushirib qoldiriladi — matn fayl ichida
ma'nosiz.
"""

from __future__ import annotations

import subprocess
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Matn deb hisoblanadigan kengaytmalar. Ro'yxat **to'liq**: yangi turdagi
# fayl qo'shilsa, u ham shu yerga yozilishi kerak — aks holda u jimgina
# tushib qolardi va egasi «kod to'liq emas» deb o'ylardi.
TEXT_SUFFIXES = {
    ".kt", ".kts", ".xml", ".py", ".sh", ".properties", ".toml",
    ".md", ".yml", ".yaml", ".json", ".txt", ".pro", ".gitignore",
}

# Fayl nomi bo'yicha (kengaytmasiz) kiritiladiganlar.
TEXT_NAMES = {"LICENSE", "gitignore", ".gitignore"}

# Tartib: har bir fayl shu ro'yxatdagi birinchi mos qolipga tushadi.
# Tartib mazmunli — build, keyin kod, keyin resurs, keyin test, keyin asbob.
ORDER = [
    ("Yig'ish va sozlash", ["build.gradle.kts", "settings.gradle.kts",
                            "gradle.properties", "gradle/libs.versions.toml",
                            ".github/workflows/"]),
    ("Ilova kodi — kirish nuqtasi", ["app/src/main/AndroidManifest.xml",
                                     "app/src/main/java/uz/ovozstudio/app/MainActivity.kt",
                                     "app/src/main/java/uz/ovozstudio/app/OvozStudioApp.kt"]),
    ("Ilova kodi — yadro (media/)", ["app/src/main/java/uz/ovozstudio/app/media/"]),
    ("Ilova kodi — ekranlar (ui/)", ["app/src/main/java/uz/ovozstudio/app/ui/"]),
    ("Ilova kodi — qolgani", ["app/src/main/java/"]),
    ("Resurslar", ["app/src/main/res/"]),
    ("Testlar", ["app/src/test/"]),
    ("Asboblar (bin/, tools/)", ["bin/", "tools/"]),
    ("Hujjatlar", ["README.md", "docs/", "LICENSE", ".gitignore"]),
]


def tracked_files() -> list[str]:
    out = subprocess.run(["git", "ls-files"], cwd=ROOT,
                         capture_output=True, text=True, check=True)
    return [line for line in out.stdout.splitlines() if line]


def is_text(path: str) -> bool:
    name = Path(path).name
    if name in TEXT_NAMES:
        return True
    return Path(path).suffix in TEXT_SUFFIXES


def section_of(path: str) -> str:
    for title, prefixes in ORDER:
        for prefix in prefixes:
            # Papka qolipi («…/media/») — prefiks bo'yicha; fayl qolipi —
            # aynan mos kelishi shart (aks holda `README.md` har qanday
            # yo'lning oxiriga yopishib ketardi).
            if prefix.endswith("/"):
                if path.startswith(prefix):
                    return title
            elif path == prefix or path.endswith("/" + prefix):
                return title
    return "Boshqa"


def commit_id() -> str:
    out = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT,
                         capture_output=True, text=True)
    return out.stdout.strip() if out.returncode == 0 else "noma'lum"


def build() -> str:
    files = sorted(f for f in tracked_files() if is_text(f))
    by_section: dict[str, list[str]] = {}
    for path in files:
        by_section.setdefault(section_of(path), []).append(path)

    titles = [title for title, _ in ORDER if title in by_section]
    for title in sorted(by_section):
        if title not in titles:
            titles.append(title)

    header = [
        "=" * 78,
        "OVOZ STUDIO — TO'LIQ MANBA KODI",
        "=" * 78,
        "",
        "Loyiha: Professional Audio Tahrirlovchi Ilova (Android, Kotlin + Compose)",
        "Litsenziya: GPL-3.0 — yopiq kodli nusxa yasash mumkin emas",
        f"Sana: {date.today().isoformat()}",
        f"Commit: {commit_id()}",
        f"Fayllar: {len(files)} ta matn fayli, "
        f"{sum(len((ROOT / f).read_text(errors='replace').splitlines()) for f in files)} ta satr",
        "",
        "Bu fayl butun loyihani bitta matnga yig'adi: yig'ish fayllari, ilova",
        "kodi (Kotlin), resurslar (4 til), testlar va tekshiruv asboblari.",
        "Har bir fayl oldiga `===== yo'l =====` sarlavhasi qo'yilgan — parcha",
        "bo'lib o'qilsa ham, qaysi fayldan olingani ko'rinib turadi.",
        "",
        "Ikonkalar (PNG) va `.jar` fayllar tushirib qoldirilgan: ular binary",
        "va matn ichida ma'nosiz. Ular repoda o'z joyida turadi.",
        "",
        "=" * 78,
        "MUNDARIJA",
        "=" * 78,
        "",
    ]

    lines: list[str] = list(header)
    # Mundarija uchun satr raqamlari kerak, lekin ular hali ma'lum emas —
    # shuning uchun avval bo'sh joy qoldiriladi, keyin to'ldiriladi.
    toc_at = len(lines)
    toc_placeholder: list[str] = []
    for title in titles:
        toc_placeholder.append(f"  {title}  ({len(by_section[title])} fayl)")
        for path in by_section[title]:
            toc_placeholder.append(f"      {path}")
    toc_placeholder.append("")
    lines.extend(toc_placeholder)
    toc_lines = len(toc_placeholder)

    offsets: list[tuple[str, int]] = []  # (yo'l, mundarijadagi satr indeksi)
    toc_index: dict[str, int] = {}
    cursor = 0
    for entry in toc_placeholder:
        stripped = entry.strip()
        if stripped.startswith("  ") or not stripped:
            pass
        if stripped and not stripped.endswith(") fayl"):
            toc_index[stripped] = toc_at + cursor
        cursor += 1

    for title in titles:
        lines.append("")
        lines.append("=" * 78)
        lines.append(title.upper())
        lines.append("=" * 78)
        lines.append("")
        for path in by_section[title]:
            toc_index[path] = len(lines)
            lines.append("-" * 78)
            lines.append(f"===== {path} =====")
            lines.append("-" * 78)
            body = (ROOT / path).read_text(errors="replace")
            lines.extend(body.splitlines())
            lines.append("")
            offsets.append((path, len(lines)))

    # Mundarijadagi yo'llarni haqiqiy satr raqamlari bilan almashtiramiz.
    for i, entry in enumerate(toc_placeholder):
        stripped = entry.strip()
        if stripped in toc_index:
            indent = " " * (len(entry) - len(entry.lstrip()))
            lines[toc_at + i] = f"{indent}{stripped}  — {toc_index[stripped]}-satr"

    lines.append("=" * 78)
    lines.append("KOD TUGADI")
    lines.append("=" * 78)
    return "\n".join(lines) + "\n"


def main() -> int:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else (
        Path("/home/superlisa/workspace/scratch/ovozstudio-kod.txt"))
    target.parent.mkdir(parents=True, exist_ok=True)
    text = build()
    target.write_text(text)
    print(f"{target}: {len(text.splitlines())} satr, {len(text)} bayt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
