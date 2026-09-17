#!/usr/bin/env python3
"""WAV faylni mustaqil o'lchash: davomiylik va asosiy chastota.

`bin/verify-speed.sh` uchun. Ataylab ilovaning kodidan ham, ffmpeg'dan ham
foydalanmaydi: RIFF sarlavhasi va PCM namunalari to'g'ridan-to'g'ri
o'qiladi. Shu sababli bu o'lchov natijani tekshirayotgan tomonning o'z
xatosini takrorlamaydi.

Asosiy chastota nolni kesib o'tishlar orqali topiladi (gisterezis bilan):
bitta sinus ohang uchun bu yetarli aniqlik beradi va spektr tahlilini
talab qilmaydi. Faylning ikki chetidan [SKIP] sekund tashlanadi — u yerda
ustma-ust qo'yish oynalari hali to'liq qoplanmagan.

Chiqish — `kalit=qiymat` qatorlari:
    frames=96000
    rate=48000
    seconds=2.000000
    hz=440.03
"""

import struct
import sys

SKIP_SECONDS = 0.25


def read_pcm(path):
    """(rate, namunalar ro'yxati) qaytaradi. Faqat mono 16-bit PCM."""
    with open(path, "rb") as handle:
        data = handle.read()

    if len(data) < 12 or data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise SystemExit("%s: WAV fayl emas" % path)

    fmt = None
    position = 12
    while position + 8 <= len(data):
        chunk = data[position:position + 4]
        size = struct.unpack_from("<I", data, position + 4)[0]
        body = data[position + 8:position + 8 + size]

        if chunk == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", body, 0)
        elif chunk == b"data":
            if fmt is None:
                raise SystemExit("%s: fmt bo'lagi data'dan keyin kelgan" % path)
            audio_format, channels, rate, _, _, bits = fmt
            if audio_format != 1 or channels != 1 or bits != 16:
                raise SystemExit(
                    "%s: faqat mono 16-bit PCM kutiladi "
                    "(format=%d kanal=%d bit=%d)" % (path, audio_format, channels, bits)
                )
            count = len(body) // 2
            samples = struct.unpack_from("<%dh" % count, body, 0)
            return rate, samples

        # Bo'laklar juft uzunlikka to'g'rilanadi.
        position += 8 + size + (size & 1)

    raise SystemExit("%s: data bo'lagi topilmadi" % path)


def dominant_hz(samples, rate):
    """Gisterezis bilan nolni kesib o'tishlar orqali chastota."""
    skip = int(rate * SKIP_SECONDS)
    if len(samples) <= 2 * skip + 2:
        skip = 0

    window = samples[skip:len(samples) - skip] if skip else samples
    peak = max(abs(value) for value in window)
    if peak == 0:
        return 0.0
    threshold = peak * 0.4

    below = False
    first = None
    last = None
    crossings = 0
    for index, value in enumerate(window):
        if value < -threshold:
            below = True
        elif below and value > threshold:
            below = False
            if first is None:
                first = index
            else:
                crossings += 1
                last = index

    if not crossings or last is None or last == first:
        return 0.0
    return crossings * rate / (last - first)


def main():
    if len(sys.argv) < 2:
        raise SystemExit("Ishlatilishi: verify-speed-measure.py <fayl.wav>")

    rate, samples = read_pcm(sys.argv[1])
    print("frames=%d" % len(samples))
    print("rate=%d" % rate)
    print("seconds=%.6f" % (len(samples) / float(rate)))
    print("hz=%.3f" % dominant_hz(samples, rate))


main()
