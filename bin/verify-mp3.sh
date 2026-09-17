#!/bin/bash
# MP3 kodlovchisini MUSTAQIL dekoder bilan tekshirish.
#
# Nega alohida skript: JVM sinovlari faqat oqim tuzilishini ko'radi
# (kadr sinxronizatsiyasi, bit tezligi, o'lcham). Ovozning haqiqatan
# to'g'ri chiqqanini esa faqat boshqa dekoder aytadi. Bu yerda u — ffmpeg.
# Uchinchi tomon nazorati: LAME'ning o'z dekoderi emas, balki boshqa
# kod bazasi. Shu sababli bu skript `run-tests.sh` dan alohida turadi:
# u ffmpeg'ga bog'liq, ffmpeg esa har bir muhitda ham bo'lavermaydi.
#
# Nima tekshiriladi:
#   1. ffmpeg faylni xatosiz ochadimi;
#   2. dekodlangan davomiylik manbaga mos keladimi;
#   3. dekodlangan to'lqin manbani takrorlaydimi (RMS xato chegarada);
#   4. bir xil manbani native LAME bilan kodlaganda xato shu darajadami.
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ~/.local/lib/jars/jump3r.jar,
#        ffmpeg, python3.
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
JUMP3R="${JUMP3R:-$HOME/.local/lib/jars/jump3r.jar}"
WORK="${WORK:-/tmp/superlisa/mp3-verify}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$STDLIB" "$JUMP3R"; do
    [ -e "$tool" ] || { echo "Yetishmayapti: $tool" >&2; exit 2; }
done
command -v ffmpeg >/dev/null || { echo "ffmpeg topilmadi" >&2; exit 2; }

OUT="$WORK/classes"
rm -rf "$WORK" && mkdir -p "$OUT" "$WORK/cases"

# Kodsiz probe: faqat format qatlami + kotlovchi, ilovaning qolgani kerak emas.
"$KOTLINC" -cp "$JUMP3R" -jvm-target 17 -nowarn -d "$OUT" \
    app/src/main/java/uz/ovozstudio/app/media/WavWriter.kt \
    app/src/main/java/uz/ovozstudio/app/media/WavFile.kt \
    app/src/main/java/uz/ovozstudio/app/media/RecorderConfig.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/AudioFormat.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/AudioFormatDetector.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/FormatSupport.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/AudioEncoder.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/WavPcmReader.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/FormatPreservingExporter.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/FlacEncoder.kt \
    app/src/main/java/uz/ovozstudio/app/media/format/Mp3Encoder.kt \
    tools/Mp3Verify.kt > "$WORK/kotlinc.log" 2>&1 || {
        grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
            "$WORK/kotlinc.log" >&2
        exit 1
    }

CP="$OUT:$STDLIB:$JUMP3R"
CASES=(
    "stereo_192k:2:16:192"
    "mono_96k:1:16:96"
    "stereo_vbr:2:16:0"
    "mono_24bit_192k:1:24:192"
    "mono_24bit_vbr:1:24:0"
)

for case in "${CASES[@]}"; do
    IFS=: read -r name channels depth kbps <<< "$case"
    java -cp "$CP" Mp3VerifyKt \
        "$WORK/cases/$name.wav" "$WORK/cases/$name.mp3" 12288 "$channels" "$depth" "$kbps"
    ffmpeg -v error -y -i "$WORK/cases/$name.mp3" \
        -f s16le -acodec pcm_s16le "$WORK/cases/$name.raw"
    # Nazorat: xuddi shu manbani native LAME bilan kodlaymiz.
    if [ "$kbps" = "0" ]; then
        ffmpeg -v error -y -i "$WORK/cases/$name.wav" -c:a libmp3lame \
            -compression_level 2 -q:a 4 "$WORK/cases/ref_$name.mp3"
    else
        ffmpeg -v error -y -i "$WORK/cases/$name.wav" -c:a libmp3lame \
            -compression_level 2 -b:a "${kbps}k" -ac "$channels" "$WORK/cases/ref_$name.mp3"
    fi
    ffmpeg -v error -y -i "$WORK/cases/ref_$name.mp3" \
        -f s16le -acodec pcm_s16le "$WORK/cases/ref_$name.raw"
done

WORK="$WORK" python3 "$ROOT/bin/verify-mp3-compare.py"
