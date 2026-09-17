#!/usr/bin/env bash
# Til fayllarini tekshiradi: kalitlar, o'rin egallovchilar, apostroflar.
#
# APK bu konteynerda yig'ilmaydi (`aapt2` faqat x86_64 uchun), ya'ni aapt2
# til faylidagi xatoni ushlaydigan joyda bizda hech narsa yo'q. Bu skript
# o'sha bo'shliqni to'ldiradi. Batafsil: verify-locales.py
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 bin/verify-locales.py "$@"
