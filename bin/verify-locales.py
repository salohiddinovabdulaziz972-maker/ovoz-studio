#!/usr/bin/env python3
"""Til fayllarini tekshiradi: kalitlar, o'rin egallovchilar, qochirilgan belgilar.

Nega alohida skript kerak. Bu konteynerda APK yig'ib bo'lmaydi (`aapt2` faqat
x86_64 uchun, konteyner esa aarch64), ya'ni aapt2 til fayllaridagi xatoni
ushlaydigan joyda bizda hech narsa yo'q. Xatolar esa jim: ruscha faylda
kalit yetishmasa, foydalanuvchi shunchaki o'zbekcha satrni ko'radi va buni
hech kim xato deb hisoblamaydi.

Tekshiruvlar (tashqi haqiqat — `values/strings.xml`, ya'ni tayanch til):

  1. Kalitlar to'plami to'rt faylda bir xilmi.
  2. Bitta kalit uchun o'rin egallovchilar (`%1$s`) to'rt faylda bir xilmi.
     Tarjimada `%1$s` tushib qolsa, foydalanuvchi satrda ma'lumot o'rniga
     bo'sh joy ko'radi — masalan «Versiya: » — va buni sezmaydi.
  3. Qochirilmagan `'` bormi. Android resurslarida apostrof `\\'` bo'lishi
     shart; aks holda aapt2 yig'ishni to'xtatadi. O'zbek tilida apostrof
     ko'p (`o'zbek`, `to'g'ri`), ya'ni bu xato shu loyihada eng ehtimolli.
  4. Bitta faylda bir xil nomli kalit ikki marta uchramasinmi (ikkinchisi
     birinchisini jimgina bosib ketadi).
  5. `values/` dagi har bir kalit kodda ishlatilganmi (o'lik kalit).

Faqat o'qish: hech narsa o'zgartirilmaydi. Xato topilsa — chiqish kodi 1.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
# Butun `app/src/main` — manifest ham shu yerda (`@string/app_name`).
SRC = ROOT / "app/src/main"
REFERENCE = "values"

LOCALES = ["values", "values-b+uz+Cyrl", "values-ru", "values-en"]

# `<string name="kalit">matn</string>` — matn ichida `</string>` uchramaydi.
STRING_RE = re.compile(
    r'<string\s+name="([^"]+)"\s*>(.*?)</string>',
    re.DOTALL,
)
PLACEHOLDER_RE = re.compile(r"%(\d+\$)?[a-zA-Z]")
# Yozuv aralashuvini bildiruvchi belgilar (batafsil: `check_script`).
LATIN_DIGRAPH_RE = re.compile(r"[oOgG]'")
CYRILLIC_RE = re.compile(r"[Ѐ-ӿ]")
LATIN_RE = re.compile(r"[A-Za-z]")

errors: list[str] = []
warnings: list[str] = []


def read_locale(folder: str) -> dict[str, str]:
    path = RES / folder / "strings.xml"
    if not path.exists():
        errors.append(f"{folder}/strings.xml topilmadi")
        return {}
    text = path.read_text(encoding="utf-8")

    found: dict[str, str] = {}
    for name, value in STRING_RE.findall(text):
        if name in found:
            errors.append(f"{folder}: «{name}» kaliti ikki marta yozilgan")
        found[name] = value
    return found


def check_quote_escapes(folder: str, text: str) -> None:
    """XML ichidagi matnlarda qochirilmagan apostrofni qidiradi."""
    for name, value in STRING_RE.findall(text):
        # `\'` — to'g'ri; yolg'iz `'` — aapt2 ni to'xtatadi.
        stripped = value.replace("\\'", "")
        if "'" in stripped and "\\'" not in value:
            errors.append(f"{folder}: «{name}» ichida qochirilmagan apostrof")


def placeholders(text: str) -> list[str]:
    """Matndagi o'rin egallovchilar: `%1$s`, `%d`, `%s`.

    `findall` guruhni qaytaradi, shuning uchun `group(0)` olinadi — aks holda
    xato xabari `%1$s` o'rniga `1$` deb yozardi.
    """
    return sorted(match.group(0) for match in PLACEHOLDER_RE.finditer(text))


def check_script(folder: str, table: dict[str, str]) -> None:
    """Yozuv aralashib ketmaganini tekshiradi.

    Belgilar ataylab aniq tanlangan, «shu yozuvning harflari» emas:
    texnik nomlar (`OvozStudio`, `Telegram`, `MP3`) ham lotin harflarida
    yoziladi, ya'ni keng qoida yolg'on signal berardi.

    - `values` (tayanch, lotin): kirill harfi umuman bo'lmasligi kerak.
    - `values-b+uz+Cyrl`: o'zbek lotiniga xos `o'`/`g'` digraflari
      bo'lmasligi kerak — kirill yozuvida ular `ў`/`ғ` bo'ladi. Bu belgi
      lotin matni kirill faylga ko'chirilganini aniq ko'rsatadi.
    """
    if folder == REFERENCE:
        for name, value in table.items():
            if CYRILLIC_RE.search(value):
                errors.append(
                    f"{folder}: «{name}» ichida kirill harfi bor — yozuv aralashgan"
                )
        return

    if folder != "values-b+uz+Cyrl":
        return

    for name, value in table.items():
        if LATIN_DIGRAPH_RE.search(value):
            errors.append(
                f"{folder}: «{name}» ichida o'zbek lotin digrafi (o'/g') bor — "
                f"yozuv aralashgan"
            )
            continue
        # O'rin egallovchilar olib tashlanadi: `%1$s` ichidagi `s` lotin
        # harfi, ya'ni ularsiz tekshirish yolg'on signal berardi.
        plain = PLACEHOLDER_RE.sub("", value)
        if LATIN_RE.search(plain) and not CYRILLIC_RE.search(plain):
            errors.append(
                f"{folder}: «{name}» butunlay lotin yozuvida — tarjima "
                f"tushib qolgan bo'lishi mumkin"
            )


def check_placeholders(folder: str, name: str, value: str, reference: str) -> None:
    expected = placeholders(reference)
    actual = placeholders(value)
    if expected != actual:
        errors.append(
            f"{folder}: «{name}» o'rin egallovchilari mos emas: "
            f"kutilgan {expected or 'yo`q'}, topilgan {actual or 'yo`q'}"
        )


def check_unused(reference: dict[str, str]) -> None:
    """Kodda va manifestda ishlatilmagan kalitlarni yig'adi (o'lik satrlar).

    Ikki murojaat shakli bor: kotlinda `R.string.kalit`, XML'da
    `@string/kalit`. Faqat bittasini ko'rish `app_name` ni o'lik deb
    ko'rsatardi — u esa manifestda ishlatiladi.
    """
    code = "\n".join(
        path.read_text(encoding="utf-8")
        for path in list(SRC.rglob("*.kt")) + list(SRC.rglob("*.xml"))
    )
    used = set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", code))
    used |= set(re.findall(r"@string/([A-Za-z0-9_]+)", code))
    for name in reference:
        if name not in used:
            warnings.append(f"ishlatilmagan kalit: {name}")


def main() -> int:
    locales: dict[str, dict[str, str]] = {}
    for folder in LOCALES:
        locales[folder] = read_locale(folder)
        path = RES / folder / "strings.xml"
        if path.exists():
            check_quote_escapes(folder, path.read_text(encoding="utf-8"))

    reference = locales.get(REFERENCE, {})
    if not reference:
        print("Xato: tayanch til fayli bo'sh", file=sys.stderr)
        return 1

    # Tayanch til pastdagi tsikldan chetda qoladi (u bilan solishtiriladi),
    # shuning uchun yozuvi alohida tekshiriladi.
    check_script(REFERENCE, reference)

    for folder in LOCALES:
        if folder == REFERENCE:
            continue
        table = locales[folder]
        check_script(folder, table)
        missing = sorted(set(reference) - set(table))
        extra = sorted(set(table) - set(reference))
        if missing:
            errors.append(f"{folder}: yetishmayotgan kalitlar: {', '.join(missing)}")
        if extra:
            errors.append(f"{folder}: ortiqcha kalitlar: {', '.join(extra)}")
        for name, value in table.items():
            if name in reference:
                check_placeholders(folder, name, value, reference[name])

    check_unused(reference)

    print(f"Tayanch til: {len(reference)} kalit, {len(LOCALES)} til fayli")

    if warnings:
        print(f"\nOgohlantirish ({len(warnings)}):")
        for line in warnings:
            print(f"  - {line}")

    if errors:
        print(f"\nXato ({len(errors)}):")
        for line in errors:
            print(f"  - {line}")
        return 1

    print("\nOK: kalitlar, o'rin egallovchilar va apostroflar joyida")
    return 0


if __name__ == "__main__":
    sys.exit(main())
