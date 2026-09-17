#!/bin/bash
# Tezlik va ohangni MUSTAQIL o'lchov bilan tekshirish.
#
# Nega alohida skript: JVM sinovlari ham manbani, ham natijani o'zi yasaydi
# va o'zi o'lchaydi — ya'ni ko'r bo'lmagan taqqoslash. Bu yerda ish
# boshqacha: ohangni ffmpeg yasaydi, o'lchovni esa **python3** WAV faylning
# o'zidan bajaradi (RIFF sarlavhasi va PCM namunalari to'g'ridan-to'g'ri
# o'qiladi). Kutilgan qiymatlar ham ilovadan emas, ta'rifdan olinadi:
# uzunlik `kirish / tezlik`, chastota `kirish · 2^(yarim ton / 12)`.
#
# Ikki qism:
#
#   A. Ta'rifga qiyoslash. Har bir holat uchun ma'lum chastotali ohang
#      yasaladi, amal qo'llanadi va ikkita kattalik o'lchanadi: davomiylik
#      va asosiy chastota. Tezlik uzunlikni o'zgartiradi, ohangni emas;
#      yarim tonlar esa teskarisini qiladi. «Chipmunk» xatosi aynan shu
#      ikkinchisida ko'rinadi — tezlashtirish ohangni ham ko'tarib
#      yuborsa, chastota o'lchovi buni darhol ushlaydi.
#
#   B. Boshqa amalga oshirish bilan qiyoslash. Xuddi shu ish ffmpeg'ning
#      o'z vositalari bilan ham bajariladi: `atempo` ham WSOLA sinfidagi
#      algoritm, `asetrate` esa namuna olish tezligini o'zgartirib ohangni
#      ko'chiradi. Namuna-ba-namuna solishtirish bu yerda mumkin emas —
#      algoritmlar bir xil natija berishga majbur emas — shuning uchun
#      o'lchanadigan ikki xossa qiyoslanadi: davomiylik va chastota.
#      Ikki mustaqil kod bazasi bir xil xossani ko'rsatsa, xossa
#      algoritmning tasodifidan emas, ishning o'zidan kelib chiqadi.
#
# Nima tekshirilmaydi: tovush sifati (qo'shni bo'laklarning qanchalik
# silliq ulanishi) — bu quloq bilan baholanadi, son bilan emas. Skript
# faqat o'lchanadigan ikki xossa uchun javob beradi.
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ffmpeg, python3.
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
WORK="${WORK:-/tmp/superlisa/speed-verify}"
RATE=48000

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$STDLIB"; do
    [ -e "$tool" ] || { echo "Yetishmayapti: $tool" >&2; exit 2; }
done
command -v ffmpeg >/dev/null || { echo "ffmpeg topilmadi" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 topilmadi" >&2; exit 2; }

OUT="$WORK/classes"
CASES_DIR="$WORK/holatlar"
rm -rf "$WORK" && mkdir -p "$OUT" "$CASES_DIR"

# Kodsiz probe: faqat amal va uning bog'liqliklari.
"$KOTLINC" -jvm-target 17 -nowarn -d "$OUT" \
    app/src/main/java/uz/ovozstudio/app/media/RecorderConfig.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavWriter.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavFile.kt \
    app/src/main/java/uz/ovozstudio/app/media/AudioTrimmer.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/PcmWindow.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/Wsola.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/Resampler.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/SpeedPitch.kt \
    tools/SpeedVerify.kt > "$WORK/kotlinc.log" 2>&1 || {
        grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
            "$WORK/kotlinc.log" >&2
        exit 1
    }
CP="$OUT:$STDLIB"

# Sinus ohangi. Amplituda 0.2 — cho'qqilar chegaradan oshmasin.
tone() {
    ffmpeg -v error -y -f lavfi -i "aevalsrc=0.2*sin(2*PI*$2*t):s=$RATE:d=$3" \
        -c:a pcm_s16le -ac 1 "$1"
}

# O'lchov: "sekundlar chastota". O'lchovchi python3 — ilovaning ham,
# ffmpeg'ning ham kodidan mustaqil. Skript to'rt qator beradi, shuning
# uchun kerakli ikkitasi shu yerda ajratiladi.
measure() {
    python3 "$ROOT/bin/verify-speed-measure.py" "$1" | awk -F= '
        $1 == "seconds" { seconds = $2 }
        $1 == "hz"      { hz = $2 }
        END { print seconds, hz }'
}

# Nisbiy farq foizda.
spread() {
    awk -v a="$1" -v b="$2" 'BEGIN { if (b == 0) { print 999 } else { d = (a - b) / b; if (d < 0) d = -d; print d * 100 } }'
}

# Chegara ichidami: "1" yoki "0".
within() {
    awk -v value="$1" -v limit="$2" 'BEGIN { print (value <= limit) ? "1" : "0" }'
}

FAILED=0

echo "== A. Ta'rifga qiyoslash =="
echo

# nom|kirish Hz|kirish s|tezlik|yarim ton|kutilgan s|kutilgan Hz
CASES=(
    "tezlik-2|440|2.0|2.0|0|1.000000|440.000"
    "tezlik-0.5|440|2.0|0.5|0|4.000000|440.000"
    "tezlik-1.5|440|2.0|1.5|0|1.333333|440.000"
    "ohang-yuqori|440|2.0|1.0|12|2.000000|880.000"
    "ohang-past|440|2.0|1.0|-12|2.000000|220.000"
    "ohang-yettinchi|440|2.0|1.0|7|2.000000|659.255"
    "ikkalasi|440|2.0|2.0|12|1.000000|880.000"
)

for case in "${CASES[@]}"; do
    IFS='|' read -r name tone_hz seconds speed semitones want_seconds want_hz <<< "$case"
    dir="$CASES_DIR/$name"
    mkdir -p "$dir"

    tone "$dir/manba.wav" "$tone_hz" "$seconds"
    java -cp "$CP" SpeedVerifyKt "$dir/manba.wav" "$dir/natija.wav" "$speed" "$semitones" \
        > "$dir/probe.log"

    read -r got_seconds got_hz < <(measure "$dir/natija.wav")

    seconds_error=$(spread "$got_seconds" "$want_seconds")
    hz_error=$(spread "$got_hz" "$want_hz")
    seconds_ok=$(within "$seconds_error" 0.5)
    hz_ok=$(within "$hz_error" 1.0)

    if [ "$seconds_ok" = "1" ] && [ "$hz_ok" = "1" ]; then verdict="o'tdi"; else verdict="XATO"; fi

    printf '%-18s uzunlik %8s s (kutilgan %s)  chastota %8s Hz (kutilgan %s) — %s\n' \
        "$name" "$got_seconds" "$want_seconds" "$got_hz" "$want_hz" "$verdict"
    if [ "$verdict" = "XATO" ]; then
        echo "  farq: uzunlik ${seconds_error}%, chastota ${hz_error}%" >&2
        FAILED=$((FAILED + 1))
    fi
done

echo
echo "== B. ffmpeg bilan qiyoslash =="
echo

# ffmpeg zanjiri: `asetrate` ohangni ko'chiradi (uzunlikni ham o'zgartiradi),
# `atempo` esa uzunlikni tiklaydi. Kerakli tezlik `t = tezlik / koeffitsient`.
ffmpeg_chain() {
    local speed="$1" ratio="$2"
    local tempo rate
    tempo=$(awk -v s="$speed" -v p="$ratio" 'BEGIN { printf "%.6f", s / p }')
    if awk -v p="$ratio" 'BEGIN { exit !(p == 1) }'; then
        echo "atempo=$tempo"
    else
        rate=$(awk -v p="$ratio" -v r="$RATE" 'BEGIN { printf "%d", r * p }')
        echo "asetrate=$rate,aresample=$RATE,atempo=$tempo"
    fi
}

# nom|tezlik|yarim ton
CROSS=(
    "qiyos-tezlik-2|2.0|0"
    "qiyos-tezlik-0.5|0.5|0"
    "qiyos-tezlik-1.5|1.5|0"
    "qiyos-ohang-yuqori|1.0|12"
    "qiyos-ohang-past|1.0|-12"
    "qiyos-ikkalasi|2.0|12"
)

for case in "${CROSS[@]}"; do
    IFS='|' read -r name speed semitones <<< "$case"
    dir="$CASES_DIR/$name"
    mkdir -p "$dir"

    ratio=$(awk -v s="$semitones" 'BEGIN { printf "%.6f", exp(s * log(2) / 12) }')
    chain=$(ffmpeg_chain "$speed" "$ratio")

    tone "$dir/manba.wav" 440 2.0
    java -cp "$CP" SpeedVerifyKt "$dir/manba.wav" "$dir/ilova.wav" "$speed" "$semitones" \
        > "$dir/probe.log"
    ffmpeg -v error -y -i "$dir/manba.wav" -af "$chain" -c:a pcm_s16le "$dir/ffmpeg.wav"

    read -r our_seconds our_hz < <(measure "$dir/ilova.wav")
    read -r ref_seconds ref_hz < <(measure "$dir/ffmpeg.wav")

    seconds_error=$(spread "$our_seconds" "$ref_seconds")
    hz_error=$(spread "$our_hz" "$ref_hz")
    # Bag'rikenglik A bo'limdagidan kengroq: ffmpeg'ning `atempo` filtri
    # oxirgi to'liq bo'lmagan tahlil oynasini tashlab yuboradi, ya'ni uning
    # chiqishi ~30 ms qisqa bo'lishi mumkin (2 sekundlik faylda ~1.5%).
    # Bu — qiyoslanayotgan tomonning o'z xususiyati, xato emas; bizning
    # chiqish esa ta'rif bo'yicha kadr aniqligida.
    seconds_ok=$(within "$seconds_error" 1.5)
    hz_ok=$(within "$hz_error" 1.0)

    if [ "$seconds_ok" = "1" ] && [ "$hz_ok" = "1" ]; then verdict="o'tdi"; else verdict="XATO"; fi

    printf '%-18s uzunlik %8s / %8s s   chastota %8s / %8s Hz — %s\n' \
        "$name" "$our_seconds" "$ref_seconds" "$our_hz" "$ref_hz" "$verdict"
    if [ "$verdict" = "XATO" ]; then
        echo "  zanjir: $chain" >&2
        echo "  farq: uzunlik ${seconds_error}%, chastota ${hz_error}%" >&2
        FAILED=$((FAILED + 1))
    fi
done

echo
if [ "$FAILED" -gt 0 ]; then
    echo "XATO: $FAILED ta tekshiruv o'tmadi" >&2
    exit 1
fi
echo "Hamma tekshiruv o'tdi."
