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
- **Kam ruxsat so'raydi**: faqat audio fayllarni *o'qish* (`READ_MEDIA_AUDIO`,
  Android 12 va pastda `READ_EXTERNAL_STORAGE`) — ilova ichidagi audio ro'yxati
  uchun; Android 9 va pastda natijani yozish uchun `WRITE_EXTERNAL_STORAGE`.
  Mikrofon, kontaktlar, joylashuv va bildirishnoma ruxsatlari yo'q. PDF va tizim
  tanlagichi orqali olingan fayllar ruxsat talab qilmaydi.
- **Foydalanuvchining asl faylini o'chirmaydi**: ilova faqat o'zining
  vaqtinchalik (kesh) papkasidagi fayllarni o'chiradi (`WorkStore.deleteIfOwned`).
  Qurilmadagi haqiqiy fayl tanlansa ham, ro'yxatdan olib tashlansa ham, rad
  etilsa ham o'z joyida qoladi.
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
