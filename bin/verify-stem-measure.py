#!/usr/bin/env python3
"""WAV fayllarni mustaqil o'lchash — vokal/cholg'u ajratishni tekshirish uchun.

Bu skript ilova kodini umuman ishlatmaydi: RIFF sarlavhasini o'zi o'qiydi
va namunalarni to'g'ridan-to'g'ri fayldan oladi. Shu tufayli u mustaqil
tekshiruv hisoblanadi — ilova «ajratdim» deb xato aytgan bo'lsa, bu
yerdagi o'lchov buni ko'rsatadi.

To'rt buyruq bor:

  shape fayl.wav
      Faylning shakli: rate=, channels=, frames=, bits=.

  reconstruct manba.wav vokal.wav cholgu.wav
      Vokal + cholg'u yig'indisini manba bilan **namunama-namuna**
      qiyoslaydi. maxdiff= va rmsdiff= chiqadi.

  compare a.wav b.wav
      Ikki faylni namunama-namuna qiyoslaydi (faqat birinchi kanal
      yetarli bo'lsa ham to'liq: barcha kanallar tekshiriladi).

  tones fayl.wav hz1 hz2 [kanal]
      [kanal] da ikki chastotaning amplitudasi (Goertzel; sukut — 0).
      Chetdagi 0,1 sekund tashlab yuboriladi — fayl boshida va oxirida
      ustma-ust qo'shish to'liq emas, o'sha yerdagi son ishonchsiz.

Masalan:
  verify-stem-measure.py tones natija.wav 440 1200
"""

import math
import struct
import sys

# Chetdan tashlab yuboriladigan oraliq (sekund).
EDGE_SECONDS = 0.1


def read_wav(path):
    """(rate, kanallar, bits, kanal_royxati) qaytaradi; namunalar -1..1."""
    with open(path, "rb") as handle:
        data = handle.read()

    if len(data) < 12 or data[0:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise SystemExit(f"WAV emas: {path}")

    pos = 12
    fmt = None
    payload = None
    while pos + 8 <= len(data):
        chunk_id = data[pos:pos + 4]
        size = struct.unpack_from("<I", data, pos + 4)[0]
        body = data[pos + 8:pos + 8 + size]
        if chunk_id == b"fmt ":
            fmt = body
        elif chunk_id == b"data":
            payload = body
        pos += 8 + size + (size & 1)

    if fmt is None or payload is None:
        raise SystemExit(f"fmt yoki data bo'limi yo'q: {path}")

    audio_format, channels, rate, _, _, bits = struct.unpack_from("<HHIIHH", fmt, 0)
    frames = len(payload) // (channels * bits // 8)

    flat = [0.0] * (frames * channels)
    if audio_format == 3:  # IEEE float
        for i in range(frames * channels):
            flat[i] = struct.unpack_from("<f", payload, i * 4)[0]
    elif bits == 16:
        for i in range(frames * channels):
            flat[i] = struct.unpack_from("<h", payload, i * 2)[0] / 32768.0
    elif bits == 24:
        step = 3
        for i in range(frames * channels):
            flat[i] = int.from_bytes(
                payload[i * step:i * step + step], "little", signed=True
            ) / 8_388_608.0
    else:
        raise SystemExit(f"{path}: {bits} bitli PCM qo'llab-quvvatlanmaydi")

    return rate, channels, bits, [flat[c::channels] for c in range(channels)]


def goertzel(samples, freq, rate):
    """Chap kanaldagi [freq] chastotasining amplitudasi."""
    start = int(EDGE_SECONDS * rate)
    stop = len(samples) - start
    if stop <= start:
        start, stop = 0, len(samples)
    chunk = samples[start:stop]

    n = len(chunk)
    k = n * freq / rate
    w = 2.0 * math.pi * k / n
    c = 2.0 * math.cos(w)
    s1 = s2 = 0.0
    for value in chunk:
        s0 = value + c * s1 - s2
        s2, s1 = s1, s0
    power = s1 * s1 + s2 * s2 - c * s1 * s2
    return 2.0 * math.sqrt(max(power, 0.0)) / n


def command_shape(path):
    rate, channels, bits, channels_data = read_wav(path)
    print(f"rate={rate}")
    print(f"channels={channels}")
    print(f"bits={bits}")
    print(f"frames={len(channels_data[0])}")


def differences(first, second):
    """Ikki fayl orasidagi eng katta va o'rtacha kvadratik farq."""
    rate_a, channels_a, _, data_a = read_wav(first)
    rate_b, channels_b, _, data_b = read_wav(second)

    if rate_a != rate_b:
        raise SystemExit(f"chastotalar har xil: {rate_a} va {rate_b}")
    if channels_a != channels_b:
        raise SystemExit(f"kanallar har xil: {channels_a} va {channels_b}")
    if len(data_a[0]) != len(data_b[0]):
        raise SystemExit(
            f"uzunliklar har xil: {len(data_a[0])} va {len(data_b[0])}"
        )

    worst = 0.0
    total = 0.0
    count = 0
    for channel in range(channels_a):
        for a, b in zip(data_a[channel], data_b[channel]):
            delta = a - b
            if abs(delta) > worst:
                worst = abs(delta)
            total += delta * delta
            count += 1
    return worst, math.sqrt(total / count) if count else 0.0


def command_reconstruct(source, vocal, instrumental):
    """vokal + cholg'u manbaga tengmi (namunama-namuna)."""
    rate, channels, _, data_source = read_wav(source)
    rate_v, channels_v, _, data_vocal = read_wav(vocal)
    rate_i, channels_i, _, data_instrumental = read_wav(instrumental)

    if channels != channels_v or channels != channels_i:
        raise SystemExit("kanallar soni mos emas")
    if rate != rate_v or rate != rate_i:
        raise SystemExit("chastotalar mos emas")
    if len(data_source[0]) != len(data_vocal[0]) or len(data_source[0]) != len(
        data_instrumental[0]
    ):
        raise SystemExit("uzunliklar mos emas")

    # Vokal ikkala kanalga birdek yoziladi, cholg'u esa kanal bo'yicha.
    worst = 0.0
    total = 0.0
    count = 0
    for frame in range(len(data_source[0])):
        for channel in range(channels):
            rebuilt = data_vocal[channel][frame] + data_instrumental[channel][frame]
            delta = rebuilt - data_source[channel][frame]
            if abs(delta) > worst:
                worst = abs(delta)
            total += delta * delta
            count += 1

    print(f"maxdiff={worst:.8f}")
    print(f"rmsdiff={math.sqrt(total / count) if count else 0.0:.8f}")


def command_compare(first, second):
    worst, rms = differences(first, second)
    print(f"maxdiff={worst:.8f}")
    print(f"rmsdiff={rms:.8f}")


def command_tones(path, first_hz, second_hz, channel=0):
    rate, channels, _, data = read_wav(path)
    if channel >= channels:
        raise SystemExit(f"{path}: {channel}-kanal yo'q ({channels} ta kanal)")
    samples = data[channel]
    print(f"amp1={goertzel(samples, first_hz, rate):.6f}")
    print(f"amp2={goertzel(samples, second_hz, rate):.6f}")


def main(argv):
    if len(argv) < 3:
        raise SystemExit(__doc__)

    command = argv[1]
    if command == "shape":
        command_shape(argv[2])
    elif command == "compare":
        command_compare(argv[2], argv[3])
    elif command == "reconstruct":
        command_reconstruct(argv[2], argv[3], argv[4])
    elif command == "tones":
        channel = int(argv[5]) if len(argv) > 5 else 0
        command_tones(argv[2], float(argv[3]), float(argv[4]), channel)
    else:
        raise SystemExit(f"Noma'lum buyruq: {command}")


if __name__ == "__main__":
    main(sys.argv)
