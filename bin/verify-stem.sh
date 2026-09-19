#!/bin/bash
# Vokal/cholg'u ajratishni MUSTAQIL o'lchov bilan tekshirish.
#
# JVM sinovlari ham manbani, ham natijani o'zi yasaydi va o'zi o'qiydi.
# Bu yerda ish boshqacha: manbani **ffmpeg** yasaydi, natijani esa
# **python3** faylning o'zidan o'qiydi — RIFF sarlavhasi va PCM namunalari
# to'g'ridan-to'g'ri olinadi, ilova kodi umuman ishlamaydi.
#
# Manba ataylab **nazariy jihatdan aniq** qilib yasaladi: 440 Hz ohang
# ikkala kanalga birdek (markazda turadi), 1200 Hz ohang esa chapga
# musbat, o'ngga manfiy yoziladi (ya'ni butunlay yonda). Bunday manbada
# markaz va yon qismlar bir-biriga aralashmaydi, shuning uchun
# «ajratish ishladimi» degan savolga aniq javob berish mumkin.
#
# Besh qism:
#
#   A. Rekonstruksiya. Usulning asosiy xossasi: vokal + cholg'u = manba
#      (namunama-namuna). Bu **o'lchov emas, qonun**: ajratish spektrda
#      qo'shishga teskari amal bo'lgani uchun hech narsa yo'qolmaydi va
#      hech narsa o'ylab topilmaydi. Shu sababli chegarasi razryad
#      darajasida qattiq (16-bit fayl uchun ~1 razryad = 3e-5).
#      Nazorat qatori ham bor: manba bilan vokalning o'zi albatta farq
#      qilishi kerak — aks holda qiyoslash ishlamayotgan bo'lardi.
#
#   B. Boshqa amalga oshirish bilan qiyoslash. `REMOVE_VOCALS` rejimi
#      matematik jihatdan **aniq amal**: markazni ayirish, ya'ni chap
#      kanal uchun (L-R)/2, o'ng kanal uchun (L-R)/2 - (L+R)/2 ... aniqrog'i
#      cholg'u = L - (L+R)/2 = (L-R)/2. Xuddi shu sonni ffmpeg `pan`
#      filtri mustaqil hisoblaydi. Ikkalasi namuna-ba-namuna qiyoslanadi.
#      Nazorat: manbaning o'zi bu etalonga teng bo'lmasligi kerak.
#
#   C. Ajratish sifati (`SPLIT` rejimi). Bu — o'lchanadigan kattalik:
#      440 Hz qanchalik vokalga tushdi va cholg'uda qanchalik qoldi;
#      1200 Hz esa aksincha. Ikki qarama-qarshi yo'nalishdagi chegaralar
#      o'lchovning o'zini ham tekshiradi: «hamma joyda katta» yoki
#      «hamma joyda kichik» o'lchov ikkalasidan birini yiqitadi.
#
#   D. Koeffitsient formulasi. Butun signal faqat chap kanalda bo'lsa,
#      har bir polosada `M = S = L/2`, ya'ni `d = 1/2` — bu holda
#      vokalning kutilgan amplitudasi **formuladan** chiqadi
#      (`M·d^kuch`). Kutilgan son o'lchangan L va R dan hisoblanadi va
#      o'lchov bilan qiyoslanadi: bu kuch darajasining o'zi to'g'rimi
#      degan savolga javob beradi. Shu yerda `REMOVE_VOCALS` ham
#      ishlatiladi — ikki rejim haqiqatan farq qilishini ko'rsatish uchun.
#
#   E. Imkonsiz manbalar. Bitta kanalli fayl va kanallari bir xil stereo
#      fayl **ochiq xato** berishi kerak — jim nusxa emas. Xato matni
#      drayverning chiqishidan o'qiladi, ya'ni ilova aytgan so'z
#      tekshiriladi.
#
# Nima tekshirilmaydi: musiqiy sifat. Bu usul kanal usuli, ya'ni
# «markazda turmaydigan vokal» ni ajratmaydi; ekran buni o'lchangan
# `sideToMidDb` soni bilan ochiq aytadi.
#
# Kerak: ~/.local/lib/jdk, ~/.local/lib/kotlinc, ffmpeg, python3.
set -e

export JAVA_HOME="$HOME/.local/lib/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

KOTLINC="$HOME/.local/lib/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/.local/lib/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
WORK="${WORK:-/tmp/superlisa/stem-verify}"
RATE=48000
SECONDS_LEN=1.5

# Manbadagi ikki ohang va ularning amplitudasi.
CENTER_HZ=440
SIDE_HZ=1200
AMPLITUDE=0.4

# `SPLIT` rejimi uchun kuch.
STRENGTH=1.5

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

for tool in "$KOTLINC" "$STDLIB"; do
    [ -e "$tool" ] || { echo "Yetishmayapti: $tool" >&2; exit 2; }
done
command -v ffmpeg >/dev/null || { echo "ffmpeg topilmadi" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 topilmadi" >&2; exit 2; }

MEASURE="$ROOT/bin/verify-stem-measure.py"
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
    app/src/main/java/uz/ovozstudio/app/media/dsp/StemSeparator.kt \
    tools/StemVerify.kt > "$WORK/kotlinc.log" 2>&1 || {
        grep -v "jansi\|UnsatisfiedLink\|^java.lang\|^[[:space:]]*at \|osinfo" \
            "$WORK/kotlinc.log" >&2
        exit 1
    }
CP="$OUT:$STDLIB"

FAILED=0

# --- manbalar ---

# Markaz (440 Hz) + yon (1200 Hz). Cho'qqi 0,8 — chegaradan oshmaydi.
ffmpeg -v error -y -f lavfi -i \
    "aevalsrc='$AMPLITUDE*sin(2*PI*$CENTER_HZ*t)+$AMPLITUDE*sin(2*PI*$SIDE_HZ*t)|$AMPLITUDE*sin(2*PI*$CENTER_HZ*t)-$AMPLITUDE*sin(2*PI*$SIDE_HZ*t)':s=$RATE:d=$SECONDS_LEN" \
    -c:a pcm_s16le -ac 2 "$WORK/manba.wav"

# Faqat bitta kanal — ajratish uchun yaroqsiz.
ffmpeg -v error -y -f lavfi -i \
    "aevalsrc=$AMPLITUDE*sin(2*PI*$CENTER_HZ*t):s=$RATE:d=1" \
    -c:a pcm_s16le -ac 1 "$WORK/mono.wav"

# Ikkala kanal bir xil — mazmuni mono, yon qism nolga teng.
ffmpeg -v error -y -f lavfi -i \
    "aevalsrc=$AMPLITUDE*sin(2*PI*$CENTER_HZ*t)|$AMPLITUDE*sin(2*PI*$CENTER_HZ*t):s=$RATE:d=1" \
    -c:a pcm_s16le -ac 2 "$WORK/bir-xil.wav"

# --- yordamchilar ---

# Amalni ishga tushirib, chiqishini chop etadi; xato bo'lsa ham 0 bilan
# tugaydi (xato matnining o'zi natija).
run() {
    local dir="$1" source="$2" mode="$3" strength="$4"
    mkdir -p "$dir"
    java -cp "$CP" StemVerifyKt "$source" "$dir/vokal.wav" "$dir/cholgu.wav" \
        "$mode" "$strength" > "$dir/probe.log"
}

field() { awk -F= -v k="$1" '$1 == k { print $2 }'; }

# Absolyut qiymat.
abs_of() { awk -v v="$1" 'BEGIN { print (v < 0) ? -v : v }'; }

# Chegara ichidami: "1" yoki "0".
within() { awk -v v="$1" -v lim="$2" 'BEGIN { print (v <= lim) ? "1" : "0" }'; }

# Qiymat chegaradan **katta**mi (qarama-qarshi chek uchun).
at_least() { awk -v v="$1" -v lim="$2" 'BEGIN { print (v >= lim) ? "1" : "0" }'; }

# Natijani chiqarib, hisoblagichni yuritadi.
report() {
    local name="$1" got="$2" limit="$3" want="$4" ok="$5"
    local verdict="o'tdi"
    if [ "$ok" != "1" ]; then
        verdict="XATO"
        FAILED=$((FAILED + 1))
    fi
    printf '%-42s %12s %s %-10s (%s) — %s\n' "$name" "$got" "$want" "$limit" "$6" "$verdict"
}

echo "== A. Rekonstruksiya: vokal + cholg'u = manba =="
echo

for mode in split remove; do
    dir="$WORK/A-$mode"
    run "$dir" "$WORK/manba.wav" "$mode" "$STRENGTH"

    error=$(field error < "$dir/probe.log")
    if [ -n "$error" ]; then
        echo "$mode: kutilmagan xato: $error" >&2
        FAILED=$((FAILED + 1))
        continue
    fi

    read -r maxdiff rmsdiff < <(python3 "$MEASURE" reconstruct \
        "$WORK/manba.wav" "$dir/vokal.wav" "$dir/cholgu.wav" | awk -F= '
        $1 == "maxdiff" { a = $2 }
        $1 == "rmsdiff" { b = $2 }
        END { print a, b }')

    # 16-bit faylda bitta razryad 3,05e-5. Chegara ~33 razryad: shu
    # darajadan katta farq «nimadir yo'qoldi» degani bo'lardi.
    ok=$(within "$maxdiff" 0.001)
    printf '%-42s %12s %s %-10s — %s\n' "$mode: eng katta farq" "$maxdiff" "<=" "1e-3" \
        "$([ "$ok" = "1" ] && echo "o'tdi" || echo "XATO")"
    printf '%-42s %12s\n' "$mode: o'rtacha kvadratik farq" "$rmsdiff"
    [ "$ok" = "1" ] || FAILED=$((FAILED + 1))
done

# Nazorat: qiyoslash ishlayotganiga ishonch. Manba bilan vokalning o'zi
# albatta farq qilishi kerak — farq qilmasa, yuqoridagi «o'tdi» ham
# hech narsani isbotlamaydi.
control=$(python3 "$MEASURE" compare "$WORK/manba.wav" "$WORK/A-split/vokal.wav" |
    field maxdiff)
ok=$(at_least "$control" 0.1)
printf '%-42s %12s %s %-10s — %s\n' "nazorat: manba va vokal farq qiladi" "$control" ">=" "0.1" \
    "$([ "$ok" = "1" ] && echo "o'tdi" || echo "XATO")"
[ "$ok" = "1" ] || FAILED=$((FAILED + 1))

echo
echo "== B. Aniq ayirish rejimi ffmpeg \`pan\` bilan =="
echo

dir="$WORK/B-qiyos"
run "$dir" "$WORK/manba.wav" remove 1.5

# Etalon: chap kanal (L-R)/2, markaz esa (L+R)/2 — ffmpeg mustaqil
# hisoblaydi.
ffmpeg -v error -y -i "$WORK/manba.wav" -af "pan=mono|c0=0.5*c0-0.5*c1" \
    -c:a pcm_s16le "$WORK/etalon-yon.wav"
ffmpeg -v error -y -i "$WORK/manba.wav" -af "pan=mono|c0=0.5*c0+0.5*c1" \
    -c:a pcm_s16le "$WORK/etalon-markaz.wav"

# Ilovaning cholg'u fayli — chap kanali yon qismga teng.
ffmpeg -v error -y -i "$dir/cholgu.wav" -af "pan=mono|c0=c0" \
    -c:a pcm_s16le "$WORK/ilova-yon.wav"
ffmpeg -v error -y -i "$dir/vokal.wav" -af "pan=mono|c0=c0" \
    -c:a pcm_s16le "$WORK/ilova-markaz.wav"

side_diff=$(python3 "$MEASURE" compare "$WORK/etalon-yon.wav" "$WORK/ilova-yon.wav" |
    field maxdiff)
center_diff=$(python3 "$MEASURE" compare "$WORK/etalon-markaz.wav" "$WORK/ilova-markaz.wav" |
    field maxdiff)

ok_side=$(within "$side_diff" 0.0005)
ok_center=$(within "$center_diff" 0.0005)
report "cholg'u (chap) = (L-R)/2" "$side_diff" "5e-4" "<=" "$ok_side" "ffmpeg pan"
report "vokal = (L+R)/2" "$center_diff" "5e-4" "<=" "$ok_center" "ffmpeg pan"

# Nazorat: manbaning o'zi etalonga teng emas — aks holda qiyoslash
# ishlamayotgan bo'lardi.
ffmpeg -v error -y -i "$WORK/manba.wav" -af "pan=mono|c0=c0" \
    -c:a pcm_s16le "$WORK/manba-chap.wav"
control=$(python3 "$MEASURE" compare "$WORK/manba-chap.wav" "$WORK/etalon-yon.wav" |
    field maxdiff)
ok=$(at_least "$control" 0.1)
report "nazorat: manba etalondan farq qiladi" "$control" "0.1" ">=" "$ok" "nazorat"

echo
echo "== C. Ajratish sifati (SPLIT) =="
echo

dir="$WORK/C-sifat"
run "$dir" "$WORK/manba.wav" split "$STRENGTH"

read -r src_center src_side < <(python3 "$MEASURE" tones "$WORK/manba.wav" \
    "$CENTER_HZ" "$SIDE_HZ" | awk -F= '
    $1 == "amp1" { a = $2 }
    $1 == "amp2" { b = $2 }
    END { print a, b }')
read -r vocal_center vocal_side < <(python3 "$MEASURE" tones "$dir/vokal.wav" \
    "$CENTER_HZ" "$SIDE_HZ" | awk -F= '
    $1 == "amp1" { a = $2 }
    $1 == "amp2" { b = $2 }
    END { print a, b }')
read -r inst_center inst_side < <(python3 "$MEASURE" tones "$dir/cholgu.wav" \
    "$CENTER_HZ" "$SIDE_HZ" | awk -F= '
    $1 == "amp1" { a = $2 }
    $1 == "amp2" { b = $2 }
    END { print a, b }')

printf 'manba:    markaz %s, yon %s\n' "$src_center" "$src_side"
printf 'vokal:    markaz %s, yon %s\n' "$vocal_center" "$vocal_side"
printf "cholg'u:  markaz %s, yon %s\n" "$inst_center" "$inst_side"
echo

# Markaz ohangi vokalda qolishi, cholg'uda esa bostirilishi kerak.
ok=$(at_least "$vocal_center" 0.36)
report "vokalda markaz ohangi bor" "$vocal_center" "0.36" ">=" "$ok" "$CENTER_HZ Hz"
ok=$(within "$inst_center" 0.063)
report "cholg'uda markaz ohangi yo'q" "$inst_center" "0.063" "<=" "$ok" "$CENTER_HZ Hz (-16 dB)"

# Yon ohang esa aksincha: cholg'uda qoladi, vokalda bo'lmasligi kerak.
ok=$(at_least "$inst_side" 0.36)
report "cholg'uda yon ohang bor" "$inst_side" "0.36" ">=" "$ok" "$SIDE_HZ Hz"
ok=$(within "$vocal_side" 0.1)
report "vokalda yon ohang yo'q" "$vocal_side" "0.1" "<=" "$ok" "$SIDE_HZ Hz (-12 dB)"

# Ilova o'zi e'lon qilgan o'lchov: markaz va yon teng kuchli, ya'ni
# nisbat 0 dB atrofida bo'lishi kerak.
side_to_mid=$(field sideToMidDb < "$dir/probe.log")
ok=$(within "$(abs_of "$side_to_mid")" 0.5)
report "ilova e'lon qilgan sideToMidDb" "$side_to_mid" "±0.5" "~0" "$ok" "dB"

echo
echo "== D. Koeffitsient formulasi (SPLIT kuchi) =="
echo

# Manba ataylab shunday tanlanganki, koeffitsient **oldindan hisoblanadi**:
# butun signal faqat chap kanalda (o'ng kanal jim). U holda har bir
# polosada markaz ham, yon ham bir xil quvvatga ega:
#     M = (L+R)/2 = L/2,  S = (L-R)/2 = L/2  =>  d = 1/2.
# Ilova `d^kuch` ni qo'llaydi, ya'ni vokal `M·d^kuch` bo'lishi shart.
# Bu — taxmin emas, formulaning o'zi; shuning uchun kutilgan son
# **o'lchangan** L va R dan hisoblanadi va o'lchov bilan qiyoslanadi.
# Bu yerda `REMOVE_VOCALS` ham ishlatiladi: u xuddi shu manbada butun
# markazni beradi, ya'ni ikki rejim haqiqatan farq qilishini ko'rsatadi.
PANNED_HZ=1000
PANNED_AMP=0.8

ffmpeg -v error -y -f lavfi -i \
    "aevalsrc='$PANNED_AMP*sin(2*PI*$PANNED_HZ*t)|0':s=$RATE:d=$SECONDS_LEN" \
    -c:a pcm_s16le -ac 2 "$WORK/chap.wav"

split_dir="$WORK/D-split"
remove_dir="$WORK/D-remove"
run "$split_dir" "$WORK/chap.wav" split "$STRENGTH"
run "$remove_dir" "$WORK/chap.wav" remove "$STRENGTH"

src_left=$(python3 "$MEASURE" tones "$WORK/chap.wav" "$PANNED_HZ" "$PANNED_HZ" 0 |
    field amp1)
src_right=$(python3 "$MEASURE" tones "$WORK/chap.wav" "$PANNED_HZ" "$PANNED_HZ" 1 |
    field amp1)

split_vocal=$(python3 "$MEASURE" tones "$split_dir/vokal.wav" "$PANNED_HZ" "$PANNED_HZ" 0 |
    field amp1)
split_inst_left=$(python3 "$MEASURE" tones "$split_dir/cholgu.wav" "$PANNED_HZ" "$PANNED_HZ" 0 |
    field amp1)
split_inst_right=$(python3 "$MEASURE" tones "$split_dir/cholgu.wav" "$PANNED_HZ" "$PANNED_HZ" 1 |
    field amp1)
remove_vocal=$(python3 "$MEASURE" tones "$remove_dir/vokal.wav" "$PANNED_HZ" "$PANNED_HZ" 0 |
    field amp1)

# Kutilgan qiymatlar — formuladan, o'lchangan L va R asosida.
read -r want_vocal want_inst_left want_inst_right < <(awk \
    -v l="$src_left" -v r="$src_right" -v g="$STRENGTH" 'BEGIN {
        mid = (l + r) / 2
        side = (l - r) / 2
        d = (mid * mid) / (mid * mid + side * side)
        mask = d ^ g
        printf "%.6f %.6f %.6f\n", mid * mask, l - mid * mask, r - mid * mask
    }')

printf 'manba: chap %s, o'"'"'ng %s (1000 Hz)\n' "$src_left" "$src_right"
printf 'kutilgan: vokal %s, cholg'"'"'u chap %s, o'"'"'ng %s\n' \
    "$want_vocal" "$want_inst_left" "$want_inst_right"
echo

# Goertzel **amplitudani** o'lchaydi, ishorani emas: o'ng kanaldagi
# kutilgan qiymat manfiy, o'lchov esa musbat chiqadi. Shuning uchun
# qiyoslash |kutilgan| bo'yicha — ishora haqidagi da'vo bu yerda
# tekshirilmaydi, uni A bo'limi (rekonstruksiya) allaqachon qoplaydi.
tolerance=0.003
for pair in \
    "vokal (chap):$split_vocal:$want_vocal" \
    "cholg'u chap:$split_inst_left:$want_inst_left" \
    "cholg'u o'ng:$split_inst_right:$want_inst_right"; do
    IFS=':' read -r name got want <<< "$pair"
    gap=$(abs_of "$(awk -v a="$got" -v b="$want" \
        'BEGIN { if (b < 0) b = -b; print a - b }')")
    ok=$(within "$gap" "$tolerance")
    report "$name = formula bo'yicha" "$got" "$tolerance" "~|$want|" "$ok" "M·d^kuch"
done

# Ikki rejim haqiqatan farq qiladi: aniq ayirishda markaz butunlay
# olinadi (qolgan manba markazining o'zi), SPLIT da esa faqat uning
# bir qismi.
ok=$(at_least "$(awk -v a="$remove_vocal" -v b="$split_vocal" 'BEGIN { print a / b }')" 2.0)
report "SPLIT markazni qismiy oladi" "$split_vocal" "2.0" "<= remove/2" "$ok" \
    "remove: $remove_vocal"

echo
echo "== E. Imkonsiz manbalar =="
echo

expect_error() {
    local name="$1" source="$2" expected="$3"
    local dir="$WORK/D-$name"
    run "$dir" "$source" split 1.5
    local got
    got=$(field error < "$dir/probe.log")
    if [ "$got" = "$expected" ]; then
        printf "%-42s %s — o'tdi\n" "$name" "$got"
    else
        printf '%-42s %s (kutilgan: %s) — XATO\n' "$name" "$got" "$expected" >&2
        FAILED=$((FAILED + 1))
    fi
}

expect_error "bitta kanalli fayl" "$WORK/mono.wav" "Manba stereo emas"
expect_error "kanallari bir xil fayl" "$WORK/bir-xil.wav" "Manba kanallari bir xil (mono)"

echo
if [ "$FAILED" -gt 0 ]; then
    echo "XATO: $FAILED ta tekshiruv o'tmadi" >&2
    exit 1
fi
echo "Hamma tekshiruv o'tdi."
