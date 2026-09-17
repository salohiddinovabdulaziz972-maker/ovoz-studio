#!/usr/bin/env python3
"""Aralashmani ilovadan tashqarida o'lchaydi.

Uch xil savolga javob beriladi va uchtasi ham kerak:

  1. **Qo'shish to'g'rimi** — ilova yozgan fayl ffmpeg'ning `amix` filtri
     bergan fayl bilan namuna-ba-namuna solishtiriladi. Kirish stereo va
     panorama o'rtada, ya'ni bu yerda hech qanday «panorama qoidasi»
     qatnashmaydi: faqat yig'indi va desibel.

  2. **Panorama va siljish to'g'rimi** — chiqishning har bir kanalida
     kerakli chastotaning amplitudasi Goertzel usuli bilan o'lchanadi.
     Kutilgan son **analitik**: manba 0.5, mono o'rtada −3 dB, chetda
     0 dB. Ya'ni javob ilovaning kodidan emas, matematikadan olinadi.

  3. **Kesish himoyasi ishlaydimi** — yig'indi 1.6 bo'lgan holatda fayl
     cho'qqisi aynan 0.999 bo'lishi va qo'llangan koeffitsient
     0.999/1.6 ga teng bo'lishi kerak.

WAV o'quvchisi — python'ning o'zi (24-bitni qo'lda yoyamiz); ilovaning
birorta satri bu o'lchovda qatnashmaydi.

Ishlatish: WORK=... python3 bin/verify-mix-compare.py
"""

import math
import os
import sys
import wave

WORK = os.environ.get("WORK", "/tmp/superlisa/mix-verify")

# Chastota 48 kHz da 0.8 soniya — ikkala ohang uchun ham butun davr:
# 440·0.8 = 352, 880·0.8 = 704. Butun bo'lmaganda Goertzel oynasi
# sizib o'tardi (leakage) va o'lchov o'zi xato berardi.
WINDOW_START = 9600
WINDOW_COUNT = 38400
RATE = 48000

# Panorama qoidasi: mono o'rtada −3 dB.
CENTER = math.cos(math.pi / 4)


def read_wav(path):
    """(channels, kanallar ro'yxati) qaytaradi; namunalar −1.0 … 1.0."""
    with wave.open(path, "rb") as handle:
        channels = handle.getnchannels()
        width = handle.getsampwidth()
        rate = handle.getframerate()
        raw = handle.readframes(handle.getnframes())

    if width != 3:
        raise ValueError(f"{path}: 24-bit kutilgan, {width * 8}-bit topildi")
    if rate != RATE:
        raise ValueError(f"{path}: {RATE} Hz kutilgan, {rate} topildi")

    values = []
    for offset in range(0, len(raw), 3):
        chunk = raw[offset:offset + 3]
        values.append(int.from_bytes(chunk, "little", signed=True) / 8_388_608.0)

    return [values[c::channels] for c in range(channels)]


def goertzel(samples, freq, start=WINDOW_START, count=WINDOW_COUNT):
    """Oynadagi [freq] chastotasining amplitudasi."""
    chunk = samples[start:start + count]
    n = len(chunk)
    k = n * freq / RATE
    w = 2.0 * math.pi * k / n
    c = 2.0 * math.cos(w)
    s1 = s2 = 0.0
    for value in chunk:
        s0 = value + c * s1 - s2
        s2, s1 = s1, s0
    power = s1 * s1 + s2 * s2 - c * s1 * s2
    return 2.0 * math.sqrt(max(power, 0.0)) / n


def peak(samples):
    return max(abs(value) for value in samples)


def onset(samples, threshold=0.02):
    for index, value in enumerate(samples):
        if abs(value) > threshold:
            return index
    return -1


def read_probe(path):
    values = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            key, _, value = line.rstrip("\n").partition("=")
            values[key] = value
    return values


def check(failures, name, got, want, tolerance):
    ok = abs(got - want) <= tolerance
    verdict = "o'tdi" if ok else "XATO"
    print(f"{name:<34} {got:>10.5f} (kutilgan {want:.5f} ±{tolerance:g}) — {verdict}")
    if not ok:
        failures.append(f"{name}: {got} != {want} ±{tolerance}")


def compare_with_ffmpeg(failures):
    """A: namuna-ba-namuna qiyoslash."""
    ours = read_wav(os.path.join(WORK, "holatlar", "a-ilova.wav"))
    theirs = read_wav(os.path.join(WORK, "holatlar", "a-ffmpeg.wav"))

    if len(ours) != len(theirs):
        failures.append(f"A: kanal soni farq qiladi — {len(ours)} va {len(theirs)}")
        return
    if len(ours[0]) != len(theirs[0]):
        failures.append(f"A: uzunlik farq qiladi — {len(ours[0])} va {len(theirs[0])}")
        return

    worst = 0.0
    where = 0
    for channel in range(len(ours)):
        for index, value in enumerate(ours[channel]):
            difference = abs(value - theirs[channel][index])
            if difference > worst:
                worst, where = difference, index

    # 24-bit faylda kvantlash qadami 6e-8, ya'ni 1e-4 — bu «deyarli aynan»
    # emas, balki haqiqiy chegaradan ancha katta: undan kichik farq faqat
    # hisoblash tartibidan chiqadi.
    check(failures, "A. ffmpeg amix bilan farq", worst, 0.0, 1e-4)
    if worst > 1e-4:
        failures.append(f"A: eng katta farq {where}-namunada")


def main():
    failures = []
    cases = os.path.join(WORK, "holatlar")

    compare_with_ffmpeg(failures)
    print()

    # B1: bitta mono yo'l o'rtada.
    left, right = read_wav(os.path.join(cases, "b1.wav"))
    check(failures, "B1. chap kanal 440 Hz", goertzel(left, 440), 0.5 * CENTER, 5e-4)
    check(failures, "B1. o'ng kanal 440 Hz", goertzel(right, 440), 0.5 * CENTER, 5e-4)

    # B2: 440 chapda, 880 o'ngda va 200 ms kechikkan.
    left, right = read_wav(os.path.join(cases, "b2.wav"))
    check(failures, "B2. chap 440 Hz (chetda 0 dB)", goertzel(left, 440), 0.5, 5e-4)
    check(failures, "B2. chapda 880 Hz yo'q", goertzel(left, 880), 0.0, 1e-3)
    check(failures, "B2. o'ngda 440 Hz yo'q", goertzel(right, 440), 0.0, 1e-3)
    check(failures, "B2. o'ng 880 Hz (chetda 0 dB)", goertzel(right, 880), 0.5, 5e-4)

    # Siljish: 200 ms · 48 kHz = 9600 kadr. Ohang noldan boshlanadi,
    # ya'ni birinchi namunada 0 bo'ladi va chegaradan keyin darhol oshadi.
    first = onset(right)
    ok = abs(first - 9600) <= 3
    print(f"{'B2. o\'ng kanal boshlanishi':<34} {first:>10d} (kutilgan 9600 ±3) — "
          f"{'o\'tdi' if ok else 'XATO'}")
    if not ok:
        failures.append(f"B2: siljish {first} != 9600")

    # B3: master −6 dB.
    left, right = read_wav(os.path.join(cases, "b3.wav"))
    expected = 0.5 * CENTER * 0.501187
    check(failures, "B3. chap kanal (master −6 dB)", goertzel(left, 440), expected, 5e-4)
    check(failures, "B3. o'ng kanal (master −6 dB)", goertzel(right, 440), expected, 5e-4)

    print()

    # C: kesish himoyasi.
    left, right = read_wav(os.path.join(cases, "c.wav"))
    measured = max(peak(left), peak(right))
    check(failures, "C. chiqish cho'qqisi", measured, 0.999, 2e-3)

    probe = read_probe(os.path.join(cases, "c.log"))
    check(failures, "C. o'lchangan cho'qqi", float(probe["choqqi"]), 1.6, 1e-3)
    check(failures, "C. koeffitsient", float(probe["koeffitsient"]), 0.999 / 1.6, 1e-6)

    print()
    if failures:
        for failure in failures:
            print(f"XATO: {failure}", file=sys.stderr)
        return 1
    print("OK: aralashma ffmpeg bilan aynan mos, panorama va siljish analitik "
          "javobga to'g'ri keldi, kesish himoyasi ishladi.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
