# Xavfsizlik

## Zaiflik topsangiz

Iltimos, **ochiq Issue ochmang**: zaiflik tuzatilmaguncha hamma ko'rib qoladi.

GitHub'da repozitoriyning **Security** bo'limiga kiring va
**Report a vulnerability** tugmasini bosing (shaxsiy xabar, faqat egasi
ko'radi). Xabarda yozing: nima topildi, qanday takrorlanadi, qanday zarar
bo'lishi mumkin.

## Ilova nima qilmaydi

- **Internetga chiqmaydi**: manifestda `INTERNET` ruxsati yo'q. Fayllar
  telefondan tashqariga o'zi chiqib ketishi texnik jihatdan mumkin emas.
- **Hech qanday ruxsat so'ramaydi**: fayl tizim tanlagichi orqali olinadi va
  saqlanadi, mikrofon va ommaviy xotiraga kirish yo'q.
- Zaxira nusxa (`allowBackup`) o'chirilgan.

## Nimalar muhim

- fayllarni boshqa ilovaga ochib qo'yadigan xatolar (`FileProvider`): u faqat
  `cache/natija/` papkasini beradi, `exported=false`;
- noma'lum fayl (PDF, DOCX, EPUB, ODT, RTF, FB2, HTML, audio) ilovani yiqitadigan
  yoki xotirani to'ldiradigan holatlar. Himoya: XML o'quvchida DOCTYPE
  o'chirilgan (XXE yo'q), arxiv yozuvlari va umumiy hajm cheklangan (zip-bomba),
  matn fayllari 32 MB gacha, PDF diskdagi vaqtinchalik fayl rejimida ochiladi;
- xatolar jurnali: fayl nomlari, `content://` havolalari, qurilma yo'llari
  va hujjat matni **yozilmaydi** (`ErrorJournal.redact`); jurnal faqat
  foydalanuvchi ulashganda chiqadi;
- uchinchi tomon kutubxonalari: `pdfbox-android` (Apache-2.0), `jump3r`
  (LGPL-2.1+). Versiyalar `gradle/libs.versions.toml` da qotirilgan.

## Sirlar

Kalitlar, parollar va tokenlar repozitoriyga **hech qachon** qo'yilmaydi
(git tarixida ham qoladi). Imzo kaliti — faqat GitHub Secrets'da.
