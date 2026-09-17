#!/bin/bash
# Ekvalayzerni MUSTAQIL o'lchov bilan tekshirish.
#
# Nega alohida skript: JVM sinovlari filtrning matematikasini o'z
# koeffitsientlari orqali tekshiradi — ya'ni o'z-o'zini. Bu yerda ish
# boshqacha: ilova faylni filtrlaydi, o'lchovni esa **ffmpeg** bajaradi.
# Kutilgan qiymatlar ham ilovadan emas, spetsifikatsiyadan olinadi
# (desibel ta'rifi, Buterworth javobi).
#
# Ikki qism:
#
#   A. Ohang bilan o'lchov. Har bir holat uchun bitta sinus yasaladi,
#      ekvalayzer qo'llanadi va chiqishning RMS darajasi ffmpeg bilan
#      o'lchanadi. Farq kutilgan kuchaytirishga teng bo'lishi kerak.
#      Kutilgan qiymat aniq bo'lishi uchun ohang doim polosaning
#      markaziga qo'yiladi.
#
#   B. Boshqa amalga oshirish bilan qiyoslash. Xuddi shu sozlama
#      ffmpeg'ning o'z `equalizer` filtri bilan ham qo'llanadi (bir xil
#      RBJ formulasi, boshqa kod bazasi) va ikki natija namuna-ba-namuna
#      solishtiriladi. Bu kaskadning o'zini tekshiradi: kanal holati,
#      polosalar tartibi, hisoblash aniqligi.
#
# Nima tekshirilmaydi: tayyor profil jadvalidagi sonlar (`EqBands`) —
# ular sof JVM sinovlarida tekshiriladi. Bu skript filtrlash
# dvigatelining ishi uchun javob beradi.
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ffmpeg, python3.
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
WORK="${WORK:-/tmp/superlisa/eq-verify}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$STDLIB"; do
    [ -e "$tool" ] || { echo "Yetishmayapti: $tool" >&2; exit 2; }
done
command -v ffmpeg >/dev/null || { echo "ffmpeg topilmadi" >&2; exit 2; }

OUT="$WORK/classes"
CASES_DIR="$WORK/holatlar"
rm -rf "$WORK" && mkdir -p "$OUT" "$CASES_DIR"

# Kodsiz probe: ekvalayzer va uning bog'liqliklari, ilovaning qolgani kerak emas.
"$KOTLINC" -jvm-target 17 -nowarn -d "$OUT" \
    app/src/main/java/uz/ovozstudio/app/media/RecorderConfig.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavWriter.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavFile.kt \
    app/src/main/java/uz/ovozstudio/app/media/AudioTrimmer.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/Biquad.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/EqBands.kt \
    app/src/main/java/uz/ovozstudio/app/media/dsp/Equalizer.kt \
    tools/EqVerify.kt > "$WORK/kotlinc.log" 2>&1 || {
        grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
            "$WORK/kotlinc.log" >&2
        exit 1
    }
CP="$OUT:$STDLIB"

# ffmpeg ham ilovadagi arifmetikani ishlatsin: o'zgaruvchan
# to'g'ridan-to'g'ri II shakl (`tdii`) va ikki aniqlikdagi (`f64`) hisob.
# Boshqa sozlamada farq filtrdan emas, hisoblash usulidan bo'lardi.
FF="precision=f64:transform=tdii"

# Sinus ohangi. Amplituda ataylab past (0.2): eng katta kuchaytirish
# (+12 dB) ham chegaradan oshmasin, aks holda himoya butun faylni
# pasaytirib, o'lchov ma'nosiz bo'lardi.
tone() {
    ffmpeg -v error -y -f lavfi -i "aevalsrc=0.2*sin(2*PI*$2*t):s=48000:d=2" \
        -c:a pcm_s16le -ac 1 "$1"
}

# Faylning RMS darajasi (dB). Boshidagi 0.3 s tashlanadi — filtrning
# o'rnashish cho'qqisi o'lchovga tushmasin.
rms_db() {
    ffmpeg -v error -i "$1" \
        -af "atrim=start=0.3,astats=metadata=1:reset=0,ametadata=mode=print:file=-" \
        -f null - 2>/dev/null |
        grep "lavfi.astats.Overall.RMS_level=" | tail -1 | cut -d= -f2
}

echo "== A. Ohang bilan o'lchov =="

# nom|ohang Hz|sozlama|past-kesish|kutilgan dB|yo'l qo'yilgan xato
CASES=(
    "kuchaytirish|1000|1000:+6|0|6.0|0.2"
    "pasaytirish|1000|1000:-12|0|-12.0|0.2"
    "yuqori-polosa|8000|8000:+12|0|12.0|0.2"
    "boshqa-chastota|100|8000:+12|0|0.0|0.2"
    "past-kesish-past|30|flat|120|-24.10|0.5"
    "past-kesish-orta|1000|flat|120|0.0|0.5"
)

FAILED=0
for case in "${CASES[@]}"; do
    IFS='|' read -r name tone_hz setting low_cut expected tolerance <<< "$case"
    dir="$CASES_DIR/$name"
    mkdir -p "$dir"

    tone "$dir/manba.wav" "$tone_hz"
    java -cp "$CP" EqVerifyKt "$dir/manba.wav" "$dir/natija.wav" "$setting" "$low_cut" \
        > "$dir/probe.log"

    before=$(rms_db "$dir/manba.wav")
    after=$(rms_db "$dir/natija.wav")
    gain=$(awk -v a="$after" -v b="$before" 'BEGIN { printf "%.3f", a - b }')
    # Taqqoslash bash'da: awk dasturi bittalik qo'shtirnoq ichida turadi,
    # «o'tdi» so'zidagi apostrof esa uni yopib qo'yardi.
    within=$(awk -v g="$gain" -v e="$expected" -v t="$tolerance" \
        'BEGIN { d = g - e; if (d < 0) d = -d; print (d <= t) ? "1" : "0" }')
    if [ "$within" = "1" ]; then verdict="o'tdi"; else verdict="XATO"; fi

    printf '%-20s %8s dB (kutilgan %s ±%s) — %s\n' \
        "$name" "$gain" "$expected" "$tolerance" "$verdict"
    if [ "$within" != "1" ]; then
        echo "  manba $before dB, natija $after dB" >&2
        FAILED=$((FAILED + 1))
    fi
done

echo
echo "== B. ffmpeg'ning o'z filtri bilan qiyoslash =="

# Chastotasi 20 Hz dan 20 kHz gacha ko'tariladigan signal: butun spektrni
# bir marta bosib o'tadi, ya'ni bitta o'lchov hamma polosani tekshiradi.
SWEEP="aevalsrc=0.2*sin(2*PI*20*2/log(1000)*(exp(t*log(1000)/2)-1)):s=48000:d=2"
ffmpeg -v error -y -f lavfi -i "$SWEEP" -c:a pcm_s16le -ac 1 "$CASES_DIR/sweep.wav"

: > "$WORK/qiyos.tsv"

diff_case() {
    local name="$1" chain="$2" setting="$3"
    local dir="$CASES_DIR/$name"
    mkdir -p "$dir"

    java -cp "$CP" EqVerifyKt "$CASES_DIR/sweep.wav" "$dir/ilova.wav" "$setting" 0 \
        > "$dir/probe.log"
    ffmpeg -v error -y -i "$CASES_DIR/sweep.wav" -af "$chain" -c:a pcm_s16le "$dir/ffmpeg.wav"
    printf '%s\t%s\t%s\n' "$name" "$dir/ilova.wav" "$dir/ffmpeg.wav" >> "$WORK/qiyos.tsv"
}

# B1. Bitta qo'ng'iroq filtr — ikkala tomonda ham bir xil sonlar.
diff_case "bitta-polosa" "equalizer=f=1000:t=q:w=1.41:g=6:$FF" "1000:+6"

# B2. Butun kaskad: ilova 10 polosali tayyor profilni qo'llaydi, ffmpeg
# esa o'sha polosalarni birma-bir zanjir qilib oladi. Polosa sonlari
# ilovaning o'zidan olinadi (`.chain` fayli), filtrlar esa boshqa kod
# bazasidan — shuning uchun bu tekshiruv kaskadning o'zini o'lchaydi.
PROFILE_DIR="$CASES_DIR/profil-ovoz"
mkdir -p "$PROFILE_DIR"
java -cp "$CP" EqVerifyKt "$CASES_DIR/sweep.wav" "$PROFILE_DIR/ilova.wav" "preset:VOICE" 0 \
    > "$PROFILE_DIR/probe.log"

CHAIN=""
# `|| [ -n "$frequency" ]` — oxirgi qator yangi qatorsiz tugasa ham
# o'qilsin: aks holda eng yuqori polosa jimgina tushib qolardi.
while IFS=: read -r frequency gain q || [ -n "$frequency" ]; do
    [ -n "$frequency" ] || continue
    CHAIN="${CHAIN}${CHAIN:+,}equalizer=f=$frequency:t=q:w=$q:g=$gain:$FF"
done < "$PROFILE_DIR/ilova.wav.chain"

ffmpeg -v error -y -i "$CASES_DIR/sweep.wav" -af "$CHAIN" -c:a pcm_s16le "$PROFILE_DIR/ffmpeg.wav"
printf '%s\t%s\t%s\n' "profil-ovoz" "$PROFILE_DIR/ilova.wav" "$PROFILE_DIR/ffmpeg.wav" \
    >> "$WORK/qiyos.tsv"

if ! WORK="$WORK" python3 "$ROOT/bin/verify-eq-compare.py"; then
    FAILED=$((FAILED + 1))
fi

echo
if [ "$FAILED" -gt 0 ]; then
    echo "XATO: $FAILED ta tekshiruv o'tmadi" >&2
    exit 1
fi
echo "Hamma tekshiruv o'tdi."
