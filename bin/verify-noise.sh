#!/bin/bash
# Shovqin tozalashni MUSTAQIL o'lchov bilan tekshirish.
#
# JVM sinovlari ham manbani, ham natijani o'zi yasaydi va o'zi o'lchaydi.
# Bu yerda ish boshqacha: manbani **ffmpeg** yasaydi, o'lchovni esa
# **python3** WAV faylning o'zidan bajaradi — RIFF sarlavhasi va PCM
# namunalari to'g'ridan-to'g'ri o'qiladi, ilova kodi umuman ishlamaydi.
#
# Signallar ataylab ikki qismdan iborat: boshida 0,5 sekund **faqat
# shovqin** (shundan namuna olinadi), keyin 2,5 sekund ohang + shovqin.
# Shunday qilib bitta faylda ikkala qarama-qarshi talab ham o'lchanadi:
# shovqin pasayishi kerak, ohang esa tegilmasligi kerak.
#
# Uch qism:
#
#   A. Nazariy modelga qiyoslash. Har bir sozlama uchun pasayish
#      kattaligi oldindan hisoblanadi va o'lchov bilan qiyoslanadi.
#
#      Model. Kadr spektrining bitta polosasi (bin) shovqin uchun
#      eksponensial taqsimlangan: u = P/N ~ Exp(1). Ilova polosaga
#          g = min(1, sqrt(max(u - kuch, qoldiq) / u))
#      koeffitsientini qo'llaydi (Berouti qoidasi). Demak kadr quvvati
#      bo'yicha kutilgan pasayish E[g^2] ga teng:
#          E[g^2] = integral_0^inf min(1, max(u-kuch, qoldiq)/u) * e^-u du
#      Integral shu skriptda, awk bilan, to'g'ridan-to'g'ri ta'rifdan
#      hisoblanadi — hech qanday qo'lda olingan son yo'q.
#
#      Model **ideallashtirilgan**: u kadrlar orasida bog'liq emas deb
#      olinadi. Aslida qo'shni kadrlar bir-birining ustiga tushadi
#      (H=256, N=1024), ya'ni ularning koeffitsientlari ham bog'liq va
#      ustma-ust qo'shish (OLA) natijasining quvvati shu sababdan
#      modeldan bir necha dB farq qiladi. Shuning uchun bu ustun
#      **kutilgan** qiymat, qat'iy bashorat emas; ruxsat etilgan farq
#      shu sababdan keng (4 dB) va sabab izohda ochiq yozilgan.
#
#   B. Boshqa amalga oshirish bilan qiyoslash. Xuddi shu fayl ffmpeg'ning
#      `afftdn` filtri bilan tozalanadi va **o'sha python3 o'lchovi**
#      bilan o'lchanadi. Bu ikkitasini tekshiradi: (1) o'lchov usulining
#      o'zi ishlaydimi — boshqa, mustaqil tozalagich ham shu usulda
#      ko'rinadigan pasayish beradimi; (2) bizning natija o'sha sinfdami.
#      `afftdn` ning shovqin poloshi (nf) kirishning haqiqiy polosiga
#      moslanadi: moslanmasa filtr hech narsani o'zgartirmaydi va
#      qiyoslash bo'sh chiqadi.
#
#   C. Silliqlash. Kadrlar orasidagi koeffitsient silliqlash pasayish
#      **miqdorini** emas, uning vaqt bo'yicha tekisligini o'zgartirishi
#      kerak. Shu tekshiriladi.
#
# Nima tekshirilmaydi: tovush sifati (musiqiy shovqin, «suv osti» effekti)
# — bu quloq bilan baholanadi. Skript faqat o'lchanadigan kattaliklar
# uchun javob beradi.
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ffmpeg, python3.
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
WORK="${WORK:-/tmp/superlisa/noise-verify}"
RATE=48000

# Faylning tuzilishi (sekundlarda). Shovqin namunasi 0…0,4 s —
# ko'rsatkich oynasi undan ichkarida, chetlardan uzoqda olinadi.
NOISE_SECONDS=0.5
TONE_SECONDS=2.5
RMS_FROM=0.05
RMS_TO=0.40
TONE_HZ=1000
TONE_FROM=1.0
TONE_TO=3.0

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$STDLIB"; do
    [ -e "$tool" ] || { echo "Yetishmayapti: $tool" >&2; exit 2; }
done
command -v ffmpeg >/dev/null || { echo "ffmpeg topilmadi" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 topilmadi" >&2; exit 2; }

OUT="$WORK/classes"
rm -rf "$WORK" && mkdir -p "$OUT"

# Kodsiz probe: faqat amal va uning bog'liqliklari.
"$KOTLINC" -jvm-target 17 -nowarn -d "$OUT" \
    app/src/main/java/uz/ovozstudio/app/media/RecorderConfig.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavWriter.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavFile.kt \
    app/src/main/java/uz/ovozstudio/app/media/AudioTrimmer.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/PcmWindow.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/Fft.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/NoiseReducer.kt \
    tools/NoiseVerify.kt > "$WORK/kotlinc.log" 2>&1 || {
        grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
            "$WORK/kotlinc.log" >&2
        exit 1
    }
CP="$OUT:$STDLIB"

# Manba: 0,5 s toza shovqin + 2,5 s ohang va shovqin. Ikki bo'lakning
# shovqini har xil urug'dan olinadi (bir xil darajada): profil bir
# realizatsiyadan olinib, boshqasiga qo'llanadi — bu real holatga
# yaqinroq va tekshiruvni kuchliroq qiladi.
make_source() {
    ffmpeg -v error -y -f lavfi \
        -i "anoisesrc=c=white:r=$RATE:a=0.05:d=$NOISE_SECONDS:seed=1234" \
        -c:a pcm_s16le -ac 1 "$WORK/bosh.wav"
    # Ohang 0,5 amplituda — cho'qqi chegaradan oshmaydi (shovqin ±0,05).
    ffmpeg -v error -y -f lavfi \
        -i "aevalsrc=0.5*sin(2*PI*$TONE_HZ*t):s=$RATE:d=$TONE_SECONDS" \
        -f lavfi -i "anoisesrc=c=white:r=$RATE:a=0.05:d=$TONE_SECONDS:seed=4321" \
        -filter_complex "[0:a][1:a]amix=inputs=2:duration=shortest:normalize=0" \
        -c:a pcm_s16le -ac 1 "$WORK/ohang.wav"
    ffmpeg -v error -y -i "$WORK/bosh.wav" -i "$WORK/ohang.wav" \
        -filter_complex "[0:a][1:a]concat=n=2:v=0:a=1" -c:a pcm_s16le -ac 1 "$1"
}

# O'lchov: "rms ohang". O'lchovchi — python3, ilovaning ham, ffmpeg'ning
# ham kodidan mustaqil.
measure() {
    python3 "$ROOT/bin/verify-noise-measure.py" "$1" \
        "$RMS_FROM" "$RMS_TO" "$TONE_HZ" "$TONE_FROM" "$TONE_TO" | awk -F= '
        $1 == "rms"  { rms = $2 }
        $1 == "tone" { tone = $2 }
        END { print rms, tone }'
}

# Ikki daraja orasidagi farq, dB (musbat — ikkinchisi pastroq).
drop_db() {
    awk -v a="$1" -v b="$2" 'BEGIN {
        if (a <= 0 || b <= 0) { print 999; exit }
        print 20 * log(a / b) / log(10)
    }'
}

# Absolyut qiymat.
abs_of() { awk -v v="$1" 'BEGIN { print (v < 0) ? -v : v }'; }

# Chegara ichidami: "1" yoki "0". Argumentlar aynan shu tartibda:
# birinchi o'lchangan qiymat, keyin ruxsat etilgan chegara.
within() { awk -v v="$1" -v lim="$2" 'BEGIN { print (v <= lim) ? "1" : "0" }'; }

# Qiymat chegaradan **katta**mi (teskari tekshiruv uchun).
at_least() { awk -v v="$1" -v lim="$2" 'BEGIN { print (v >= lim) ? "1" : "0" }'; }

# Nazariy model: E[g^2] ni to'g'ridan-to'g'ri ta'rifdan integrallab,
# kutilgan pasayishni dB da qaytaradi.
estimate_db() {
    awk -v alpha="$1" -v floor_db="$2" 'BEGIN {
        beta = exp(floor_db * log(10) / 10)
        du = 0.0005
        sum = 0
        for (u = du / 2; u < 48; u += du) {
            t = u - alpha
            if (t < beta) t = beta
            g = sqrt(t / u)
            if (g > 1) g = 1
            sum += g * g * exp(-u) * du
        }
        print -10 * log(sum) / log(10)
    }'
}

FAILED=0

make_source "$WORK/manba.wav"
read -r SRC_RMS SRC_TONE < <(measure "$WORK/manba.wav")

echo "== A. Nazariy modelga qiyoslash =="
echo
printf 'manba: shovqin darajasi %s (RMS), ohang %s\n\n' "$SRC_RMS" "$SRC_TONE"

# nom|kuch|qoldiq dB
CASES=(
    "standart|2.5|-15"
    "kuchli|4.0|-30"
    "yumshoq|1.5|-10"
)

for case in "${CASES[@]}"; do
    IFS='|' read -r name strength floor <<< "$case"
    dir="$WORK/A-$name"
    mkdir -p "$dir"

    java -cp "$CP" NoiseVerifyKt "$WORK/manba.wav" "$dir/natija.wav" \
        0 400 "$strength" "$floor" > "$dir/probe.log"
    app_drop=$(awk -F= '$1 == "drop" { print $2 }' "$dir/probe.log")

    read -r out_rms out_tone < <(measure "$dir/natija.wav")

    measured=$(drop_db "$SRC_RMS" "$out_rms")
    expected=$(estimate_db "$strength" "$floor")
    tone_error=$(abs_of "$(drop_db "$SRC_TONE" "$out_tone")")
    report_error=$(abs_of "$(awk -v a="$app_drop" -v b="$measured" 'BEGIN { print a - b }')")

    # 1. Pasayish kutilgan qiymatga yaqin (model ideallashtirilgan).
    drop_ok=$(within "$(abs_of "$(awk -v a="$measured" -v b="$expected" 'BEGIN { print a - b }')")" 4.0)
    # 2. Ilova o'zi e'lon qilgan son mustaqil o'lchovga mos.
    report_ok=$(within "$report_error" 0.5)
    # 3. Ohang tegilmaydi.
    tone_ok=$(within "$tone_error" 0.5)
    # 4. Umuman ish qildimi.
    did_ok=$(at_least "$measured" 3.0)

    if [ "$drop_ok" = "1" ] && [ "$report_ok" = "1" ] && [ "$tone_ok" = "1" ] && [ "$did_ok" = "1" ]; then
        verdict="o'tdi"
    else
        verdict="XATO"
    fi

    printf '%-10s pasayish %6s dB (kutilgan %6s)  ilova %6s  ohang %5s dB — %s\n' \
        "$name" "$measured" "$expected" "$app_drop" "$tone_error" "$verdict"
    if [ "$verdict" = "XATO" ]; then
        echo "  kuch=$strength qoldiq=${floor}dB; farqlar: model $(awk -v a="$measured" -v b="$expected" 'BEGIN{print a-b}') dB, ilova ${report_error} dB, ohang ${tone_error} dB" >&2
        FAILED=$((FAILED + 1))
    fi
done

echo
echo "== B. ffmpeg afftdn bilan qiyoslash =="
echo

# `nf` — filtrning shovqin poloshi haqidagi taxmini. Manbaning haqiqiy
# polosi -30 dBFS atrofida, filtrning sukut bo'yicha taxmini esa -50:
# moslanmasa filtr shovqinni «signal» deb hisoblab, deyarli tegmaydi.
CHAIN="afftdn=nr=12:nf=-32:rf=-80:nt=w"
dir="$WORK/B-qiyos"
mkdir -p "$dir"

java -cp "$CP" NoiseVerifyKt "$WORK/manba.wav" "$dir/ilova.wav" \
    0 400 2.5 -15 > "$dir/probe.log"
ffmpeg -v error -y -i "$WORK/manba.wav" -af "$CHAIN" -c:a pcm_s16le "$dir/ffmpeg.wav"

read -r our_rms our_tone < <(measure "$dir/ilova.wav")
read -r ref_rms ref_tone < <(measure "$dir/ffmpeg.wav")

our_drop=$(drop_db "$SRC_RMS" "$our_rms")
ref_drop=$(drop_db "$SRC_RMS" "$ref_rms")
our_tone_error=$(abs_of "$(drop_db "$SRC_TONE" "$our_tone")")
ref_tone_error=$(abs_of "$(drop_db "$SRC_TONE" "$ref_tone")")
gap=$(abs_of "$(awk -v a="$our_drop" -v b="$ref_drop" 'BEGIN { print a - b }')")

# Filtr haqiqatan pasaytirdimi — o'lchov usuli ishlayotganining isboti.
ref_did_ok=$(at_least "$ref_drop" 4.0)
# Natijalar bir sinfda.
gap_ok=$(within "$gap" 8.0)
# Ikkalasi ham ohangni saqlaydi: eng kattasi chegaradan oshmasin.
worst_tone=$(awk -v a="$our_tone_error" -v b="$ref_tone_error" 'BEGIN { print (a > b) ? a : b }')
tones_ok=$(within "$worst_tone" 1.5)

if [ "$ref_did_ok" = "1" ] && [ "$gap_ok" = "1" ] && [ "$tones_ok" = "1" ]; then
    verdict="o'tdi"
else
    verdict="XATO"
fi

printf '%-10s pasayish %6s dB  ohang %5s dB\n' "ilova" "$our_drop" "$our_tone_error"
printf '%-10s pasayish %6s dB  ohang %5s dB   (%s)\n' "ffmpeg" "$ref_drop" "$ref_tone_error" "$CHAIN"
printf '%-10s %s dB — %s\n' "farq" "$gap" "$verdict"

if [ "$verdict" = "XATO" ]; then
    echo "  ffmpeg pasayishi 4 dB dan kam bo'lsa o'lchov usuli shubhali; farq 8 dB dan katta bo'lsa natijalar boshqa sinfda" >&2
    FAILED=$((FAILED + 1))
fi

echo
echo "== C. Silliqlash =="
echo

dir="$WORK/C-silliqlash"
mkdir -p "$dir"
java -cp "$CP" NoiseVerifyKt "$WORK/manba.wav" "$dir/tekis.wav" 0 400 2.5 -15 0 > "$dir/probe.log"
java -cp "$CP" NoiseVerifyKt "$WORK/manba.wav" "$dir/silliq.wav" 0 400 2.5 -15 0.5 > "$dir/probe2.log"

read -r flat_rms _ < <(measure "$dir/tekis.wav")
read -r smooth_rms _ < <(measure "$dir/silliq.wav")

flat_drop=$(drop_db "$SRC_RMS" "$flat_rms")
smooth_drop=$(drop_db "$SRC_RMS" "$smooth_rms")
smoothing_gap=$(abs_of "$(awk -v a="$flat_drop" -v b="$smooth_drop" 'BEGIN { print a - b }')")

# Silliqlash koeffitsientni vaqt bo'yicha tekislaydi: pasayish miqdori
# o'zgarmasligi kerak.
if [ "$(within "$smoothing_gap" 3.0)" = "1" ]; then verdict="o'tdi"; else verdict="XATO"; fi

printf 'silliqlashsiz %6s dB, silliqlash 0.5 bilan %6s dB, farq %5s dB — %s\n' \
    "$flat_drop" "$smooth_drop" "$smoothing_gap" "$verdict"

if [ "$verdict" = "XATO" ]; then
    echo "  silliqlash pasayish miqdorini o'zgartirib yubordi" >&2
    FAILED=$((FAILED + 1))
fi

echo
if [ "$FAILED" -gt 0 ]; then
    echo "XATO: $FAILED ta tekshiruv o'tmadi" >&2
    exit 1
fi
echo "Hamma tekshiruv o'tdi."
