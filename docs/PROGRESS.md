# Holat va reja

Oxirgi yangilanish: 2026-09-17

## Bajarildi

1. **Loyiha skeleti** — Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3,
   minSdk 24, targetSdk 35. Gradle version catalog, manifest, launcher ikonkalari
   (barcha zichliklar uchun generatsiya qilingan), 4 til: uz (lotin),
   uz-Cyrl, ru, en — 88 ta satr, hammasi to'liq tarjima qilingan.
2. **Accessibility qatlami** — `ui/common/A11y.kt` va `ChoiceRow.kt`:
   yorliqsiz tugma bo'lishi mumkin emas (yorliq majburiy parametr), minimal
   tegish maydoni 48 dp, radio guruhlar `selectableGroup()` bilan, kalitlar
   `toggleable` qator sifatida (TalkBack butun qatorni o'qiydi).
3. **Yozib olish yadrosi** — `media/AudioRecorderEngine.kt`, `WavWriter.kt`,
   `WavFile.kt`, `RecorderConfig.kt`.
4. **Kesish yadrosi** — `media/AudioTrimmer.kt`, `media/AudioPlayer.kt`,
   `media/RecordingStore.kt`.
5. **Ekranlar** — bosh, yozib olish, kesish (+ uch ViewModel).
6. **Testlar** — 85 ta sof JVM testi (`app/src/test/…`), hammasi o'tadi.
   Yurgizish: `bash bin/run-tests.sh` (Android SDK kerak emas).
   CI'da ham ishlaydi: `.github/workflows/android.yml` → `testDebugUnitTest`.
7. **Android qatlamining kompilyatsiyasi** — `bin/typecheck-android.sh`:
   android.jar + AndroidX/Compose + Compose kompilyator plagini bilan barcha
   31 manba fayl kompilyatsiya qilinadi. Ilgari ekranlar va ViewModel'lar
   umuman kompilyatordan o'tmagan edi — xatolar faqat CI'da ko'rinardi.
   APK bermaydi (aapt2 faqat x86_64 uchun), lekin Kotlin xatolarini
   darhol topadi. `R` sinfi resurslardan generatsiya qilinadi (`R.string`,
   `R.drawable`); yangi tur ishlatilsa, skriptga ham qo'shiladi.
8. **Bo'lish va ko'p nuqtali o'chirish** — kesish ekranining oxirgi
   yetishmagan qismi. Bo'lish nuqtasi alohida maydonda kiritiladi;
   birinchi qism tahrirlash zanjirida qoladi, ikkinchisi kutubxonaga
   tushadi. Ko'p nuqtali o'chirishda bo'laklar ro'yxatga yig'iladi va
   har biri alohida olib tashlanadi.
9. **Fon rejimida yozish** — `media/RecordingService.kt`. Ekran o'chganda
   yoki ilova fonda qolganda jarayon endi o'ldirilmaydi.
10. **Format qatlami va formatni saqlash** — `media/format/`.
    `AudioFormatDetector` faylning formatini kengaytmaga emas, sarlavha
    baytlariga qarab aniqlaydi; `FormatSupport` Android'ning kodek
    imkoniyatlari jadvalini saqlaydi; `FormatPreservingExporter` tahrirlangan
    faylni manba formatida qaytaradi. FLAC kodlovchisi noldan yozildi
    (Android'da FLAC uchun kodlovchi yo'q, faqat dekoder). Batafsil:
    «Formatni saqlash» bo'limi pastda.

## Muhim texnik qarorlar

- **Ovoz har doim float ko'rinishida o'qiladi** (`ENCODING_PCM_FLOAT`), faylga
  yozishda 16 yoki 24-bitga o'giriladi. Sabab: `AudioRecord` 24-bitni faqat
  API 31+ da bera oladi, float esa API 21+ dan ishlaydi — shu yo'l bilan
  24-bit fayl deyarli har qanday qurilmada olinadi.
- **Hech bir amal faylni joyida o'zgartirmaydi.** Har bir kesish yangi fayl
  yozadi, tarix — fayllar ro'yxati. «Orqaga qaytarish» — ro'yxatda bir qadam
  orqaga. Ma'lumot yo'qolmaydi.
- **Xatolar kod sifatida qaytariladi** (`TrimError`), matn UI qatlamida
  tarjima qilinadi. Aks holda rus tilidagi qurilmada o'zbekcha xato chiqardi.
- **Vaqt to'rt alohida maydonda kiritiladi**, bitta matn maydonida emas:
  raqamli klaviaturada `:` yo'q, ekran o'quvchi uchun esa har bir maydon
  alohida o'qiladi.
- **Taymer ovoz bilan avtomatik e'lon qilinmaydi.** Har soniyada o'qish
  ekran o'quvchisini bosib ketadi — vaqt faqat tugma bosilganda aytiladi.
- **`MediaPlayer` ishlatilgan**, Media3 emas: WAV qurilmaning o'zi o'qiydi,
  ortiqcha kutubxona va versiya xatolari yo'q.
- **Ovozli vaqt birliklari resurslardan olinadi** (`SpokenTime.kt`):
  o'zbekcha qurilmada ekran o'quvchi «3 daqiqa 12 soniya» deb o'qiydi,
  inglizcha «3 min 12 s» emas.
- **Standart til — o'zbekcha.** `values/strings.xml` — o'zbekcha (lotin),
  `values-en/`, `values-ru/`, `values-b+uz+Cyrl/` — qolganlari. Android
  `values/` ni zaxira sifatida ishlatadi: qurilma tili ro'yxatda bo'lmasa,
  ilova o'zbekcha ochiladi. Ilgari `values/` inglizcha edi — o'zbek
  foydalanuvchisi uchun bu noto'g'ri zaxira edi.
- **Kesish oraliqlari — yarim ochiq**: `[boshlanish, tugash)`. `LongRange`
  ataylab ishlatilmaydi (`AudioTrimmer.Cut`), chunki uning oxirgi elementi
  kiradimi-yo'qmi ko'rinmaydi va aynan shu noaniqlik bir kadr yo'qolishiga
  olib kelgan edi.
- **Namunalar kodlovchiga butun son ko'rinishida beriladi**, float emas.
  Sabab: float32 faqat 24 bitgacha bo'lgan butun sonlarni aniq saqlaydi,
  24-bit tovush esa ±2^23 chegarasida yuradi — namuna float orqali o'tib
  qaytsa, chegaradagi qiymatlar bir birlikka surilardi. Formatni saqlash
  talabi aynan shu aniqlikni talab qiladi.

## Formatni saqlash — egasining talabi

Talab: *«Foydalanuvchi ilovaga qanday audio format yuklasa, tahrirlash
jarayonida ish yakunlangandan keyin shunday format qaytarilsin, boshqalari
o'zgartirilmasdan.»*

Qanday bajariladi:

1. Import paytida `AudioFormatDetector` faylning konteyneri va kodekini
   **sarlavha baytlaridan** aniqlaydi — kengaytmadan emas. `ovoz.mp3` deb
   nomlangan FLAC fayl noto'g'ri kodek tanlashiga olib kelmasligi kerak.
2. Aniqlangan `AudioFormat` loyiha bilan birga saqlanadi.
3. Tahrirlash ichkarida yo'qotishsiz PCM ustida ketadi — kesish, bo'lish,
   fade va aralashtirish boshqacha bo'lishi mumkin emas.
4. Saqlashda `FormatPreservingExporter` natijani manbaning konteyneri va
   kodekiga qayta kodlaydi. Asl fayl hech qachon o'zgartirilmaydi: natija
   har doim yangi faylga yoziladi.
5. Boshqa formatga o'tish faqat konvertor ekranida ochiq so'ralganda bo'ladi
   (`FormatPreservingExporter.export(override = …)`).

**Nima saqlanadi, nima saqlanmaydi.** Konteyner va kodek — saqlanadi.
Chastota, kanal soni va bit chuqurligi — tahrirlangan fayldan olinadi, ya'ni
ular ham amalda manbaniki bo'lib qoladi (tahrirlash ularni o'zgartirmaydi).
Agar kelajakda biror amal ularni o'zgartirsa, sarlavha fayl ichidagi
haqiqatga mos kelishi kerak — shuning uchun manba emas, fayl ustun turadi.

**Qo'llab-quvvatlash jadvali.** O'qish: WAV, FLAC, MP3, M4A/AAC, OGG
(Vorbis va Opus) — hammasi ishlaydi; **WMA ishlamaydi**, Android'da u uchun
dekoder umuman yo'q. Yozish: WAV va FLAC — o'z kodlovchilarimiz; MP3 —
`jump3r` (sof Java); M4A/AAC — `MediaCodec` + `MediaMuxer`; Opus — API 29+.
**OGG/Vorbis yozilmaydi**: Android'da Vorbis kodlovchisi yo'q, faqat
dekoder. Bunday manba import qilinsa, ilova buni import paytida aytadi va
o'rniga yo'qotishsiz FLAC taklif qiladi — sabab `FallbackReason` kodida
qaytariladi, matn UI qatlamida tarjima qilinadi.

**FLAC kodlovchisi o'zimizniki.** Android'da FLAC uchun dekoder bor, kodlovchi
yo'q; mavjud sof Java kutubxonalari (`jflac`) 2012-yildan beri
yangilanmagan va Android'da bo'lmagan `javax.sound.sampled` ga tayanadi.
Shuning uchun `FlacEncoder` noldan yozildi: STREAMINFO + MD5, FIXED
bashoratchilar, Rice qoldiq kodlash, CRC-8 va CRC-16. Tashqi tekshiruv:
chiqqan fayl `ffmpeg` bilan ochiladi va PCM manba bilan **bayt-bayt**
solishtiriladi — jim, doimiy, arra, sinus, shovqin, 8/16/24-bit, mono va
stereo; sakkizalasida ham mos keldi, STREAMINFO ichidagi nazorat summasi
ham to'g'ri chiqdi.

## Tuzatilgan xatolar (2026-09-17 tekshiruvi)

Topilgan va tuzatilgan, chunki ular jimgina ma'lumot yo'qotardi yoki ilovani
umuman yig'ib bo'lmasdi:

1. `TimeParts.isEmpty` — `millis.isEmpty` (qavssiz). **Ilova kompilyatsiya
   qilinmasdi.** Kotlin kompilyatori topdi.
2. `AudioTrimmer.deleteRanges` — oraliq chegarasida bittadan kadr yo'qolardi
   (1000 kadrli faylda 500 o'rniga 498). Test topdi, `Cut` turi bilan
   tuzatildi.
3. `WavWriter.write` — massivda yetarli namuna bo'lmasa ham hisoblagich
   to'liq kadrlarni sanardi va sarlavha fayldagidan ko'p ma'lumot va'da
   qilardi (fayl buzilardi).
4. `RecordingStore` — fayl nomi sekundgacha aniq edi, shu sababli bir sekund
   ichida saqlash mavjud yozuvning ustiga yozardi.
5. `TrimViewModel.save()` — saqlashdan keyin oraliq fayllar o'chirilardi, tarix
   esa ularga ishora qilib qolardi: «orqaga qaytarish» mavjud bo'lmagan faylga
   olib borardi.
6. `TrimViewModel.deleteSelection()` — bo'sh yoki teskari oraliqda «butun
   faylni o'chirib bo'lmaydi» deb xato xabar chiqarardi.
7. `AudioRecorderEngine.start()` — `startRecording()` xato berganda `running`
   `true` bo'lib qolardi va ochilgan resurslar bo'shatilmasdi: keyingi urinish
   «yozish allaqachon ketmoqda» bilan yopilardi.
8. `TimeFormat.formatSpoken` — birliklar inglizcha qattiq yozilgan edi.
9. O'lik kod (`TimeParts.describe()`) olib tashlandi, `TimeParts` UI faylidan
   `util/` ga ko'chirildi (mantiq endi Android'siz test qilinadi).

### Ikkinchi tekshiruv — kompilyator bilan (2026-09-17)

`bin/typecheck-android.sh` yozilgach, ekranlar va ViewModel'lar birinchi marta
haqiqiy kompilyatordan o'tdi. Topilgani:

10. `RecordScreen` — `spokenTime()` (u `@Composable`) `LaunchedEffect` blokida
    va `onClick` lambdasida chaqirilgan edi. **Ilova yig'ilmasdi.** Ikkalasi
    ham tuzatildi: matn kompozitsiyada hisoblanadi, effektga tayyor holda
    uzatiladi.
11. `TrimViewModel` — `TrimUiState` ning boshlang'ich qiymati `DEFAULT_FADE_MS`
    ni sinf ichidagi `private companion` dan olardi; u yerdan ko'rinmaydi.
    **Ilova yig'ilmasdi.** Doimiylik fayl darajasiga chiqarildi.
12. `Icons.Filled.Delete` ishlatilgan, lekin `material-icons-core` bog'liqligi
    e'lon qilinmagan edi — material3 uni o'zi bilan olib kelmaydi.
13. Bosh ekran ro'yxati yangilanmasdi: `HomeViewModel` fayllarni faqat
    yaratilganda bir marta o'qirdi, shuning uchun yangi yozuv yoki saqlangan
    tahrir ro'yxatda ko'rinmasdi. Endi ekranga har qaytilganda qayta o'qiladi.
14. Manifestda ishlatilmaydigan `READ_EXTERNAL_STORAGE` va `READ_MEDIA_AUDIO`
    ruxsatlari bor edi — olib tashlandi. Ilova faqat mikrofon ruxsatini
    so'raydi.
15. `locales_config.xml` qo'shildi: Android 13+ da foydalanuvchi tilni qurilma
    tilidan mustaqil tanlay oladi (o'zbek, o'zbek-kirill, rus, ingliz).
16. `RecordingService` — `getSystemService(NotificationManager::class.java)`
    null bo'lishi mumkin, kod esa darhol metod chaqirardi. **Ilova
    yig'ilmasdi.** Type-check skripti topdi (CI'ni kutmasdan).
17. `RecordingService` uchun `R.drawable.ic_mic` ishlatilgan edi, type-check
    `R` stub'i esa faqat `R.string` ni bilardi — skript resurs turlarini
    generatsiya qiladigan qilib kengaytirildi. Sabab: tekshiruv vositasi
    kod ortidan emas, kod bilan birga o'sishi kerak.

### Uchinchi tekshiruv — ffmpeg bilan (2026-09-17)

FLAC kodlovchisi o'qib tekshirilganda to'g'ri ko'rinardi, lekin chiqqan
faylni `ffmpeg` ocholmasdi. Sababi faqat haqiqiy dekoder bilan aniqlanadi:

18. **Isrof bitlar bayrog'i har doim `1` yozilardi.** Uchta shoxning
    uchalasida ham `bw.write(1, 1)` shartsiz turgan edi. Isrof bitlar
    bo'lmaganda dekoder bayroqni `1` deb o'qib, qoldiq ma'lumotini unar
    o'lcham sifatida yeydi. `writeSubframeHeader(tur, isrof)` ajratildi:
    bayroq faqat haqiqatan isrof bit bo'lganda `1`.
19. **Bo'laklash tartibi blok hajmi bo'yicha hisoblanmasdi.** `n >> po`
    (blok hajmi) o'rniga qoldiqlar soni bo'yicha bo'linardi va birinchi
    bo'lak uzunligi boshqacha chiqardi. To'g'ri qoida: har bir bo'lak
    `n >> po` namuna oladi, faqat birinchisi `order` taga kam.
20. **Qoldiq kodlash usuli maydoni umuman yozilmasdi.** FLAC'da FIXED
    pastki freymdan keyin 2 bitlik usul maydoni majburiy. U tushib qolganda
    dekoder bo'laklash tartibini 2 bitga surib o'qiydi — `ffmpeg`
    «invalid residual» derdi. `RICE_METHOD_4BIT` qo'shildi.

Uchtasi ham faqat tashqi dekoder bilan topildi. Shu sababli qoida: yangi
kodlovchi yozilganda u **mustaqil dekoderda** tekshiriladi, o'z-o'zidan
emas — o'z-o'zini tekshirish «to'g'ri ko'rinadi» degan natijadan nariga
o'tmaydi.

## Yo'l xaritasi — egasining tavsifidagi imkoniyatlar

Har bir band — egasi bergan tavsifning bo'limi. Tartib: avval mavjud
imkoniyatni mustahkamlash, keyin yangisini qo'shish.

**Tayyor**

- Til va accessibility: tizim tilini aniqlash, o'zbek (lotin + kirill),
  rus, ingliz; yorliqsiz tugma yo'q; 48 dp tegish maydoni; vaqt faqat
  so'ralganda aytiladi.
- Aniq kesish: soat/daqiqa/soniya/millisoniya qo'lda kiritiladi, fade in/out,
  tanlangan qismni eshitish, orqaga/oldinga qaytarish.
- Bo'lish va ko'p nuqtali o'chirish.
- Yozib olish: WAV 16/24-bit, 44.1/48/96 kHz, pauza/davom, belgilar,
  shovqin bostirish va exo yo'qotish; endi fon rejimida ham.
- Fayl kutubxonasi: ro'yxat, o'chirish, kesishga o'tish.

**Keyingi navbat (shu tartibda)**

1. **Format konvertori** — qaror qilindi: `MediaCodec`, ffmpeg'siz.
   Fon qatlami tayyor (`media/format/`): aniqlash, imkoniyatlar jadvali,
   formatni saqlash, WAV va FLAC kodlovchilari, konvertor oqimi.
   Qolgani: MP3 kodlovchisini `jump3r` orqali ulash, `MediaCodec`
   kodlovchilari (M4A/AAC/Opus) va konvertor ekrani. WMA hech qachon
   ishlamaydi — Android'da dekoderi yo'q, buni foydalanuvchiga import
   paytida aytamiz. OGG/Vorbis ham yozilmaydi (kodlovchi yo'q) — bunday
   fayl import qilinsa, o'rniga FLAC taklif qilinadi.
2. **Parametrik ekvalayzer** — 10 va 31 polosa, biquad filtrlar, float
   domenida. Ekran o'quvchi uchun har bir polosa raqamli maydonda.
3. **Tezlik va ohang** — ohangni saqlab tezlashtirish (WSOLA), 0.5x–2x.
4. **Shovqin tozalash** — spektral ayirish; avval shovqin namunasi olinadi.
5. **Ovoz dvigateli abstraksiyasi** (`VoiceEngine` + `DeviceTtsEngine`) —
   qurilma TTS'i, keyin bulut AI ovozini shu interfeys ortiga ulash.
6. **Hujjat → audio-kitob** — PDF/DOCX/TXT/EPUB, boblarga bo'lish, har bob
   alohida MP3, avtomatik belgilar, uxlash taymeri. 5-bandga tayanadi.
7. **ID3 teglar va ulashish** — nom, ijrochi, albom, muqova; faylni boshqa
   ilovaga yuborish.
8. **Ko'p yo'lli aralashtirish** — har bir yo'lga ovoz balandligi, panorama,
   ducking.
9. **Sozlamalar ekrani** — til tanlash, soddalashtirilgan rejim, ilova haqida.
10. **Vokal/cholg'u ajratish** — qurilmada ishlaydigan model (ONNX/TFLite);
    eng og'ir band, shuning uchun oxirida.

**Faqat egasi bajaradi**

- APK'ni qurilmada, TalkBack yoqilgan holda qo'lda sinash: yozish, kesish,
  bo'lish, ekran o'chganda yozuv davom etishi.

## Ochiq savollar

- Ilovaning yakuniy nomi va paket nomi (`uz.ovozstudio.app` — vaqtinchalik).
- ~~Litsenziya~~ — **hal qilindi: GPL-3.0** (`LICENSE`). Sabab: egasining
  talabi «hech kim reklama joylolmasin». GPL yopiq kodli forkni taqiqlaydi,
  ya'ni reklamali yopiq nusxa paydo bo'lishi huquqiy jihatdan mumkin emas.
  MIT bu talabni bajarmasdi.
- Ovozlar: qurilma TTS'i bilan boshlanadi; bulut AI ovoziga o'tish qarori
  ovoz sifati sinovidan keyin.
- Fayllarni «Musiqa» papkasiga chiqarish (MediaStore) kerakmi — hozir
  ilovaning o'z papkasida, ruxsat so'ralmaydi.
