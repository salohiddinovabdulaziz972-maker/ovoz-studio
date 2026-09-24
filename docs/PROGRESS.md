# Ovoz Studio — ish jurnali

Bu hujjat — nima, nima uchun va qanday tekshirilgani. Eski (0.1.0) jurnal
o'chirilgan imkoniyatlar (yozib olish, konvertor, ekvalayzer, shovqin, tezlik,
stem, teglar, ko'p yo'lli aralashtirish, audio-kitob) haqida edi; ular endi
ilovada yo'q.

## 0.4.0 — audio-kitob (MP3) va ekran o'chganda to'xtamaslik

Egasining talabi: kitobni MP3 audio-kitob qilib saqlash imkoni yo'q edi —
qo'shildi. Ekran o'chganda jonli o'qish to'xtab qolardi — endi to'xtamaydi.
Buning uchun **yangi ruxsatlar** kerak bo'ldi (egasi tasdiqladi).

### Nima qo'shildi
- **Ovoz dvigateliga faylga yozish imkoniyati** (`VoiceEngine.synthesizeToFile`,
  `DeviceTtsEngine`): matnni jonli o'qimasdan, WAV fayliga yozadi.
- **`AudioBookExporter`**: hujjatni MP3 audio-kitobga aylantiruvchi asosiy
  mantiq — abzatslarni bo'lib ovozga o'giradi, ketma-ket qo'shadi
  (`AudioMerger`), MP3 ga kodlaydi (`Mp3Encoder`, nutq uchun 64 kbit/s).
- **`AudioBookService`**: eksportning o'zi shu yerda ketadi — ViewModel yoki
  ekran emas. Foydalanuvchi ekrandan chiqib ketsa, telefonni qulflasa ham
  ish davom etadi; bildirishnomada foiz va bosqich (ovozga o'girish → 
  qo'shish → MP3) ko'rinadi, bekor qilish tugmasi bilan.
- **`KeepAliveService`**: jonli o'qishda (`ReaderViewModel`) ekran o'chganda
  jarayon o'chirilib qolmasligi uchun — hech qanday ovoz mantig'i yo'q,
  faqat bildirishnoma orqali «bu ish muhim» deydi. `ReaderViewModel`ning
  `speaking` holatiga reaktiv ulangan: qaysi yo'l bilan to'xtaganidan
  qat'i nazar (pauza, hujjat tugashi, xato, ekrandan butunlay chiqish)
  xizmat to'g'ri to'xtaydi.
- **Yangi ekran**: bosh ekrandan «Hujjatni audio-kitob qilish (MP3)» —
  hujjat tanlanadi, ovoz/dvigatel/tezlik tanlanadi, natija saqlanadi yoki
  ulashiladi.

### Yangi ruxsatlar (manifestga qo'shildi)
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
`FOREGROUND_SERVICE_MEDIA_PROCESSING`, `POST_NOTIFICATIONS`. Bularsiz
Android ekran o'chganda yoki ilova fonga tushganda jarayonni o'chirib
qo'yishi mumkin edi. Boshqa hech qanday ruxsat qo'shilmadi — fayllar hali
ham faqat tizim tanlagichi orqali olinadi/saqlanadi.

### Tekshiruv
Bu safar ham kompilyator yo'q edi. Qo'shimcha: 207 til kaliti × 4 til (mos),
417+134 funksiya chaqiruvi, 18 `enum`/`when` bloki. Tekshiruv jarayonida
ikkita haqiqiy xato topildi va tuzatildi (ikkalasi ham shu safar yozilgan
yangi kodda): `continuation.resume(...)` dan keyin ortiqcha bo'sh figurali
qavs (sintaksis xatosi) va `resume` uchun import yetishmasligi. Bundan
tashqari, `job.cancel()` chaqirilganda coroutine mexanizmi tashlaydigan
haqiqiy `CancellationException` avval "xato" deb noto'g'ri ushlanardi —
endi "bekor qilindi" sifatida to'g'ri ushlanadi. Bitta yolg'on signal ham
chiqdi (tekshiruv skriptining o'zida): companion-object ichidagi
`AudioBookService.cancel(context)` chaqiruvi `AudioBookViewModel.cancel()`
bilan adashtirilgan — kodda xato emas, tekshiruv skriptining cheklovi.

### Hali qilinmagan
- Qurilmada sinov: foreground xizmat, TalkBack bilan yurish, uzun kitobni
  to'liq audio-kitob qilish.
- Audio-kitob ekranida bildirishnoma ruxsati so'ralishi (hozir tizim o'zi
  so'raydi, lekin ilova birinchi marta ochilganda tushuntirish yo'q).

## 0.3.0 — tezlik, jarayon foizi, ekran o'quvchi uchun qisqaroq yo'l

Egasining talabi: katta audio fayllarda ish sekin ketyapti — tezlashtirilsin;
barcha uzoq ish foiz bilan ko'rsatilsin (ekran o'quvchi bilan ham qulay
bo'lsin); kesish/o'chirish/birlashtirishda qo'shimcha parametrlar (vaqt
maydonlari) bitta joyga yig'ilsin — hozir «tugatish» tugmasigacha juda ko'p
tugma orqali o'tish kerak.

### Tezlik
- Kesish, o'chirish, birlashtirish va WAV o'qish/yozish bo'lagi 16 384 dan
  65 536 kadrga oshirildi (4 baravar kamroq o'qish/yozish chaqiruvi);
  WAV IO buferi 64 dan 256 KB ga.
- **Asl formatga qaytarish** (`FormatPreservingExporter`) bo'lagi 4096 dan
  65 536 ga oshirildi — bu eng katta topilma edi: MP3/FLAC kabi sof Java'da
  ketadigan kodlash bosqichi eng sekin joy, lekin bo'lagi eng kichik edi.

### Jarayon foizi (hammasi endi bor)
- Dekodlash (`AndroidAudioDecoder`) — avval umuman yo'q edi, endi
  `MediaExtractor`ning o'qilgan vaqtidan hisoblanadi.
- Import zanjiri (`AndroidAudioImporter` → `AudioOpener`) shu foizni ekranga
  yetkazadi.
- Kesish/o'chirish (`TrimViewModel`), birlashtirish — fayl qo'shish va
  birlashtirishning o'zi (`MergeViewModel`), asl formatga qaytarish
  (ikkalasida ham) — barchasi foiz beradi.
- PDF sahifalarini kesib olish/o'chirish (`PdfPageTools.extract/delete`) —
  sahifa-sahifa foiz.
- Yangi `WorkProgress` (`ui/common`): foiz ekranda uzluksiz ko'rinadi, TalkBack'ga
  esa faqat har 10 foizda bir marta e'lon qilinadi — aks holda «1%...2%...3%...»
  bilan boshqa hech narsani eshittirmas edi.

### Ekran o'quvchi uchun qisqaroq yo'l
- Yangi `ParamsGroup` (`ui/common`): qo'shimcha parametrlar (vaqt maydonlari,
  fayllar tartibi) YOPIQ boshlanadi — faqat xulosa va bitta «O'zgartirish»
  tugmasi. Standart holatda (butun faylni kesish/o'chirish, fayllar qo'shilgan
  tartibda) foydalanuvchi «tugatish» tugmasigacha 2-3 to'xtash bilan yetadi,
  avvalgi 10+ o'rniga.
- Kesish/o'chirish ekranida: boshlanish/tugash vaqti maydonlari va «Eshitish»
  tugmasi shu guruh ichida.
- Birlashtirish ekranida: fayllar ro'yxati (nom + Yuqoriga/Pastga/Olib
  tashlash — fayl sonига qarab o'sadigan) shu guruh ichida; format va
  umumiy uzunlik esa doim ko'rinadi.
- Yangi `a11yGroup()` (`ui/common/A11y.kt`): bir necha qatorni (fayl nomi,
  format, uzunlik) TalkBack uchun bitta to'xtash nuqtasiga birlashtiradi.

### Tekshiruv
Bu safar ham kompilyator yo'q edi. Qo'shimcha tekshirilgan: 181 til kaliti ×
4 til (mos), 373+119 funksiya chaqiruvi, 17 `enum`/`when` bloki — hammasi
avvalgidek toza. Yangi fayllar (`ParamsGroup.kt`, `ProgressBar.kt`) shu
tekshiruvlarga kiritildi.

### Hali qilinmagan (keyingi bosqich, egasi tasdiqladi)
- **Hujjatni MP3 audio-kitob qilib eksport qilish** — hozir faqat jonli
  o'qish bor (TTS bilan ekranda), faylga saqlash yo'q.
- **Ekran o'chganda o'qish/eksport to'xtamasligi** — buning uchun fon xizmati
  (foreground service) va bitta yangi ruxsat guruhi kerak: bildirishnoma
  (`POST_NOTIFICATIONS`) va fon ijro/qayta ishlash
  (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`).
  Bu ilovaning «hech qanday ruxsat so'ralmaydi» qoidasini o'zgartiradi —
  egasi buni bilib tasdiqladi.

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
