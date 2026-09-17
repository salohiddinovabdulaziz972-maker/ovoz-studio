#!/usr/bin/env python3
"""Sinov uchun PDF namunalar yasaydi (`PdfTextReaderTest` ishlatadi).

Nega fayllar oldindan yasalgan: PDF o'quvchini **boshqa** yozuvchi bilan
tekshirish kerak. O'zimiz yozgan PDF'ni o'zimiz o'qisak, ikkala tomon bir
xil noto'g'ri tasavvurga ega bo'lishi mumkin — natija esa «to'g'ri
ko'rinadi». Bu yerda yozuvchi — fpdf2 (uchinchi tomon kutubxonasi).

Ikki xil fayl ataylab kerak, chunki ular PDF'ning ikki xil matn yo'lini
qamraydi:

  * `kitob-lotin.pdf`   — oddiy shrift (WinAnsi), bayt = belgi;
  * `kitob-kirill.pdf`  — Type0 shrift (DejaVuSans) + ToUnicode jadvali.

Ishlatish:  python3 tools/make-pdf-fixtures.py
Natija:     app/src/test/fixtures/*.pdf
"""

import os

from fpdf import FPDF

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "test", "fixtures")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

LOTIN = [
    "BIRINCHI BOB",
    "Salim aka qishloqdan shaharga keldi.",
    "U uzoq yillar davomida shu kunni kutdi.",
    "",
    "IKKINCHI BOB",
    "Ertalab havo ochiq edi, yo'l esa changli.",
]


def yoz(nom, qatorlar, shrift):
    pdf = FPDF()
    pdf.set_auto_page_break(auto=True, margin=15)
    if shrift:
        pdf.add_font("dejavu", "", FONT)
        pdf.set_font("dejavu", size=14)
    else:
        pdf.set_font("helvetica", size=14)
    pdf.add_page()
    for qator in qatorlar:
        # Bo'sh qator PDF'da ham bo'sh joy sifatida qoladi.
        pdf.cell(0, 10, qator, new_x="LMARGIN", new_y="NEXT")
    pdf.output(os.path.join(OUT, nom))


def main():
    os.makedirs(OUT, exist_ok=True)
    yoz("kitob-lotin.pdf", LOTIN, shrift=False)
    # Kirill + o'zbek apostrofi (U+02BB): faqat ko'p baytli shriftda bor.
    kirill = [
        "BIRINCHI BOB",
        "Салим ака қишлоқдан шаҳарга келди.",
        "U \"Oʻzbekiston\" deb yozdi — bu toʻgʻri.",
    ]
    yoz("kitob-kirill.pdf", kirill, shrift=True)
    print("Tayyor:", os.listdir(OUT))


if __name__ == "__main__":
    main()
