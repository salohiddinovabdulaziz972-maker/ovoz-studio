#!/bin/bash
# ID3 teg yozuvchini MUSTAQIL o'quvchi bilan tekshirish.
#
# Nega alohida skript: JVM sinovlari faqat o'zimiz yasagan baytlarni
# ko'radi — «shunday bo'lishi kerak» degan tasavvurimizni takrorlaydi.
# Ovozni esa boshqa kod bazasi o'qiydi: bu yerda ffprobe. U bizning
# taxminlarimizni bilmaydi, shuning uchun uning «title = Kitob» deyishi —
# haqiqiy dalil.
#
# Nima tekshiriladi:
#   1. ffprobe tegni o'qiy oladimi va qiymatlar aynan mos keladimi;
#   2. kirill harflari buzilmaganmi (noto'g'ri kodlashda «?????» chiqadi);
#   3. eski teg almashtirilganmi (ffmpeg yozgan teg ustiga yozamiz);
#   4. muqova alohida rasm oqimi bo'lib ko'rinadimi;
#   5. ovoz oqimi joyidami — davomiylik o'zgarmaganmi.
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ffmpeg (ffprobe), python3.
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
WORK="${WORK:-/tmp/superlisa/tag-verify}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$STDLIB"; do
    [ -e "$tool" ] || { echo "Yetishmayapti: $tool" >&2; exit 2; }
done
command -v ffmpeg >/dev/null || { echo "ffmpeg topilmadi" >&2; exit 2; }
command -v ffprobe >/dev/null || { echo "ffprobe topilmadi" >&2; exit 2; }

OUT="$WORK/classes"
rm -rf "$WORK" && mkdir -p "$OUT" "$WORK/cases"

# Teg qatlami Android'ga bog'lanmagan — shuning uchun faqat shu uchta fayl
# yig'iladi. Ilovaning qolgani kerak emas.
"$KOTLINC" -jvm-target 17 -nowarn -d "$OUT" \
    app/src/main/java/uz/ovozstudio/app/media/format/AudioFormat.kt \
    app/src/main/java/uz/ovozstudio/app/media/tag/AudioTags.kt \
    app/src/main/java/uz/ovozstudio/app/media/tag/Id3v2Reader.kt \
    app/src/main/java/uz/ovozstudio/app/media/tag/Id3v2Tag.kt \
    app/src/main/java/uz/ovozstudio/app/media/tag/Mp3Tagger.kt \
    tools/TagRead.kt \
    tools/TagVerify.kt > "$WORK/kotlinc.log" 2>&1 || {
        grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
            "$WORK/kotlinc.log" >&2
        exit 1
    }

CP="$OUT:$STDLIB"

# Manba ovoz: bir soniyalik 440 Hz sinus, LAME bilan MP3 qilib kodlangan.
ffmpeg -v error -y -f lavfi -i "sine=frequency=440:duration=1" \
    -c:a libmp3lame -b:a 128k "$WORK/cases/ovoz.mp3"

# Muqova: kichik JPEG (ffmpeg o'zi yasaydi — bizning kod faqat baytlarni
# ko'chiradi, rasm yasash uning ishi emas).
ffmpeg -v error -y -f lavfi -i "color=c=blue:s=64x64" -frames:v 1 \
    "$WORK/cases/muqova.jpg"
COVER_BYTES="$(stat -c%s "$WORK/cases/muqova.jpg")"

# Eski tegli fayl: ffmpeg o'zi yozgan ID3. Ustiga yozamiz — natijada
# faqat yangi qiymatlar qolishi kerak.
ffmpeg -v error -y -i "$WORK/cases/ovoz.mp3" -c copy \
    -metadata title="ESKI NOM" -metadata artist="ESKI IJROCHI" \
    -id3v2_version 3 "$WORK/cases/eski.mp3"

teg() { # nom manba muqova nom ijrochi albom yil janr raqam jami
    local name="$1" source="$2" cover="$3"
    shift 3
    java -cp "$CP" TagVerifyKt \
        "$source" "$WORK/cases/$name.mp3" "$cover" "$@" > "$WORK/$name.log"
}

teg "toliq"     "$WORK/cases/ovoz.mp3" "$WORK/cases/muqova.jpg" \
    "Kitob: birinchi bob" "Husanboy" "OvozStudio" "2026" "Audiobook" "3" "12"
teg "kirill"    "$WORK/cases/ovoz.mp3" "-" \
    "Салим ака келди" "Абдулла Қодирий" "Ўткан кунлар" "2025" "Kitob" "1" "0"
teg "eski-teg"  "$WORK/cases/eski.mp3" "-" \
    "Yangi nom" "Yangi ijrochi" "" "" "" "0" "0"
teg "faqat-nom" "$WORK/cases/ovoz.mp3" "-" \
    "Faqat nom" "" "" "" "" "0" "0"

# O'quvchi tekshiruvi. Ikki tomonlama va ikkisi ham kerak:
#   1. `eski.mp3` — tegni BOSHQA dastur (ffmpeg) yozgan. O'quvchimiz begona
#      tuzilmani ham tushunishi shart, aks holda foydalanuvchi tashqaridan
#      kelgan faylni ochganda maydonlar bo'sh ko'rinadi va u saqlaganda
#      eski teg jimgina o'chib ketadi;
#   2. qolganlari — o'zimiz yozgan teg fayldan haqiqatan o'qiladi.
java -cp "$CP" TagReadKt "$WORK/cases/eski.mp3" > "$WORK/oqish-eski.txt"
java -cp "$CP" TagReadKt "$WORK/cases/toliq.mp3" > "$WORK/oqish-toliq.txt"
java -cp "$CP" TagReadKt "$WORK/cases/kirill.mp3" > "$WORK/oqish-kirill.txt"

# Muqova bayt darajasida o'zgarmaganini tekshiramiz: ffmpeg rasmni
# ajratib oladi, `cmp` esa uni asl fayl bilan solishtiradi. Teg ichida
# rasmning MIME'i, turi va izohi bor — bittasi siljisa, rasm buziladi.
ffmpeg -v error -y -i "$WORK/cases/toliq.mp3" -an -c:v copy "$WORK/cases/chiqqan.jpg"
if ! cmp -s "$WORK/cases/muqova.jpg" "$WORK/cases/chiqqan.jpg"; then
    echo "XATO: muqova baytlari o'zgargan" >&2
    exit 1
fi

WORK="$WORK" COVER_BYTES="$COVER_BYTES" python3 - "$WORK" "$COVER_BYTES" <<'PY'
import json, os, sys
work, cover_bytes = sys.argv[1], int(sys.argv[2])
expected = [
    {
        "name": "toliq", "duration": 1.0, "cover": True,
        "tags": {"title": "Kitob: birinchi bob", "artist": "Husanboy",
                 "album": "OvozStudio", "date": "2026", "genre": "Audiobook",
                 "track": "3/12"},
    },
    {
        "name": "kirill", "duration": 1.0, "cover": False,
        "tags": {"title": "Салим ака келди", "artist": "Абдулла Қодирий",
                 "album": "Ўткан кунлар", "date": "2025", "genre": "Kitob",
                 "track": "1"},
    },
    {
        # Eski teg almashtirilgan: ffmpeg yozgan nom ham, ijrochi ham
        # qolmasligi kerak.
        "name": "eski-teg", "duration": 1.0, "cover": False,
        "tags": {"title": "Yangi nom", "artist": "Yangi ijrochi"},
    },
    {
        "name": "faqat-nom", "duration": 1.0, "cover": False,
        "tags": {"title": "Faqat nom"},
    },
]
reads = [
    {
        # Boshqa dastur yozgan teg: maydonlar o'qilishi kerak, qolgani bo'sh.
        "name": "eski", "fields": {
            "nom": "ESKI NOM", "ijrochi": "ESKI IJROCHI",
            "albom": "", "yil": "", "janr": "", "raqam": "", "muqova": 0,
        },
    },
    {
        "name": "toliq", "fields": {
            "nom": "Kitob: birinchi bob", "ijrochi": "Husanboy",
            "albom": "OvozStudio", "yil": "2026", "janr": "Audiobook",
            "raqam": "3/12", "muqova": cover_bytes,
        },
    },
    {
        # Kirill harflari fayldan o'qilganda ham aynan qolishi kerak.
        "name": "kirill", "fields": {
            "nom": "Салим ака келди", "ijrochi": "Абдулла Қодирий",
            "albom": "Ўткан кунлар", "yil": "2025", "janr": "Kitob",
            "raqam": "1", "muqova": 0,
        },
    },
]

with open(os.path.join(work, "expected.json"), "w", encoding="utf-8") as handle:
    json.dump({"probe": expected, "reads": reads}, handle, ensure_ascii=False, indent=2)
PY

WORK="$WORK" python3 "$ROOT/bin/verify-tag-compare.py"
