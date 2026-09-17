#!/usr/bin/env python3
"""WAV faylni mustaqil o'lchash — shovqin tozalashni tekshirish uchun.

Bu skript ilova kodini umuman ishlatmaydi: RIFF sarlavhasini o'zi o'qiydi
va namunalarni to'g'ridan-to'g'ri fayldan oladi. Aynan shu uni mustaqil
tekshiruvga aylantiradi — ilova «shovqin N dB pasaydi» deb aytsa, bu son
shu yerdagi o'lchov bilan solishtiriladi.

Ikki o'lchov chiqadi:

  rms   — [rms_start, rms_end] oralig'idagi o'rtacha kvadratik daraja.
  tone  — [tone_start, tone_end] oralig'ida `--tone-hz` chastotasining
          amplitudasi (bitta polosali DFT).

Ishlatilishi:
  verify-noise-measure.py fayl.wav RMS_A RMS_B TONE_HZ TONE_A TONE_B

Masalan:
  verify-noise-measure.py natija.wav 0.05 0.40 1000 1.0 3.0
"""

import math
import struct
import sys


def read_pcm(path):
    """16/24/32-bit PCM yoki 32-bit float WAV ni o'qib, -1..1 oralig'ida qaytaradi."""
    with open(path, "rb") as f:
        data = f.read()

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

    values = [0.0] * (frames * channels)
    if audio_format == 3:  # IEEE float
        for i in range(frames * channels):
            values[i] = struct.unpack_from("<f", payload, i * 4)[0]
    elif bits == 16:
        for i in range(frames * channels):
            values[i] = struct.unpack_from("<h", payload, i * 2)[0] / 32768.0
    elif bits == 24:
        for i in range(frames * channels):
            raw = int.from_bytes(payload[i * 3:i * 3 + 3], "little", signed=True)
            values[i] = raw / 8388608.0
    elif bits == 32:
        for i in range(frames * channels):
            values[i] = struct.unpack_from("<i", payload, i * 4)[0] / 2147483648.0
    else:
        raise SystemExit(f"Qo'llab-quvvatlanmaydigan bit chuqurligi: {bits}")

    return values, channels, rate, frames


def rms(values, channels, rate, start_s, end_s):
    """Oraliqdagi o'rtacha kvadratik daraja (barcha kanallar birga)."""
    first = max(0, int(start_s * rate))
    last = min(len(values) // channels, int(end_s * rate))
    if last <= first:
        raise SystemExit(f"Bo'sh oraliq: {start_s}…{end_s}")

    total = 0.0
    count = 0
    for frame in range(first, last):
        for ch in range(channels):
            sample = values[frame * channels + ch]
            total += sample * sample
            count += 1
    return math.sqrt(total / count)


def tone(values, channels, rate, frequency, start_s, end_s):
    """Bitta polosaning amplitudasi — oraliq davrga karrali bo'lishi kerak."""
    first = max(0, int(start_s * rate))
    last = min(len(values) // channels, int(end_s * rate))
    count = last - first
    if count <= 0:
        raise SystemExit(f"Bo'sh oraliq: {start_s}…{end_s}")

    re = 0.0
    im = 0.0
    step = 2.0 * math.pi * frequency / rate
    for i in range(count):
        angle = step * (first + i)
        sample = values[(first + i) * channels]
        re += sample * math.cos(angle)
        im += sample * math.sin(angle)
    return 2.0 * math.sqrt(re * re + im * im) / count


def main():
    if len(sys.argv) < 7:
        raise SystemExit(__doc__)

    path = sys.argv[1]
    rms_a, rms_b = float(sys.argv[2]), float(sys.argv[3])
    tone_hz = float(sys.argv[4])
    tone_a, tone_b = float(sys.argv[5]), float(sys.argv[6])

    values, channels, rate, frames = read_pcm(path)

    print(f"frames={frames}")
    print(f"rate={rate}")
    print(f"channels={channels}")
    print(f"rms={rms(values, channels, rate, rms_a, rms_b):.6f}")
    print(f"tone={tone(values, channels, rate, tone_hz, tone_a, tone_b):.6f}")


if __name__ == "__main__":
    main()
