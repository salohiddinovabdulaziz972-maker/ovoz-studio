#!/usr/bin/env python3
"""Tekshiruv qoidalarini soxta nuqson bilan sinash.

Har bir qoida **haqiqiy nuqson** kiritilganda yiqilishi shart: hamma
narsadan o'tadigan tekshiruv hech narsani tekshirmaydi. Skript navbat
bilan har bir nuqsonni kiritadi, `bin/verify-stem.sh` ni ishga tushiradi
va u **yiqilganini** talab qiladi; keyin faylni qaytaradi.

  python3 bin/falsify-stem.py

Bu — qo'lda ishlatiladigan vosita, CI uchun emas: u manba faylni
vaqtincha o'zgartiradi (xato bo'lsa ham `finally` da qaytaradi).
To'xtatib qolinsa, holatni `git checkout` bilan tiklash mumkin.
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/uz/ovozstudio/app/media/dsp/StemSeparator.kt"

# nom -> (izlanadigan matn, almashtiriladigan matn, kutilgan xato qismi)
FAULTS = [
    (
        "aniq ayirishda markaz olib tashlanmaydi",
        "if (mode == Mode.REMOVE_VOCALS) return 1.0",
        "if (mode == Mode.REMOVE_VOCALS) return 0.0",
        "cholg'u (chap) = (L-R)/2",
    ),
    (
        "koeffitsient teskari qo'llanadi",
        "return (mid / (mid + side)).pow(gamma)",
        "return 1.0 - (mid / (mid + side)).pow(gamma)",
        "vokalda markaz ohangi bor",
    ),
    (
        "kuch darajasi e'tiborga olinmaydi",
        "return (mid / (mid + side)).pow(gamma)",
        "return (mid / (mid + side)).pow(1.0)",
        "vokal (chap) = formula bo'yicha",
    ),
    (
        "stereo xatosi boshqa so'z bilan aytiladi",
        'const val ERROR_NOT_STEREO = "Manba stereo emas"',
        'const val ERROR_NOT_STEREO = "Manba stereo emas!"',
        "bitta kanalli fayl",
    ),
    (
        "mono mazmun xatosi boshqa so'z bilan aytiladi",
        'const val ERROR_MONO_CONTENT = "Manba kanallari bir xil (mono)"',
        'const val ERROR_MONO_CONTENT = "Manba kanallari bir xil"',
        "kanallari bir xil fayl",
    ),
]


def main():
    original = TARGET.read_text()
    failures = []

    for name, old, new, expected in FAULTS:
        if old not in original:
            print(f"O'TKAZIB YUBORILDI: {name} — matn topilmadi")
            failures.append(name)
            continue

        TARGET.write_text(original.replace(old, new, 1))
        try:
            run = subprocess.run(
                ["bash", "bin/verify-stem.sh"],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
        finally:
            TARGET.write_text(original)

        # Ayrim qoidalar xatoni stderr ga yozadi — ikkala oqim ham kerak.
        output = run.stdout + run.stderr
        # Nuqson kiritilganda skript yiqilishi va aynan kutilgan qoidani
        # XATO deb belgilashi kerak.
        caught = "XATO" in output and expected in output
        if run.returncode != 0 and caught:
            print(f"o'tdi: {name} — «{expected}» ushlandi")
        else:
            print(f"XATO: {name} — qoida nuqsonni ushlamadi "
                  f"(kod {run.returncode}, iz «{expected}» topilmadi)")
            failures.append(name)

    if failures:
        print(f"\n{len(failures)} ta qoida soxta nuqsonni ushlamadi", file=sys.stderr)
        return 1

    print("\nHamma qoida haqiqiy nuqsonni ushladi.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
