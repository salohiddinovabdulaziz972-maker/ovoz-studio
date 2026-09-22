# Ovoz Studio — ish jurnali

Bu hujjat — nima, nima uchun va qanday tekshirilgani. Eski (0.1.0) jurnal
o'chirilgan imkoniyatlar (yozib olish, konvertor, ekvalayzer, shovqin, tezlik,
stem, teglar, ko'p yo'lli aralashtirish, audio-kitob) haqida edi; ular endi
ilovada yo'q.

## 0.2.0 — nima o'zgardi

Egasining talabi: ilovada **faqat** audioni kesib olish, o'chirish,
birlashtirish; PDF sahifalarini kesib olish va o'chirish; hujjatlarni ekran
o'quvchi yoki Microsoft Sardor/Madina ovozi bilan o'qish; til tizim tilidan
aniqlansin va ilova sodda o'zbekcha bo'lsin; xatolar jurnali bo'lsin; ilova tez ishlasin.

### Olib tashlandi
Yozib olish (mikrofon, xizmat), konvertor, ekvalayzer, shovqin tozalash, tezlik/ohang,
vokal ajratish, teglar, ko'p yo'lli aralashtirish, audio-kitob yig'ish, ovoz sinovi,
sozlamalar va til tanlash, kutubxona (yozuvlar ro'yxati). Ruxsatlar (mikrofon,
bildirishnoma, xizmat, WAKE_LOCK) ham olib tashlandi: **hech qanday ruxsat so'ralmaydi**.

### Qo'shildi
- **Qat'iy format qoidasi** (`StrictFormat`, `AudioOpener`): natija aynan yuklangan
  formatda; yozib bo'lmaydigan format ish boshlanmasdan oldin rad etiladi.
  Fallback (boshqa formatga taklif) butunlay yo'q.
- **Audio kesish / o'chirish** (`ui/trim`): fayl tizim tanlagichidan, asl format
  saqlanadi, natija «Saqlash» (tizim oynasi) yoki «Ulashish» bilan chiqadi.
  Chetlarda va tutashuvda 5 ms silliqlash (`AudioTrimmer.deleteRanges(joinFadeMs)`).
- **Birlashtirish** (`AudioMerger`, `ui/merge`): bir xil formatdagi fayllar; chastota
  (`Resampler`), kanal (mono→stereo) va bit chuqurligi moslashtiriladi; bir xil
  parametrda namunalar butun son ko'rinishida, bayt-bayt o'zgarmasdan ko'chadi.
- **PDF sahifalarini kesib olish / o'chirish** (`PdfPageTools`, `PageRange`):
  PDFBox orqali; natija yangi hujjatga ko'chirish yo'li bilan (o'chirilgan sahifa
  mazmuni qolmaydi); annotatsiyalar boshqa sahifaga bog'lanishi uzilgan; yozilgan
  fayl qayta ochib tekshiriladi.
- **Hujjat o'qish** (`ui/reader`): yangi o'quvchilar — RTF, ODT/ODS/ODP, FB2, HTML,
  PPTX; PDF matni PDFBox bilan (har sahifa — bob), o'z o'quvchimiz zaxira;
  kengaytma yolg'on bo'lsa imzo ustun (RTF `.doc` nomi bilan, PDF `.txt` nomi
  bilan); `ReadingText` abzatslarga bo'ladi (qator uzilishi, defisli so'z);
  `TextQuality` o'qib bo'lmaydigan («savatcha») matnni oldindan aniqlaydi.
- **Ovoz tanlash** (`DeviceTtsEngine`, `VoiceChoice`, `VoicePrefs`): dvigatel va
  ovoz tanlanadi va eslab qolinadi; nomida Sardor/Madina bor ovoz avtomatik tanlanadi.
- **Til** (`LocaleContext.followSystem`): tizim tillar ro'yxatidan ilova bilgan
  birinchisi (per-app til ham hisobga olinadi). Matnlar to'rt tilda, sodda.
- **Xatolar jurnali** (`log/`, `ui/log`): qurilmadagi matn fayli, hajmi cheklangan,
  shaxsiy ma'lumot yozilmaydi, kutilmagan to'xtashlar ham yoziladi; ko'rish,
  ulashish, saqlash, tozalash.
- **Skrinreader uchun**: har bir uzoq ish boshlanishi va xato **ovozda e'lon
  qilinadi** (`StatusMessage`, `rememberAnnouncer`); boshqaruv tugmalari matn tepasida.

### Tezlik
- `WavSampleReader` va `WavWriter`: bayt buferi qayta ishlatiladi, chuqurlik sikldan
  tashqarida tanlanadi (yuz millionlab ortiqcha shart yo'q).
- Kesishda silliqlash yo'q bo'lsa har kadr bo'ylab yurilmaydi.
- Dekoder chiqishi bir yo'la o'qiladi (`ShortBuffer.get(array)`).
- MP3 kodlash LAME `-q5` (standart) ga o'tkazildi: `-q2` ga nisbatan bir necha baravar
  tez, bit tezligi bir xil bo'lganda farq eshitilmaydi.
- Og'ir ishlar `Dispatchers.IO/Default`da; birinchi bobning abzatslarga bo'linishi
  asosiy oqimdan chiqarildi.

### Topilgan va tuzatilgan kamchiliklar
- Kesish/o'chirish natijasi faqat WAV bo'lib chiqardi (talab — asl format).
- Tutashuv joyida «chiqillash» (silliqlash faqat butun faylning boshi/oxirida edi).
- `MediaExtractor`/`MediaCodec` `IOException` dan boshqa istisno tashlasa ilova
  yiqilardi — endi hammasi ushlanadi va jurnalga yoziladi.
- Xatolar ekranda ko'rinar, lekin ekran o'quvchiga aytilmasdi.
- 8 va 32 bitli WAV tahrirlash paytida noaniq xato berardi — endi import paytida
  sababi aytiladi.
- Til: qo'lda tanlov yo'q, tizim tilidan aniq hisoblanadi.
- Ulashilgan natija ekran yopilganda o'chib ketardi — endi bir kun saqlanadi.

## Tekshiruv holati (halol hisobot)

Bu muhitda **Kotlin kompilyatori va Android SDK yo'q edi**, shuning uchun kod
**kompilyatsiya qilinmadi va testlar ishga tushirilmadi**. Buning o'rniga:

| Tekshiruv | Natija |
|---|---|
| Til fayllari (`bin/verify-locales.py`) | 178 kalit × 4 til, o'rin egallovchilar mos — **OK** |
| Qavs balansi, ichma-ich izoh, importlar (`check_kotlin.py`) | Xato topilgan va tuzatilgan (`audio/*` izohi kodni yutib yuborardi); qolgani toza |
| Chaqiruvlar imzosi (`check_calls.py`, 373 chaqiruv) | nom/son mos |
| `enum` bo'yicha `when` to'liqligi (`check_when.py`, 17 ta) | to'liq |
| Bog'liqlik yopilishi (JVM test ro'yxati) | Android'ga bog'liq fayl yo'q |
| Algoritmlar Python nusxasida (RTF, HTML, abzatslar, sahifa ro'yxati, tutashuv, matn sifati) | kutilgan qiymatlar mos, testlar shulardan yozilgan |

**Birinchi ish:** CI'da `gradle testDebugUnitTest` va `assembleDebug` ni ishga
tushirish. Yiqilsa — xato matnini yuboring, tuzatiladi. Telefonda tekshirilmagan
qismlar: `MediaCodec` dekodlash/kodlash, PDFBox, TTS dvigatelini almashtirish,
tizim «Saqlash» oynasi.

## Keyingi qadamlar
- Eski `.doc` (OLE) o'quvchisi.
- Qurilmada sinov: uzun MP3 (1 soat), 300 sahifali skaner PDF, TalkBack bilan yurish.
- PDF da xatcho'plarni saqlash (hozir yangi hujjatga ko'chirish xatcho'plarni tashlaydi).
