#!/usr/bin/env python3
"""ffprobe natijasini kutilgan teglar bilan solishtiradi.

Nega alohida qadam: `ffprobe` tegni o'qiganini JSON qilib beradi, biz esa
unda aynan nima borligini bilmoqchimiz. Tekshiruv **uch xil** narsani
qamraydi va uchtasi ham kerak:

  1. maydonlar o'qildi (ffprobe ularni ko'rsatdi);
  2. qiymatlar biz yozgan qiymatlarga **aynan** teng (kirill harflari ham
     — noto'g'ri kodlash «?????» bo'lib chiqadi);
  3. fayl baribir chalinadi: ovoz oqimi joyida, davomiyligi o'zgarmagan.

Ishlatish: WORK=... python3 bin/verify-tag-compare.py
"""

import json
import os
import subprocess
import sys

WORK = os.environ.get("WORK", "/tmp/superlisa/tag-verify")


def ffprobe(path):
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", path],
        capture_output=True,
        text=True,
        check=True,
    )
    return json.loads(result.stdout)


def main():
    with open(os.path.join(WORK, "expected.json"), encoding="utf-8") as handle:
        expected = json.load(handle)

    failures = []
    failures += compare_reads(expected["reads"])

    for case in expected["probe"]:
        name = case["name"]
        path = os.path.join(WORK, "cases", f"{name}.mp3")
        if not os.path.exists(path):
            failures.append(f"{name}: fayl yo'q — {path}")
            continue

        info = ffprobe(path)
        tags = {key.lower(): value for key, value in info.get("format", {}).get("tags", {}).items()}

        for field, want in case["tags"].items():
            got = tags.get(field)
            if got != want:
                failures.append(f"{name}: {field} — kutilgan {want!r}, ffprobe {got!r}")

        # Ovoz oqimi: teg yozilgach ham fayl chalinishi kerak.
        audio = [s for s in info.get("streams", []) if s.get("codec_type") == "audio"]
        if not audio:
            failures.append(f"{name}: ovoz oqimi yo'q — teg butun faylni buzgan")
            continue

        duration = float(info["format"].get("duration", 0))
        want_duration = case["duration"]
        if abs(duration - want_duration) > 0.2:
            failures.append(f"{name}: davomiylik {duration} — kutilgan {want_duration}")

        # Muqova alohida oqim bo'lib ko'rinadi.
        pictures = [
            s for s in info.get("streams", [])
            if s.get("disposition", {}).get("attached_pic") == 1
        ]
        # Muqova alohida rasm oqimi bo'lib ko'rinadi. Baytlari o'zgarmaganini
        # skript o'zi tekshiradi (`verify-tag.sh` ichida `cmp`) — ffprobe
        # rasmning mazmunini ko'rsatmaydi, faqat borligini aytadi.
        if case["cover"] and not pictures:
            failures.append(f"{name}: muqova topilmadi")
        if not case["cover"] and pictures:
            failures.append(f"{name}: kutilmagan muqova — eski teg qolib ketgan")

    if failures:
        for line in failures:
            print(f"XATO: {line}")
        sys.exit(1)

    print(
        f"OK: {len(expected['probe'])} ta holat — ffprobe teglarni o'qidi, ovoz joyida; "
        f"{len(expected['reads'])} ta fayl ilovaning o'z o'quvchisi bilan o'qildi"
    )


def compare_reads(cases):
    """Ilovaning o'quvchisi fayldan nima o'qiganini kutilgan qiymatlar bilan solishtiradi.

    Bu tekshiruv boshqa tomondan: yuqoridagi `ffprobe` «teg to'g'ri
    yozilganmi» degan savolga javob beradi, bu esa «ilova uni o'sha holda
    ko'radimi» degan savolga. Ikkisi kerak: yozilgan teg foydalanuvchiga
    yetib bormasa, u yo'q bilan barobar.
    """
    failures = []
    for case in cases:
        path = os.path.join(WORK, f"oqish-{case['name']}.txt")
        if not os.path.exists(path):
            failures.append(f"{case['name']}: o'qish natijasi yo'q — {path}")
            continue

        values = {}
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                key, _, value = line.rstrip("\n").partition("=")
                values[key] = value

        for field, want in case["fields"].items():
            got = values.get(field)
            if got != str(want):
                failures.append(f"{case['name']}: {field} — kutilgan {want!r}, o'quvchi {got!r}")
    return failures


if __name__ == "__main__":
    main()
