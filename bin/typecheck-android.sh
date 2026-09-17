#!/bin/bash
# Ovoz Studio — Android/Compose qismini HAQIQIY kompilyatordan o'tkazish.
#
# Nima uchun kerak: `bin/run-tests.sh` faqat Android'ga bog'liq bo'lmagan
# qismni (WAV, kesish, vaqt) tekshiradi. Ekranlar, ViewModel'lar va
# accessibility qatlami kotlinc tomonidan umuman ko'rilmay qolardi — ya'ni
# ulardagi kompilyatsiya xatosi faqat CI'da (yoki qurilmada) ma'lum bo'lardi.
#
# Bu skript o'sha bo'shliqni to'ldiradi: android.jar, AndroidX/Compose sinflari
# va Compose kompilyator plagini bilan barcha manba fayllarni kompilyatsiya
# qiladi. APK yig'ilmaydi (u uchun aapt2 kerak, u esa faqat x86_64 uchun
# chiqariladi) — lekin KOTLIN xatolari shu yerda, bir necha soniyada topiladi.
#
# Kerak: ~/.local/lib/jdk va ~/.local/lib/kotlinc (README ga qarang).
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"

# Katta fayllar (android.jar ~100 MB) repoga tushmasligi kerak.
CACHE="${OVOZ_TYPECHECK_CACHE:-/tmp/superlisa/ovoz-typecheck}"
LIBS="$CACHE/libs"
OUT="$CACHE/out"
ANDROID_JAR="$CACHE/android-all.jar"
PLUGIN="$CACHE/compose-plugin-cli.jar"

KOTLIN_VERSION=2.0.21
ROBOLECTRIC_JAR=android-all-15-robolectric-12650502.jar

for tool in "$KOTLINC" "$STDLIB" "$JAVA_HOME/bin/java"; do
    if [ ! -e "$tool" ]; then
        echo "Yetishmayapti: $tool" >&2
        exit 2
    fi
done

mkdir -p "$CACHE"

# android.jar — Robolectric nashri: Android SDK o'rnatmasdan ham framework
# sinflarini beradi.
if [ ! -f "$ANDROID_JAR" ]; then
    echo "android.jar yuklanmoqda (bir marta, ~100 MB)..."
    curl -sSL -o "$ANDROID_JAR" \
        "https://repo1.maven.org/maven2/org/robolectric/android-all/15-robolectric-12650502/$ROBOLECTRIC_JAR"
fi

# Compose kompilyator plagini AYNAN kotlinc versiyasiga mos bo'lishi shart.
# `-embeddable` emas, oddiy varianti kerak: kotlinc o'rnatilgan (embeddable
# bo'lmagan) kompilyatorda ishlaydi.
if [ ! -f "$PLUGIN" ]; then
    echo "Compose kompilyator plagini yuklanmoqda..."
    curl -sSL -o "$PLUGIN" \
        "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compose-compiler-plugin/$KOTLIN_VERSION/kotlin-compose-compiler-plugin-$KOTLIN_VERSION.jar"
fi

# AndroidX/Compose sinflari. Ro'yxat `bin/resolve-android-deps.py` ichida va
# `gradle/libs.versions.toml` bilan qo'lda sinxronlanadi.
if [ ! -d "$LIBS" ] || [ -z "$(ls -A "$LIBS" 2>/dev/null)" ]; then
    echo "AndroidX/Compose kutubxonalari yuklanmoqda (bir marta, bir necha daqiqa)..."
    python3 bin/resolve-android-deps.py
fi

# `R` sinfi: ilovada faqat `R.string` ishlatiladi, shuning uchun uni
# resurslardan generatsiya qilamiz. Qiymatlar ahamiyatsiz — muhimi tiplar.
python3 - "$ROOT" "$CACHE/R.kt" <<'PY'
import re, sys, pathlib
root, dest = sys.argv[1], sys.argv[2]
names = re.findall(
    r'<string name="([^"]+)"',
    pathlib.Path(root, 'app/src/main/res/values/strings.xml').read_text(),
)
lines = [
    "package uz.ovozstudio.app",
    "",
    "// Generatsiya qilingan stub — faqat kompilyatsiyani tekshirish uchun.",
    "object R {",
    "    object string {",
]
lines += [f"        const val {n}: Int = {i + 1}" for i, n in enumerate(names)]
lines += ["    }", "}", ""]
pathlib.Path(dest).write_text("\n".join(lines))
print(f"R.kt: {len(names)} satr kaliti")
PY

CP="$ANDROID_JAR:$STDLIB"
for jar in "$LIBS"/*.jar; do CP="$CP:$jar"; done

rm -rf "$OUT" && mkdir -p "$OUT"
mapfile -t SOURCES < <(find app/src/main/java -name '*.kt' | sort)

LOG="$CACHE/kotlinc.log"
# `|| true`: mos satr bo'lmasa grep 1 qaytaradi va `set -e` skriptni to'xtatadi.
clean() { grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" "$LOG" || true; }

if ! "$KOTLINC" \
    -classpath "$CP" \
    -jvm-target 17 \
    -Xplugin="$PLUGIN" \
    -nowarn \
    -d "$OUT" \
    "$CACHE/R.kt" "${SOURCES[@]}" > "$LOG" 2>&1; then
    clean >&2
    echo "KOMPILYATSIYA XATOSI" >&2
    exit 1
fi

clean
echo "OK: ${#SOURCES[@]} ta fayl kompilyatsiyadan o'tdi"
