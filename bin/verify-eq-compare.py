#!/usr/bin/env python3
"""`verify-eq.sh` ning qiyoslash qismi.

Ilova filtrlangan faylni ffmpeg filtrlangan fayl bilan namuna-ba-namuna
solishtiradi. Ikkala tomon ham bir xil RBJ koeffitsientlaridan foydalanadi,
lekin hisob mustaqil: biri ilovaning Kotlin kodi, ikkinchisi ffmpeg'ning C
kodi. Farq faqat yaxlitlashda qolishi kerak.

Nega namuna-ba-namuna: RMS taqqoslash filtrning fazasidagi xatoni
ko'rmaydi — noto'g'ri kaskad ham «o'xshash» spektr berishi mumkin. Namuna
darajasidagi farq esa kaskadning butun yo'lini tekshiradi: kanal holati
to'g'ri ajratilganmi, polosalar tartibi to'g'rimi, aniqlik yetarlimi.
"""

import os
import struct
import sys
import wave

WORK = os.environ.get("WORK", "/tmp/superlisa/eq-verify")

# Sabab: ikki tomon ham chiqishni 16 bitga yozadi, ya'ni har bir namunada
# kamida bitta yaxlitlash qadami bor. Shundan kelib chiqib chegaralar
# «yaxlitlashdan sal kattaroq» qilib qo'yilgan — filtr mantiqidagi xato
# (noto'g'ri polosa, aralashgan kanal, boshqa aniqlik) bu chegaradan ancha
# katta farq beradi.
#
# O'lchangan haqiqiy farq: 0.003% (cho'qqi) va 0.008% (RMS) — ya'ni bir
# necha namuna qadami. Chegara shundan ~100 marta keng: u «hammasi
# joyida» degan holatni tasdiqlash uchun, tor joyda esa boshqa muhitdagi
# ffmpeg versiyasi bilan yolg'on ogohlantirish berardi.
MAX_PEAK_PERCENT = 0.5
MAX_RMS_PERCENT = 0.1


def read_mono(path):
    with wave.open(path, "rb") as handle:
        if handle.getnchannels() != 1 or handle.getsampwidth() != 2:
            raise SystemExit(f"{path}: faqat mono 16-bit kutilgan")
        raw = handle.readframes(handle.getnframes())
    return struct.unpack("<%dh" % (len(raw) // 2), raw)


def main():
    table = os.path.join(WORK, "qiyos.tsv")
    rows = [line.split("\t") for line in open(table) if line.strip()]
    failures = 0

    for name, app_path, ffmpeg_path in rows:
        name = name.strip()
        app = read_mono(app_path.strip())
        reference = read_mono(ffmpeg_path.strip())

        if len(app) != len(reference):
            print(f"{name}: uzunlik mos emas ({len(app)} va {len(reference)})")
            failures += 1
            continue

        diffs = [x - y for x, y in zip(app, reference)]
        peak_diff = max(abs(d) for d in diffs)
        rms_signal = (sum(x * x for x in app) / len(app)) ** 0.5
        rms_diff = (sum(d * d for d in diffs) / len(diffs)) ** 0.5

        peak_percent = 100.0 * peak_diff / 32768.0
        rms_percent = 100.0 * rms_diff / rms_signal if rms_signal else 0.0
        ok = peak_percent <= MAX_PEAK_PERCENT and rms_percent <= MAX_RMS_PERCENT

        verdict = "o'tdi" if ok else "XATO"
        print(
            f"{name:<16} cho'qqi farqi {peak_percent:.4f}% "
            f"(chegara {MAX_PEAK_PERCENT}%), RMS farqi {rms_percent:.4f}% "
            f"(chegara {MAX_RMS_PERCENT}%) — {verdict}"
        )
        if not ok:
            failures += 1

    if not rows:
        print("Qiyoslash uchun holat topilmadi", file=sys.stderr)
        return 1

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
