#!/bin/bash
# Ovoz Studio — sof JVM testlarini yig'ib ishga tushirish.
#
# Android SDK talab qilinmaydi: WAV, kesish, birlashtirish, sahifalar ro'yxati,
# hujjat o'quvchilari, ovoz tanlash va xatolar jurnali Android'ga bog'liq emas,
# shuning uchun ular oddiy kotlinc bilan tekshiriladi.
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
    app/src/main/java/uz/ovozstudio/app/log/ErrorJournal.kt
    app/src/main/java/uz/ovozstudio/app/log/ErrorLog.kt
    app/src/main/java/uz/ovozstudio/app/media/AudioTrimmer.kt
    app/src/main/java/uz/ovozstudio/app/media/BitDepth.kt
    app/src/main/java/uz/ovozstudio/app/media/WavFile.kt
    app/src/main/java/uz/ovozstudio/app/media/WavWriter.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/ChapterSplitter.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/DocumentErrors.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/DocumentLoader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/DocxTextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/EpubTextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/Fb2TextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/HtmlTextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/MarkupBlocks.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/OdtTextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/PdfTextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/PlainTextDecoder.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/PptxTextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/ReadingText.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/RtfTextReader.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/TextQuality.kt
    app/src/main/java/uz/ovozstudio/app/media/doc/ZipEntries.kt
    app/src/main/java/uz/ovozstudio/app/media/dsp/PcmWindow.kt
    app/src/main/java/uz/ovozstudio/app/media/dsp/Resampler.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AdtsHeader.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AudioEncoder.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AudioFormat.kt
    app/src/main/java/uz/ovozstudio/app/media/format/AudioFormatDetector.kt
    app/src/main/java/uz/ovozstudio/app/media/format/CodecRates.kt
    app/src/main/java/uz/ovozstudio/app/media/format/FlacEncoder.kt
    app/src/main/java/uz/ovozstudio/app/media/format/FormatPreservingExporter.kt
    app/src/main/java/uz/ovozstudio/app/media/format/FormatSupport.kt
    app/src/main/java/uz/ovozstudio/app/media/format/ImportFailure.kt
    app/src/main/java/uz/ovozstudio/app/media/format/Mp3Encoder.kt
    app/src/main/java/uz/ovozstudio/app/media/format/PcmBytes.kt
    app/src/main/java/uz/ovozstudio/app/media/format/StrictFormat.kt
    app/src/main/java/uz/ovozstudio/app/media/format/WavPcmReader.kt
    app/src/main/java/uz/ovozstudio/app/media/merge/AudioMerger.kt
    app/src/main/java/uz/ovozstudio/app/media/pdf/PageRange.kt
    app/src/main/java/uz/ovozstudio/app/settings/About.kt
    app/src/main/java/uz/ovozstudio/app/settings/AppLanguage.kt
    app/src/main/java/uz/ovozstudio/app/util/LocalizedNumber.kt
    app/src/main/java/uz/ovozstudio/app/util/TimeFormat.kt
    app/src/main/java/uz/ovozstudio/app/util/TimeParts.kt
)
TESTS=(
    app/src/test/java/uz/ovozstudio/app/log/ErrorJournalTest.kt
    app/src/test/java/uz/ovozstudio/app/media/AudioTrimmerTest.kt
    app/src/test/java/uz/ovozstudio/app/media/WavFileTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/ChapterSplitterTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/DocumentLoaderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/DocxTextReaderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/EpubTextReaderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/PdfFixtures.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/PdfTextReaderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/PlainTextDecoderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/ReadingTextTest.kt
    app/src/test/java/uz/ovozstudio/app/media/doc/TextQualityTest.kt
    app/src/test/java/uz/ovozstudio/app/media/dsp/ResamplerTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/AdtsHeaderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/AudioFormatDetectorTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/CodecRatesTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/FormatPreservingExporterTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/FormatSupportTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/Mp3EncoderTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/PcmBytesTest.kt
    app/src/test/java/uz/ovozstudio/app/media/format/StrictFormatTest.kt
    app/src/test/java/uz/ovozstudio/app/media/merge/AudioMergerTest.kt
    app/src/test/java/uz/ovozstudio/app/media/pdf/PageRangeTest.kt
    app/src/test/java/uz/ovozstudio/app/settings/LanguageMatchTest.kt
    app/src/test/java/uz/ovozstudio/app/util/LocalizedNumberTest.kt
    app/src/test/java/uz/ovozstudio/app/util/TimeFormatTest.kt
    app/src/test/java/uz/ovozstudio/app/util/TimePartsTest.kt
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
# yuritilsa, u ham eskirib qolardi. Ro'yxatda test bo'lmagan yordamchi
# fayl ham bo'lishi mumkin (masalan PdfFixtures.kt) — u faqat yig'iladi,
# JUnit'ga berilmaydi, aks holda «No runnable methods» bilan yiqilardi.
CLASSES=()
for file in "${TESTS[@]}"; do
    [[ "$file" == *Test.kt ]] || continue
    name="${file#app/src/test/java/}"
    name="${name%.kt}"
    CLASSES+=("${name//\//.}")
done

rm -rf "$OUT" && mkdir -p "$OUT"

# Android sinflari (Context, Build, Log) JVM'da yo'q. Ularning o'rnini
# bosuvchi yupqa qatlam shu yerda yig'iladi: jurnal nuqtasi (`ErrorLog`)
# sinovdan o'tishi uchun, ilova mantiqini Android'siz tekshirish mumkin
# bo'lsin. Stublar faqat sinov uchun — APK'ga tushmaydi.
STUBS="$ROOT/bin/android-stub"
rm -rf "$STUBS" && mkdir -p "$STUBS"
"$KOTLINC" -jvm-target 17 -nowarn -d "$STUBS" bin/android-stub-src > /tmp/superlisa/kotlinc-stub.log 2>&1 || {
    grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
        /tmp/superlisa/kotlinc-stub.log >&2
    exit 1
}

"$KOTLINC" -cp "$STUBS:$JARS/junit.jar:$JARS/hamcrest.jar:$JUMP3R" -jvm-target 17 -nowarn \
    -d "$OUT" "${MAIN[@]}" "${TESTS[@]}" > /tmp/superlisa/kotlinc.log 2>&1 || {
    # jansi shovqinini olib tashlab, haqiqiy xatolarni ko'rsatamiz
    grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
        /tmp/superlisa/kotlinc.log >&2
    exit 1
}

java -cp "$OUT:$STUBS:$STDLIB:$JARS/junit.jar:$JARS/hamcrest.jar:$JUMP3R" org.junit.runner.JUnitCore \
    "${CLASSES[@]}"
