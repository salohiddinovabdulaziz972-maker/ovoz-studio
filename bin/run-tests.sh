#!/bin/bash
# Ovoz Studio — sof JVM testlarini yig'ib ishga tushirish.
#
# Android SDK talab qilinmaydi: WAV, kesish va vaqt mantiqi Android'ga
# bog'liq emas, shuning uchun ular oddiy kotlinc bilan tekshiriladi.
# To'liq APK yig'ish uchun Gradle va Android SDK kerak (CI'da bajariladi).
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ~/.local/lib/jars
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
JARS="$HOME/.local/lib/jars"
# MP3 kodlovchisi uchun LAME'ning Java porti (Gradle'da `de.sciss:jump3r`).
M2_REPO="${M2_REPO:-$HOME/.m2/repository}"
JUMP3R="${JUMP3R:-$JARS/jump3r.jar}"
if [ ! -e "$JUMP3R" ] && [ -e "$M2_REPO/de/sciss/jump3r/1.0.5/jump3r-1.0.5.jar" ]; then
    JUMP3R="$M2_REPO/de/sciss/jump3r/1.0.5/jump3r-1.0.5.jar"
fi
OUT="${OUT:-/tmp/superlisa/ovoz-test}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$JARS/junit.jar" "$STDLIB" "$JUMP3R"; do
    if [ ! -e "$tool" ]; then
        echo "Yetishmayapti: $tool" >&2
        echo "jump3r kerak bo'lsa: ~/.local/lib/jars/jump3r.jar ga" >&2
        echo "  https://repo1.maven.org/maven2/de/sciss/jump3r/1.0.5/jump3r-1.0.5.jar" >&2
        echo "  dan nusxa ko'chiring." >&2
        exit 2
    fi
done

MAIN=(
    app/src/main/java/uz/ovozstudio/app/util/TimeFormat.kt
    app/src/main/java/uz/ovozstudio/app/util/TimeParts.kt
    app/src/main/java/uz/ovozstudio/app/media/RecorderConfig.kt
    app/src/main/java/uz/ovozstudio/app/media/WavWriter.kt
    app/src/main/java/uz/ovozstudio/app/media/WavFile.kt
    app/src/main/java/uz/ovozstudio/app/media/AudioTrimmer.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AudioFormat.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AudioFormatDetector.kt
    app/src/main/java/uz/ovozstudio/app/media/format/FormatSupport.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AudioEncoder.kt
    app/src/main/java/uz/ovozstudio/app/media/format/WavPcmReader.kt
    app/src/main/java/uz/ovozstudio/app/media/format/FormatPreservingExporter.kt
    app/src/main/java/uz/ovozstudio/app/media/format/FlacEncoder.kt
    app/src/main/java/uz/ovozstudio/app/media/format/Mp3Encoder.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AdtsHeader.kt
    app/src/main/java/uz/ovozstudio/app/media/format/CodecRates.kt
    app/src/main/java/uz/ovozstudio/app/media/format/PcmBytes.kt
    app/src/main/java/uz/ovozstudio/app/media/dsp/Biquad.kt
    app/src/main/java/uz/ovozstudio/app/media/dsp/EqBands.kt
    app/src/main/java/uz/ovozstudio/app/media/dsp/Equalizer.kt
    app/src/main/java/uz/ovozstudio/app/util/GainText.kt
    app/src/main/java/uz/ovozstudio/app/util/LocalizedNumber.kt
)
TESTS=(
    app/src/test/java/uz/ovozstudio/app/util/TimeFormatTest.kt
    app/src/test/java/uz/ovozstudio/app/util/TimePartsTest.kt
    app/src/test/java/uz/ovozstudio/app/util/GainTextTest.kt
    app/src/test/java/uz/ovozstudio/app/util/LocalizedNumberTest.kt
    app/src/test/java/uz/ovozstudio/app/media/WavFileTest.kt
    app/src/test/java/uz/ovozstudio/app/media/AudioTrimmerTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/AudioFormatDetectorTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/FormatSupportTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/FormatPreservingExporterTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/Mp3EncoderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/AdtsHeaderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/CodecRatesTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/PcmBytesTest.kt
    app/src/test/java/uz/ovozstudio/app/media/dsp/BiquadTest.kt
    app/src/test/java/uz/ovozstudio/app/media/dsp/EqBandsTest.kt
    app/src/test/java/uz/ovozstudio/app/media/dsp/EqualizerTest.kt
)

# Ro'yxat qo'lda yuritiladi (hamma main fayl oddiy kotlinc bilan
# yig'ilavermaydi — Android'ga bog'liqlari bor), lekin unutishning jazosi
# jim bo'lmasligi kerak: yangi test fayli ro'yxatga qo'shilmasa, to'plam
# «OK» deb ko'rsatib, o'sha testlarni umuman ishga tushirmasdan o'tib
# ketardi. Shu tekshiruv buni to'xtatadi.
while IFS= read -r file; do
    case " ${TESTS[*]} " in
        *" $file "*) ;;
        *)
            echo "Xato: $file TEST ro'yxatiga qo'shilmagan" >&2
            exit 2
            ;;
    esac
done < <(find app/src/test -name '*Test.kt' | sort)

# JUnit'ga sinf nomlari yo'llardan hosil qilinadi — ikkinchi ro'yxat
# yuritilsa, u ham eskirib qolardi.
CLASSES=()
for file in "${TESTS[@]}"; do
    name="${file#app/src/test/java/}"
    name="${name%.kt}"
    CLASSES+=("${name//\//.}")
done

rm -rf "$OUT" && mkdir -p "$OUT"

"$KOTLINC" -cp "$JARS/junit.jar:$JARS/hamcrest.jar:$JUMP3R" -jvm-target 17 -nowarn \
    -d "$OUT" "${MAIN[@]}" "${TESTS[@]}" > /tmp/superlisa/kotlinc.log 2>&1 || {
    # jansi shovqinini olib tashlab, haqiqiy xatolarni ko'rsatamiz
    grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
        /tmp/superlisa/kotlinc.log >&2
    exit 1
}

java -cp "$OUT:$STDLIB:$JARS/junit.jar:$JARS/hamcrest.jar:$JUMP3R" org.junit.runner.JUnitCore \
    "${CLASSES[@]}"
