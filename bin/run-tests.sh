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
OUT="${OUT:-/tmp/superlisa/ovoz-test}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$JARS/junit.jar" "$STDLIB"; do
    if [ ! -e "$tool" ]; then
        echo "Yetishmayapti: $tool" >&2
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
)
TESTS=(
    app/src/test/java/uz/ovozstudio/app/util/TimeFormatTest.kt
    app/src/test/java/uz/ovozstudio/app/util/TimePartsTest.kt
    app/src/test/java/uz/ovozstudio/app/media/WavFileTest.kt
    app/src/test/java/uz/ovozstudio/app/media/AudioTrimmerTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/AudioFormatDetectorTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/FormatSupportTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/FormatPreservingExporterTest.kt
)

rm -rf "$OUT" && mkdir -p "$OUT"

"$KOTLINC" -cp "$JARS/junit.jar:$JARS/hamcrest.jar" -jvm-target 17 -nowarn \
    -d "$OUT" "${MAIN[@]}" "${TESTS[@]}" > /tmp/superlisa/kotlinc.log 2>&1 || {
    # jansi shovqinini olib tashlab, haqiqiy xatolarni ko'rsatamiz
    grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
        /tmp/superlisa/kotlinc.log >&2
    exit 1
}

java -cp "$OUT:$STDLIB:$JARS/junit.jar:$JARS/hamcrest.jar" org.junit.runner.JUnitCore \
    uz.ovozstudio.app.util.TimeFormatTest \
    uz.ovozstudio.app.util.TimePartsTest \
    uz.ovozstudio.app.media.WavFileTest \
    uz.ovozstudio.app.media.AudioTrimmerTest \
    uz.ovozstudio.app.media.format.AudioFormatDetectorTest \
    uz.ovozstudio.app.media.format.FormatSupportTest \
    uz.ovozstudio.app.media.format.FormatPreservingExporterTest
