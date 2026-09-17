#!/bin/bash
# Ko'p yo'lli aralashtirishni MUSTAQIL o'lchov bilan tekshirish.
#
# Nega alohida skript: JVM sinovlari soxta manba bilan ishlaydi — ya'ni
# mikserning matematikasi tekshiriladi, lekin fayl bilan ishlashi emas.
# Bu yerda ish boshqacha: manba fayllarni **ffmpeg** yasaydi, aralashmani
# ilova yozadi, natijani esa python (o'zining WAV o'quvchisi va Goertzel
# o'lchovi bilan) tekshiradi. Ilovaning kodi natijani o'lchashda
# qatnashmaydi.
#
# Uch qism:
#
#   A. Qo'shish arifmetikasi — ffmpeg'ning `amix` filtri bilan
#      namuna-ba-namuna qiyoslanadi. Ikkala tomonda ham kirish **stereo**
#      va panorama o'rtada (0 dB), ya'ni solishtirishda hech qanday
#      «panorama qoidasi» qatnashmaydi: faqat yig'ish va desibel.
#
#   B. Panorama, siljish va tovush balandligi — chiqishdagi har bir
#      kanalning kerakli chastotadagi amplitudasi o'lchanadi va **analitik**
#      javob bilan solishtiriladi (qoida: mono o'rtada −3 dB, chetda 0 dB).
#
#   C. Kesish himoyasi — ikki yo'l yig'indisi 1.6 bo'lganda butun fayl
#      bitta koeffitsientga tushishini tekshiradi: chiqish cho'qqisi
#      aynan 0.999 bo'lishi kerak, koeffitsient esa 0.999/1.6.
#
# Nima tekshirilmaydi: ffmpeg'ning o'z `pan` filtri bilan qiyoslash
# ataylab qilinmaydi — u boshqa panorama qonunini ishlatadi (mononi
# kanalga 0 dB bilan qo'yadi), ya'ni farq kodning xatosidan emas, ikki
# boshqa qoidadan chiqardi va o'lchov ma'nosiz bo'lardi.
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ffmpeg, python3.
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
WORK="${WORK:-/tmp/superlisa/mix-verify}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$STDLIB"; do
    [ -e "$tool" ] || { echo "Yetishmayapti: $tool" >&2; exit 2; }
done
command -v ffmpeg >/dev/null || { echo "ffmpeg topilmadi" >&2; exit 2; }

OUT="$WORK/classes"
CASES_DIR="$WORK/holatlar"
rm -rf "$WORK" && mkdir -p "$OUT" "$CASES_DIR"

# Kodsiz probe: mikser va uning bog'liqliklari, ilovaning qolgani kerak emas.
"$KOTLINC" -jvm-target 17 -nowarn -d "$OUT" \
    app/src/main/java/uz/ovozstudio/app/media/RecorderConfig.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavFile.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavWriter.kt \
    app/src/main/java/uz/ovozstudio/app/media/AudioTrimmer.kt \
    app/src/main/java/uz/ovozstudio/app/media/mix/MixTrack.kt \
    app/src/main/java/uz/ovozstudio/app/media/mix/MixSource.kt \
    app/src/main/java/uz/ovozstudio/app/media/mix/AudioMixer.kt \
    tools/MixRun.kt > "$WORK/kotlinc.log" 2>&1 || {
        grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
            "$WORK/kotlinc.log" >&2
        exit 1
    }
CP="$OUT:$STDLIB"

# Manba ohang: 48 kHz, 24-bit (aniqlik uchun).
#
# Stereo ohang lavfi ichida yasaladi (ikki ifoda `|` bilan), `-ac 2` bilan emas:
# `-ac 2` mononi stereoga **quvvatni saqlab** o'giradi, ya'ni har bir kanal
# −3 dB bo'lib qoladi (0.8 → 0.5657). O'shanda fayldagi cho'qqi biz
# o'ylagan son bo'lmaydi va o'lchovning o'zi xato bo'lardi.
tone() {
    local file="$1" hz="$2" channels="$3" amplitude="${4:-0.5}"
    local expr="${amplitude}*sin(2*PI*${hz}*t)"
    local source="$expr"
    [ "$channels" = "2" ] && source="$expr|$expr"
    ffmpeg -v error -y -f lavfi -i "aevalsrc=${source}:s=48000:d=1" \
        -c:a pcm_s24le "$file"
}

echo "== A. ffmpeg'ning amix filtri bilan qiyoslash =="

tone "$CASES_DIR/a440s.wav" 440 2
tone "$CASES_DIR/a880s.wav" 880 2

# Ilova: ikkinchi yo'l −6 dB.
java -cp "$CP" MixRunKt --out "$CASES_DIR/a-ilova.wav" \
    --track "$CASES_DIR/a440s.wav:0:0:0" \
    --track "$CASES_DIR/a880s.wav:-6:0:0" > "$CASES_DIR/a-ilova.log"

# ffmpeg: xuddi shu amal, boshqa kod bazasi.
ffmpeg -v error -y -i "$CASES_DIR/a440s.wav" -i "$CASES_DIR/a880s.wav" \
    -filter_complex "[1]volume=-6dB[b];[0][b]amix=inputs=2:normalize=0:duration=longest" \
    -c:a pcm_s24le "$CASES_DIR/a-ffmpeg.wav"

echo "== B. Panorama, siljish, balandlik =="

tone "$CASES_DIR/mono440.wav" 440 1
tone "$CASES_DIR/mono880.wav" 880 1

# B1: bitta mono yo'l o'rtada — ikki kanalda ham −3 dB.
java -cp "$CP" MixRunKt --out "$CASES_DIR/b1.wav" \
    --track "$CASES_DIR/mono440.wav:0:0:0" > "$CASES_DIR/b1.log"

# B2: 440 butunlay chapda, 880 butunlay o'ngda va 200 ms kechikkan.
java -cp "$CP" MixRunKt --out "$CASES_DIR/b2.wav" \
    --track "$CASES_DIR/mono440.wav:0:-1:0" \
    --track "$CASES_DIR/mono880.wav:0:1:200" > "$CASES_DIR/b2.log"

# B3: 440 o'rtada, lekin master −6 dB (umumiy balandlik sinovi).
java -cp "$CP" MixRunKt --out "$CASES_DIR/b3.wav" --master 0.501187 \
    --track "$CASES_DIR/mono440.wav:0:0:0" > "$CASES_DIR/b3.log"

echo "== C. Kesish himoyasi =="

tone "$CASES_DIR/loud1.wav" 440 2 0.8
tone "$CASES_DIR/loud2.wav" 440 2 0.8

java -cp "$CP" MixRunKt --out "$CASES_DIR/c.wav" \
    --track "$CASES_DIR/loud1.wav:0:0:0" \
    --track "$CASES_DIR/loud2.wav:0:0:0" > "$CASES_DIR/c.log"

echo
if ! WORK="$WORK" python3 "$ROOT/bin/verify-mix-compare.py"; then
    echo "XATO: tekshiruvlar o'tmadi" >&2
    exit 1
fi
