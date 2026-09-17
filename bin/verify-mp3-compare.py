#!/usr/bin/env python3
"""`verify-mp3.sh` ning taqqoslash qismi.

Manba WAV'ni dekodlangan MP3 bilan solishtiradi.

Nega shunchaki `cmp` emas: MP3 yo'qotishli va u kadrlarga bo'linadi —
dekoder chiqishining boshida 576 ta kodlovchi kechikishi va 529 ta dekoder
kechikishi turadi, oxirida esa to'ldirish. Shuning uchun avval siljish
topiladi, keyin faqat mos keladigan qism ustida xato o'lchanadi.

Mezon: RMS xato native LAME bilan bir tartibda bo'lishi kerak. Aniq son
emas — kodlovchining o'zi LAME, farq faqat sozlamalar va stereo qarorlarida.
"""

import os
import struct
import sys
import wave

WORK = os.environ.get("WORK", "/tmp/superlisa/mp3-verify")
CASES = [
    "stereo_192k", "mono_96k", "stereo_vbr", "mono_24bit_192k", "mono_24bit_vbr",
    # Audio-kitob: sintezator beradigan past chastotalar.
    "mono_8k_64k", "mono_22k_128k",
]

# Kutilgan umumiy kechikish: 576 (kodlovchi) + 529 (dekoder) + 1152 (kadr).
EXPECTED_DELAY = 2257

# Kechikish qat'iy tekshiriladigan holatlar. MPEG-2/2.5 da kadr kichikroq
# (576 namuna), shuning uchun kechikish boshqa — u LAME ichki xususiyati,
# spetsifikatsiya soni emas. Past chastotali holatlarda to'lqinning mos
# kelishi (RMS) tekshiriladi, siljish esa faqat musbat bo'lishi talab
# qilinadi: o'zimiz o'lchagan sonni «kutilgan» deb yozish o'z-o'zini
# tasdiqlash bo'lardi.
STRICT_DELAY = {"stereo_192k", "mono_96k", "stereo_vbr", "mono_24bit_192k", "mono_24bit_vbr"}

# RMS xato to'liq shkalaga nisbatan shu foizdan oshmasligi kerak.
MAX_RMS_PERCENT = 5.0
# Native LAME nazoratidan ruxsat etilgan og'ish (marta).
MAX_RATIO = 3.0


def read_source(path):
    with wave.open(path, "rb") as w:
        channels = w.getnchannels()
        width = w.getsampwidth()
        raw = w.readframes(w.getnframes())
    if width == 2:
        values = list(struct.unpack("<%dh" % (len(raw) // 2), raw))
    elif width == 3:
        # 24-bit manba 16-bit shkalasiga keltiriladi — dekoder ham shunday
        # qiladi, aks holda taqqoslash ma'nosiz bo'lardi.
        values = []
        for i in range(0, len(raw), 3):
            value = raw[i] | (raw[i + 1] << 8) | (raw[i + 2] << 16)
            if value & 0x800000:
                value -= 1 << 24
            values.append(value >> 8)
    else:
        raise ValueError("kutilmagan bit chuqurligi: %d" % width)
    return channels, values


def read_raw(path):
    data = open(path, "rb").read()
    return list(struct.unpack("<%dh" % (len(data) // 2), data))


def best_fit(source, decoded):
    """Eng yaxshi siljishni topadi va (siljish, RMS, maks) qaytaradi."""
    n = len(source)
    if len(decoded) < n:
        return None
    best = None
    for offset in range(0, len(decoded) - n + 1):
        peak = 0
        acc = 0
        for i in range(n):
            error = decoded[offset + i] - source[i]
            magnitude = -error if error < 0 else error
            if magnitude > peak:
                peak = magnitude
            acc += error * error
        rms = (acc / n) ** 0.5
        if best is None or rms < best[1]:
            best = (offset, rms, peak)
    return best


def main():
    failures = 0
    for name in CASES:
        wav = os.path.join(WORK, "cases", name + ".wav")
        ours = os.path.join(WORK, "cases", name + ".raw")
        reference = os.path.join(WORK, "cases", "ref_" + name + ".raw")
        if not os.path.exists(ours):
            print("%-18s O'TKAZIB YUBORILDI (fayl yo'q)" % name)
            failures += 1
            continue

        channels, source = read_source(wav)
        ours_fit = best_fit(source, read_raw(ours))
        frames = len(source) // channels
        if ours_fit is None:
            print("%-18s YIQILDI: dekodlangan oqim manbadan qisqa" % name)
            failures += 1
            continue

        offset, rms, peak = ours_fit
        rms_percent = 100.0 * rms / 32768.0
        line = "%-18s siljish=%5d  RMS=%7.2f (%4.2f%%)  maks=%5d" % (
            name, offset // channels, rms, rms_percent, peak)

        status = "OK"
        if name in STRICT_DELAY and offset // channels != EXPECTED_DELAY:
            status = "YIQILDI: siljish %d, kutilgan %d" % (
                offset // channels, EXPECTED_DELAY)
        elif offset <= 0:
            status = "YIQILDI: siljish topilmadi"
        elif rms_percent > MAX_RMS_PERCENT:
            status = "YIQILDI: RMS xato %.2f%% > %.2f%%" % (rms_percent, MAX_RMS_PERCENT)

        if os.path.exists(reference):
            ref_fit = best_fit(source, read_raw(reference))
            if ref_fit:
                line += "  | native RMS=%7.2f" % ref_fit[1]
                if status == "OK" and rms > ref_fit[1] * MAX_RATIO:
                    status = "YIQILDI: native'dan %.1f marta yomon" % (rms / ref_fit[1])

        print("%s  ->  %s" % (line, status))
        if status != "OK":
            failures += 1

    if failures:
        print("\n%d ta holat yiqildi" % failures)
        sys.exit(1)
    print("\nBarcha %d holat o'tdi (manba %d kadr)" % (len(CASES), frames))


if __name__ == "__main__":
    main()
