# Holat va reja

Oxirgi yangilanish: 2026-09-17

## Bajarildi

1. **Loyiha skeleti** — Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3,
   minSdk 24, targetSdk 35. Gradle version catalog, manifest, launcher ikonkalari
   (barcha zichliklar uchun generatsiya qilingan), 4 til: uz (lotin),
   uz-Cyrl, ru, en — 210 ta satr, hammasi to'liq tarjima qilingan.
2. **Accessibility qatlami** — `ui/common/A11y.kt` va `ChoiceRow.kt`:
   yorliqsiz tugma bo'lishi mumkin emas (yorliq majburiy parametr), minimal
   tegish maydoni 48 dp, radio guruhlar `selectableGroup()` bilan, kalitlar
   `toggleable` qator sifatida (TalkBack butun qatorni o'qiydi).
3. **Yozib olish yadrosi** — `media/AudioRecorderEngine.kt`, `WavWriter.kt`,
   `WavFile.kt`, `RecorderConfig.kt`.
4. **Kesish yadrosi** — `media/AudioTrimmer.kt`, `media/AudioPlayer.kt`,
   `media/RecordingStore.kt`.
5. **Ekranlar** — bosh, yozib olish, kesish (+ uch ViewModel).
6. **Testlar** — 400 ta sof JVM testi (`app/src/test/…`), hammasi o'tadi.
   Yurgizish: `bash bin/run-tests.sh` (Android SDK kerak emas).
   CI'da ham ishlaydi: `.github/workflows/android.yml` → `testDebugUnitTest`.
   Fayl ro'yxati skriptda qo'lda yuritiladi (hamma manba fayl oddiy
   `kotlinc` bilan yig'ilavermaydi — Android'ga bog'liqlari bor), lekin
   ro'yxatga tushmay qolgan test endi **jimgina o'tib ketmaydi**: skript
   har bir `*Test.kt` ni ro'yxatda qidiradi va topmasa `exit 2` beradi.
7. **Android qatlamining kompilyatsiyasi** — `bin/typecheck-android.sh`:
   android.jar + AndroidX/Compose + Compose kompilyator plagini bilan barcha
   68 manba fayl kompilyatsiya qilinadi. Ilgari ekranlar va ViewModel'lar
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
    (Android'da FLAC uchun kodlovchi yo'q, faqat dekoder). MP3 kodlovchisi
    LAME'ning sof Java porti (`de.sciss:jump3r`) ustiga qurildi — Android'da
    MP3 uchun ham faqat dekoder bor. Batafsil: «Formatni saqlash» bo'limi
    pastda.
11. **Import va konvertor ekrani** — `AndroidAudioDecoder` (MediaExtractor +
    MediaCodec) har qanday siqilgan faylni WAV ga ochadi; WAV manba esa
    umuman qayta kodlanmaydi (24-bit 24-bit bo'lib qoladi).
    `AndroidAudioEncoders` tizim kodlovchilarini (AAC, Opus) ulaydi.
    Konvertor ekrani: fayl tanlanadi, maqsad format ro'yxati esa **shu fayl
    uchun** mos variantlardan tuziladi — ya'ni "tanladingiz, lekin yozib
    bo'lmadi" holati bo'lmaydi. Tanlov dastlab manba formatida turadi.
12. **Parametrik ekvalayzer** — `media/dsp/` (`Biquad.kt`, `EqBands.kt`,
    `Equalizer.kt`) va `ui/eq/` ekrani (`EqScreen.kt`, `EqViewModel.kt`). 10 va 31 polosali to'r,
    6 tayyor profil (tekis, ovoz, bass, baland, rok, podkast), qo'lda
    past chastota kesish. Har bir polosa ekranda **alohida raqamli
    maydon**: ekran o'quvchi uchun siljish emas, kiritish. Filtrlash
    ikki marta o'tadi — birinchisi cho'qqini o'lchaydi, kerak bo'lsa
    ikkinchisi butun faylni bir xil koeffitsientga tushiradi (pastda).
    Mustaqil tekshiruv: `bin/verify-eq.sh` (ffmpeg bilan, pastda).
13. **Tezlik va ohang** — `media/dsp/` (`Wsola.kt`, `Resampler.kt`,
    `SpeedPitch.kt`) va `ui/speed/` ekrani. Tezlik ohangni buzmasdan
    o'zgaradi: WSOLA kanallar bo'ylab bir xil siljish bilan ishlaydi, ya'ni
    kanallararo faza saqlanadi. Ohang esa namunalar sonini o'zgartirmaydi —
    shuning uchun tezlik va ohang bir-biriga tegmaydi va ikkalasini bir
    vaqtda qo'llash mumkin. Ekranda ikkala qiymat ham **qo'lda kiritiladi**
    (ilova bo'ylab yagona qoida), natijadagi uzunlik esa darhol ko'rsatiladi.
    Mustaqil tekshiruv: `bin/verify-speed.sh` (ffmpeg bilan, pastda).
14. **Umumiy UI bo'laklari** — `ui/common/NumericRow.kt` (yorliqli raqamli
    maydon va ± tugmalari), `ui/common/FileSummary.kt` (kanal/chastota/uzunlik
    qatori), `util/DecimalText.kt` (kiritish qoidalari). Ekvalayzer, tezlik va
    konvertor ekranlari shularni ishlatadi: bir xil sozlama uch xil ko'rinishda
    bo'lmasligi uchun mantiq bir joyda turadi.
15. **Shovqin tozalash** — `media/dsp/NoiseReducer.kt` (FFT ustida spektral
    ayirish) va `ui/noise/` ekrani. Foydalanuvchi **shovqin namunasini**
    belgilaydi — odatda yozuvning boshi, hali hech kim gapirmagan qismi;
    ilova o'sha oraliqning chastota profili bo'yicha butun fayldan shovqinni
    ayiradi. Ekranda namuna chegaralari vaqt maydonlarida, kuch va qoldiq
    esa **qo'lda kiritiladi** (ilova bo'ylab yagona qoida).
    Mustaqil tekshiruv: `bin/verify-noise.sh` (yettinchi tekshiruv, pastda).
    **Muhim:** bu statistik usul — neyron tarmoq emas (pastda ochiq yozilgan).
16. **Ovoz dvigateli abstraksiyasi** — `media/voice/`. `VoiceEngine` interfeysi
    (tayyorlash, ovozlar ro'yxati, tilni tanlash, o'qish, to'xtatish) va uning
    birinchi amalga oshirilishi `DeviceTtsEngine` — qurilmaning o'z sintezatori
    (`android.speech.tts.TextToSpeech`). Yonida ikkita sof Kotlin bo'lagi:
    `TextChunker` (uzun matnni jumla chegarasida bo'laklarga bo'ladi — sintezator
    chegaradan uzun matnni jimgina tashlab ketadi) va `ScriptDetector` (matn
    lotin yoki kirill ekanini aniqlab, mos til nomzodlarini beradi).
    `ui/voice/` — tekshiruv ekrani: qurilmada qanday ovozlar bor, o'zbek ovozi
    topildimi, uzun matn to'g'ri bo'linyaptimi. Matn, tezlik va balandlik
    **qo'lda kiritiladi** (ilova bo'ylab yagona qoida).
    Manifestga `<queries>` bloki qo'shildi: Android 11+ da ilova o'zi ko'rmagan
    xizmatni so'ray olmaydi, usiz ovozlar ro'yxati bo'sh qaytardi.
    Halol izoh: bu **qurilma ovozi**, neyron AI ovozi emas. O'zbek ovozi
    o'rnatilgan bo'lsa — o'zbekcha o'qiydi; bo'lmasa zaxira tilga o'tadi va
    talaffuz boshqacha bo'lishi mumkin. Buni skript bilan tekshirib bo'lmaydi:
    ovozning talaffuzi faqat quloq bilan, faqat o'sha qurilmada tekshiriladi.

17. **Hujjat → audio-kitob** — `media/doc/` va `media/book/` + `ui/book/`.
    Hujjat to'rt formatda o'qiladi: **TXT** (jadval aniqlanadi: UTF-8,
    UTF-16, windows-1251), **DOCX**, **EPUB**, **PDF**. Matn boblarga
    bo'linadi (`ChapterSplitter` — hujjat formati o'z sarlavhalarini bersa
    o'shandan, oddiy matnda esa sarlavha naqshidan), har bir bo'lak qurilma
    sintezatori bilan **faylga** o'qiladi (`synthesizeToFile`), bo'laklar
    bitta bob fayliga qo'shiladi (`WavJoiner` + `ChapterAssembler`) va bob
    **MP3** bo'lib chiqadi (`Mp3Encoder`). Har bir bob uchun belgilar
    varaqasi (CUE) yoziladi, fayl nomida bob tartib raqami turadi
    (`Kitob - 01 - BIRINCHI BOB.mp3`) — pleyerda tartib aralashmasin.
    `ui/book/` ekrani: hujjat tanlash, boblar ro'yxatini oldindan ko'rsatish
    (bo'linish to'g'rimi — foydalanuvchi yasashdan oldin ko'radi), tezlik va
    balandlikni qo'lda kiritish, jarayon foizi, to'xtatish, tayyor fayllar.
    Butun zanjir sof JVM'da 44 ta test bilan qoplangan (`BookBuilder` soxta
    sintezator bilan: tartib, pauza, tozalash, to'xtatish, xato bob raqami).
    **Mustaqil tekshiruv:** PDF o'quvchi `fpdf2` bilan yasalgan namunalarda
    tekshiriladi (`tools/make-pdf-fixtures.py`) — matn to'liq solishtiriladi,
    o'zimiz yozgan PDF'ni o'zimiz o'qib «to'g'ri» deb qo'ya qolmaymiz.
    **Halol cheklovlar:** PDF'da Type0 shrifti bo'lib, `ToUnicode` jadvali
    bo'lmasa — o'qish **ataylab to'xtatiladi** (aks holda matn «savatcha»
    bo'lib chiqardi, ya'ni foydalanuvchi tushunarsiz kitobni tinglardi);
    skaner qilingan PDF'da matn qatlami yo'q va bu alohida xabar bilan
    aytiladi (OCR ilovada yo'q). PDF o'quvchi **o'zimizniki** — tashqi
    kutubxona olinmadi (pastda sababi).

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
- **Ekvalayzer polosalari — bitta qo'ng'iroq filtr** (RBJ formulasi,
  `Biquad.kt`), qo'shnilari bilan qo'shilib silliq egri chiziq beradi.
  Kenglik polosa soniga bog'lanadi: 10 polosada Q = 1.41, 31 polosada
  Q = 4.32 — ya'ni bir oktava, ikki tomondan yarim oktava.
- **Hisob ikki aniqlikda (`Double`) va har bir (filtr, kanal) uchun alohida
  holat bilan.** Sabab ikkita: `Float` da 31 polosali kaskad past
  chastotalarda eshitiladigan xato yig'ardi; umumiy holat esa kanallarni
  bir-biriga aralashtirib, stereoni mono tomonga surardi.
- **Kesish himoyasi ikki o'tishli.** Birinchi o'tish natijani vaqtinchalik
  faylga yozadi va faqat **cho'qqini** o'lchaydi; cho'qqi 0.999 dan oshsa,
  ikkinchi o'tish butun faylni **bitta** koeffitsientga tushiradi. Sabab:
  kuchaytirish fayl davomida bir xil, ya'ni cho'qqini bilgan holda butun
  faylni oldindan tushirish mumkin — natijada har bir namuna o'z chegarasiga
  urilib buzilish (limiter) o'rniga fayl butunlay toza qoladi.
- **Chastota va desibel sonlari tilga mos yoziladi** (`util/LocalizedNumber.kt`):
  o'zbek va rus tillarida o'nlik kasr vergul bilan («31,5 Gerts»), ingliz
  tilida nuqta bilan. `toString()` har doim nuqta beradi — ekran o'quvchi
  uni «o'ttiz bir nuqta besh» deb o'qib, sonni buzardi. Mingliklar
  ajratgichi ataylab qo'yilmaydi: guruhlash o'zbek tilida bo'sh joy bilan
  yoziladi va u ikkita son bo'lib eshitilishi mumkin.
- **Kuchaytirish matni alohida qatlamda tozalanadi** (`util/GainText.kt`):
  maydonga faqat raqam va bitta ajratgich o'tadi, kiritish paytida
  chegaralanadi (±12 dB). Sabab: ekran o'quvchi bilan ishlaganda
  foydalanuvchi kiritgan matnni «keyin tuzatamiz» deb qoldirib bo'lmaydi —
  u qaysi sonni kiritganini eshitmaydi.

- **PDF o'quvchi o'zimizniki, tashqi kutubxona olinmadi.** Odatdagi yo'l —
  `pdfbox-android`, lekin u Android-only AAR: bu konteynerda uni na yig'ib,
  na sinab bo'ladi, ya'ni matn to'g'ri o'qilayotganini hech kim
  tekshirmagan bo'lardi. Buning o'rniga `media/doc/PdfTextReader.kt`
  yozildi (arxiv oqimlarini ochish, sahifa daraxti, `Tj`/`TJ` amallari,
  `ToUnicode` jadvali) va u **fpdf2** bilan yasalgan namunalarda so'zma-so'z
  solishtiriladi. Narxi: PDF'ning hamma imkoniyati qo'llanmaydi (shifrlangan
  fayl, `LZWDecode`, ba'zi CMap shakllari) — bunday fayl jimgina noto'g'ri
  o'qilmaydi, ochiq xato beradi.
- **Audio-kitob mantig'i `media/` da, ViewModel'da emas.** `BookBuilder`
  sintezatorni interfeys orqali oladi, ya'ni butun zanjir (tartib, pauza,
  xato bob raqami, to'xtatish, vaqtinchalik fayllarni tozalash) soxta
  sintezator bilan sof JVM'da tekshiriladi. ViewModel'da faqat fayl
  tanlash, holat va jarayon qoladi.
- **Import qilingan hujjat ilova papkasiga nusxalanadi.** Tizim tanlagichi
  bergan `content://` havolasi faqat shu seansda yashaydi; kitob yasash esa
  undan keyin ham davom etadi. Nusxa `manba/` papkasiga tushadi.

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

**MP3 kodlovchisi `jump3r` ustida.** Android'da MP3 uchun ham faqat dekoder
bor, kodlovchi yo'q. `jump3r` — LAME'ning sof Java porti; uning `mp3` va
`mpg` paketlari Android'ga bog'liq emas. Kutubxonaning qulay o'rami
(`de.sciss.jump3r.lowlevel.LameEncoder`) esa `javax.sound.sampled` ga
tayanadi — u ishlatilmaydi va ProGuard qoidasi bilan chiqarib tashlanadi.
Litsenziya: LGPL-2.1+ (LAME'dan meros), loyihaning GPL-3.0'i bilan mos.

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

### To'rtinchi tekshiruv — MP3 kodlovchisi (2026-09-17)

21. **LAME modullari ulanish tartibi buzilgan edi.** `jump3r` ning past
    darajali API'si `setModules` orqali qo'lda ulanadi; men modullarni
    yuqoridan pastga uzatdim va ikkita `NullPointerException` oldim
    (`this.bs is null`, keyin `this.lame is null`). Modullar bir-biriga
    **ikki tomonlama** bog'langan: `BitStream` `VBRTag` ni biladi, `VBRTag`
    esa `BitStream` ni. To'g'ri tartib — pastdan yuqoriga: avval barcha
    nusxalar yaratiladi, keyin ichki bog'lanishlar, eng oxirida
    `lame.setModules`. Psixoakustik model esa `Lame` ichida yaratiladi va
    `QuantizePVT` **o'sha** nusxani olishi shart (`lame.enc.psy`), aks holda
    u bo'sh qolib kodlash paytida yiqilardi.
22. **Yo'qotishli konteynerda bit chuqurligi `null` bo'lib qolardi.**
    `FormatPreservingExporter` MP3/AAC uchun `bitDepth` ni ataylab `null`
    qilardi — «konteyner sarlavhasida bunday maydon yo'q» degan mantiq
    bilan. Lekin kodlovchi bu maydonni boshqa ma'noda ishlatadi: u
    namunalar **shkalasi**. Natijada 24-bit manba 16-bit deb hisoblanib,
    namunalar 8 bit ortiq surilib, ovoz butunlay buzilardi (RMS xato 16398,
    to'liq shkalaning yarmi). Endi `bitDepth` har doim tahrirlangan fayldan
    olinadi, ma'nosi esa `AudioFormat` hujjatida yozib qo'yildi.

**Tekshiruv natijasi** (`bash bin/verify-mp3.sh`, ffmpeg bilan):

| holat | siljish | RMS xato | native LAME |
|---|---|---|---|
| stereo 192 kbps | 2257 | 172.7 | 172.6 |
| mono 96 kbps | 2257 | 407.0 | 386.5 |
| stereo VBR | 2257 | 17.7 | 40.1 |
| mono 24-bit 192 kbps | 2257 | 231.5 | 231.4 |
| mono 24-bit VBR | 2257 | 99.1 | 47.8 |

Siljish besh holatda ham **aynan 2257 kadr** — bu 576 (kodlovchi
kechikishi) + 529 (dekoder kechikishi) + 1152 (kadr) yig'indisi, ya'ni
MP3 uchun darslikdagi qiymat. RMS xato native LAME bilan bir darajada
(ba'zi holatda yaxshiroq). Ya'ni port nafaqat «ochiladigan» fayl beradi,
balki haqiqiy LAME sifati bilan kodlaydi.

JVM sinovlari ataylab faqat **tuzilishni** tekshiradi (kadr sinxronizatsiyasi,
bit tezligi, kadr o'lchami) — ovozni ular ichida tekshirish o'z-o'zini
tekshirish bo'lardi. Shuning uchun ffmpeg tekshiruvi alohida skriptda:
`bin/verify-mp3.sh` + `bin/verify-mp3-compare.py`.

### Beshinchi tekshiruv — ekvalayzer (2026-09-17)

Ekvalayzer filtrning matematikasini o'z koeffitsientlari orqali tekshirsa,
bu o'z-o'zini tekshirish bo'lardi (uchinchi tekshiruvdagi qoida). Shuning
uchun o'lchovni **ffmpeg** bajaradi, kutilgan qiymatlar esa ilovadan emas,
spetsifikatsiyadan olinadi: desibel ta'rifi va Buterworth javobi
`|H| = w²/√(1+w⁴)`.

**A. Ohang bilan o'lchov** (`bin/verify-eq.sh`) — bitta sinus, ilova
filtrlaydi, ffmpeg RMS darajasini o'lchaydi:

| holat | o'lchangan | kutilgan |
|---|---|---|
| 1000 Hz +6 dB | 6.000 dB | 6.0 ±0.2 |
| 1000 Hz −12 dB | −12.000 dB | −12.0 ±0.2 |
| 8000 Hz +12 dB | 12.000 dB | 12.0 ±0.2 |
| 100 Hz, 8000 Hz polosa | 0.001 dB | 0.0 ±0.2 |
| past kesish 120 Hz, 30 Hz ohang | −24.100 dB | −24.10 ±0.5 |
| past kesish 120 Hz, 1 kHz ohang | −0.001 dB | 0.0 ±0.5 |

Oltisi ham **0.001 dB** aniqlikda mos keldi. Chetlanish yo'q, ya'ni
koeffitsientlar ham, filtr turi ham (qo'ng'iroq va yuqori o'tkazgich)
spetsifikatsiyaga to'g'ri keladi.

**B. Boshqa kod bazasi bilan qiyoslash** — xuddi shu sozlama ffmpeg'ning
o'z `equalizer` filtri bilan ham qo'llanadi (bir xil RBJ formulasi, boshqa
amalga oshirish) va ikki natija **namuna-ba-namuna** solishtiriladi.
RMS taqqoslash yetarli emas: u fazadagi xatoni ko'rmaydi, ya'ni noto'g'ri
kaskad ham «o'xshash» spektr berishi mumkin. Namuna darajasidagi farq esa
butun yo'lni tekshiradi — kanal holati to'g'ri ajratilganmi, polosalar
tartibi to'g'rimi, aniqlik yetarlimi.

| holat | cho'qqi farqi | RMS farqi |
|---|---|---|
| bitta polosa (1000 Hz +6 dB) | 0.0031% | 0.0071% |
| to'qqiz polosali «ovoz» profili | 0.0031% | 0.0076% |

Farq bir necha namuna qadamidan iborat — ikkala tomon ham chiqishni 16 bitga
yozadi, ya'ni har bir namunada yaxlitlash bor. Bu filtr mantiqidagi xato
emas, yaxlitlash darajasi. Chegaralar shundan kelib chiqib qo'yilgan
(0.5% va 0.1%) — filtr mantiqidagi xato ulardan yuz marta katta farq beradi.

**Yo'l-yo'lakay topilgani:**

23. **Ko'rsatkich yarim yo'lda qotib qolardi.** Kesish himoyasi ishga
    tushmagan yo'lda (eng ko'p uchraydigan holat) progress faqat 0.5 gacha
    borardi: birinchi o'tish butun ish bo'lsa-da, kod uni «yarmi» deb
    hisoblardi. Ekranda tugallangan ish «yarim yo'lda» ko'rinardi. Test
    yozilganda topildi — `Equalizer.apply` endi o'sha shoxda 1.0 beradi.
24. **`bin/run-tests.sh` yashil natija bilan yangi testlarni o'tkazib
    yuborardi.** Fayl ro'yxatlari qo'lda yuritilgani uchun beshta yangi test
    fayli va beshta yangi manba fayli ro'yxatga tushmagan edi: skript
    «OK (119 tests)» deb ko'rsatib, ularni umuman ishga tushirmagan edi.
    Endi ro'yxatga tushmagan `*Test.kt` — qattiq xato (`exit 2`), sinf
    nomlari esa yo'llardan hosil qilinadi (ikkinchi ro'yxat eskirishi
    mumkin emas). Tekshiruv ataylab vaqtinchalik test fayli bilan
    sinab ko'rildi: skript kutilganidek to'xtadi.

**Qamrov.** Bu tekshiruv filtrlash **dvigatelini** o'lchaydi: biquad
koeffitsientlari, kaskad tartibi, kanal holati, aniqlik, kesish himoyasi.
Tayyor profil jadvalidagi sonlar (`EqBands`) va kuchaytirish matni
(`GainText`, `LocalizedNumber`) sof JVM sinovlarida tekshiriladi — ular
filtrlash matematikasi emas, jadval va matn mantiqi.

**Nima qoladi.** Ekranning o'zi — TalkBack bilan qo'lda sinaladi (pastda).

### Oltinchi tekshiruv — tezlik va ohang (2026-09-17)

Bu yerda namuna-ba-namuna qiyoslash **mumkin emas**: tezlikni o'zgartirish
algoritmi bir xil kirishga bir xil chiqishni bermaydi — WSOLA ham, ffmpeg'ning
`atempo` si ham yangi tovush to'qiydi, faqat natijaning xossalari bir xil
bo'lishi shart. Shuning uchun o'lchanadigan ikki xossa olinadi:

- **uzunlik** = kirish uzunligi / tezlik (ohang unga tegmaydi);
- **asosiy chastota** = kirish chastotasi × 2^(yarim ton / 12).

Ikkalasi ham spetsifikatsiyadan olinadi, ilovadan emas.

**A. Analitik tekshiruv** — `bin/verify-speed.sh`, 7 holat (tezlik 2, 0.5, 1.5,
ohang ±12, +7 yarim ton, ikkalasi birga). Har biri formula bo'yicha
tekshiriladi. Chastota **1 %**, uzunlik **0.5 %** aniqlikda mos keldi.
Chastotani ffmpeg emas, `bin/verify-speed-measure.py` o'lchadi: u RIFF
sarlavhasini o'zi o'qib, nol kesishmalarini hisoblaydi — ya'ni o'lchov
ilovaning kodi orqali emas, mustaqil amalga oshirish orqali bajariladi.

| holat | uzunlik | chastota |
|---|---|---|
| tezlik 2.0 | 1.000000 s | 440.000 Hz |
| tezlik 0.5 | 4.000000 s | 440.000 Hz |
| tezlik 1.5 | 1.333333 s | 440.000 Hz |
| ohang +12 | 2.000000 s | 880.000 Hz |
| ohang −12 | 2.000000 s | 220.000 Hz |
| ohang +7 | 2.000000 s | 659.255 Hz |
| tezlik 2.0 + ohang +12 | 1.000000 s | 880.000 Hz |

**B. Boshqa kod bazasi bilan qiyoslash** — xuddi shu sozlama ffmpeg'ning
`atempo` / `asetrate + aresample + atempo` zanjiri bilan ham qo'llanadi va
ikki natijaning uzunligi hamda chastotasi solishtiriladi (6 holat).

**Yo'l-yo'lakay topilgani:** ffmpeg `atempo` oxirgi tugallanmagan tahlil
oynasini tashlab ketadi — chiqishi ~30 ms qisqa. Bu bizning xato emas:
bizning chiqish kadr aniqligida, ya'ni uzunlik matematik jihatdan to'g'ri.
Shuning uchun B qismida uzunlik chegarasi 1.5 % qilib qo'yildi va sabab
skript ichida yozib qo'yildi; chastota chegarasi 1 % ligicha qoldi
(u yerda ikkala tomon ham 0.01 % aniqlikda mos keldi).

**Nima tekshirilmaydi.** Tovushning silliqligi — bo'laklar birikkan joyda
shitirlash bor-yo'qligi — bu quloq bilan baholanadigan narsa, raqam emas.
Uni faqat egasi qurilmada eshitib aytadi.

### Yettinchi tekshiruv — shovqin tozalash (2026-09-17)

Bu yerda ham namuna-ba-namuna qiyoslash mumkin emas: spektral ayirish har bir
chastota polosasining kuchini alohida o'zgartiradi, ya'ni chiqish to'lqin shakli
bilan emas, **o'lchanadigan xossalari** bilan baholanadi. Uchta bir-biridan
mustaqil o'lchov olinadi.

**A. Nazariy modelga qiyoslash.** Algoritm shunday ta'riflanganki, kutilgan
natijani qog'ozda hisoblash mumkin: shovqin polosasida `u = P/N` eksponensial
taqsimlangan, daromad esa Berouti qoidasi bo'yicha
`g = min(1, √(max(u − α, β) / u))`. Bundan kutilgan quvvat pasayishi
`E[g²] = ∫₀^∞ min(1, max(u−α, β)/u) · e^(−u) du` — integral `awk` da Riman
yig'indisi bilan hisoblanadi (qadam 0.0005) va Monte-Karlo hisobi bilan
solishtirilib tekshirilgan: ikkisi ham **0.14211** berdi.

| holat | kutilgan | o'lchangan |
|---|---|---|
| standart (α=2.5, β=−15 dB) | 8.47 dB | 10.65 dB |
| kuchli (α=4.0, β=−30 dB) | 19.78 dB | 22.50 dB |
| yumshoq (α=1.5, β=−10 dB) | 4.67 dB | 5.67 dB |

Farq 1–3 dB va u **modelning ideallashtirilganidan** kelib chiqadi, ilova
xatosidan emas: model har bir kadr daromadini mustaqil deb hisoblaydi, aslida
esa to'rtta qo'shni kadr Hanning² og'irliklari bilan qo'shiladi
(a = [0, 1/6, 2/3, 1/6], Σa² = 0.5). Shuning uchun chegara 4 dB qilib qo'yildi
va sabab skript ichida yozib qo'yildi. Model **global** darajani beradi, aniq
nuqtaviy bashorat emas — bu tekshirib ko'rildi: bashorat qilingan o'rtacha
daromad 0.3086, chiqqan fayldan o'lchangan periodogramma esa 0.4888.

**B. Boshqa kod bazasi bilan qiyoslash** — ffmpeg'ning `afftdn` filtri
(mustaqil amalga oshirish), sozlama `afftdn=nr=12:nf=-32:rf=-80:nt=w`.
`nf` (shovqin poli) ataylab haqiqiy polga moslandi: standart −50 dBFS berilsa
filtr shovqinni signal deb hisoblab **hech narsa qilmaydi** (0.05 dB), ya'ni
qiyoslash bo'sh bo'lardi.

| | shovqin pasayishi | 1 kHz ohang o'zgarishi |
|---|---|---|
| ilova | 10.65 dB | 0.0019 dB |
| ffmpeg `afftdn` | 8.71 dB | 0.0000 dB |

Farq 1.95 dB: ikkala tomon ham shovqinni bostiradi va ohangni saqlab qoladi.
Chegara 8 dB — usullar bir xil emas, faqat natija bir darajadaligi
tekshiriladi.

**C. Silliqlash.** Kadrlararo silliqlash (0 → 0.5) natija miqdorini
o'zgartirmasligi kerak: 10.79 dB va 10.65 dB, farq **0.14 dB**. Silliqlash
faqat «musiqiy shovqin»ni kamaytiradi va modelda hisobga olinmaydi — shuning
uchun A qismida silliqlash 0 qilib olinadi.

**O'lchovni kim bajaradi.** Ilovaning o'zi `noiseDropDb` ni qaytaradi, lekin
unga ishonilmaydi: `bin/verify-noise-measure.py` — ilova kodidan mustaqil,
RIFF sarlavhasini o'zi o'qib RMS va ohang amplitudasini o'lchaydi. Uch holatda
ham ilova aytgan raqam mustaqil o'lchovdan **0.1 dB** ichida chiqdi: ya'ni
«tozaladim» deb yolg'on aytish darhol ko'rinadi.

**Halol izoh: bu neyron tarmoq emas.** Tavsifda «AI shovqin tozalash»
deyilgan. Amalda bu — **statistik spektral ayirish**: foydalanuvchi ko'rsatgan
shovqin namunasidan polosa profili olinadi va Berouti qoidasi bilan ayiriladi.
Usul klassik, tushunarli va telefonda tez ishlaydi, lekin u «o'rgangan» model
emas: nutqni shovqindan ajratib olmaydi, faqat shovqin poli ma'lum bo'lgan
holatda yaxshi ishlaydi (masalan, bir xil fon shovqini ostidagi yozuv).
Neyron denoiser (RNNoise/Demucs sinfidagi model, ONNX/TFLite orqali qurilmada)
alohida band sifatida yo'l xaritasiga qo'shildi — u o'z tekshiruvi bilan
keladi.

**Nima tekshirilmaydi.** Tozalashdan keyin nutq qanchalik tabiiy eshitilishi —
bu quloq bilan baholanadigan narsa, raqam emas.

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
- Format konvertori: import, formatni saqlash, 8 ta maqsad format.
- Ekvalayzer: 10/31 polosa, 6 tayyor profil, past chastota kesish,
  kesish himoyasi.
- Tezlik va ohang: 0.5x–2x, ohang ±12 yarim ton, ikkalasi birga; natijadagi
  uzunlik darhol ko'rsatiladi.
- Shovqin tozalash: shovqin namunasi bo'yicha spektral ayirish, kuch va qoldiq
  qo'lda kiritiladi, natija darhol kutubxonaga tushadi.

**Keyingi navbat (shu tartibda)**

1. ~~**Format konvertori**~~ — **tayyor**. Fon qatlami (`media/format/`)
   to'liq: aniqlash, imkoniyatlar jadvali, formatni saqlash, WAV/FLAC/MP3
   kodlovchilari (MP3 ffmpeg bilan tekshirilgan: `bin/verify-mp3.sh`),
   import dekoderi va tizim kodlovchilari. Ekran ham tayyor
   (`ui/convert/`). WMA hech qachon ishlamaydi — Android'da dekoderi yo'q,
   buni import paytida ochiq aytamiz. OGG/Vorbis ham yozilmaydi (kodlovchi
   yo'q) — bunday fayl import qilinsa, o'rniga FLAC taklif qilinadi.
   **Qolgani (faqat qurilmada tekshiriladi):** M4A/AAC/Opus kodlovchilari
   `MediaCodec` orqali ishlaydi-yu, JVM'da sinalmaydi — ularni telefonda
   ochib ko'rish kerak.
2. ~~**Parametrik ekvalayzer**~~ — **tayyor**. 10 va 31 polosa, biquad
   filtrlar, ikki aniqlikdagi hisob, 6 tayyor profil, past chastota kesish,
   kesish himoyasi; ekran o'quvchi uchun har bir polosa raqamli maydonda.
   Mustaqil tekshiruv ffmpeg bilan o'tdi (beshinchi tekshiruv, yuqorida).
   **Qolgani (faqat qurilmada tekshiriladi):** ekranning o'zi — TalkBack
   bilan har bir polosani kiritib, natijani eshitish.
3. ~~**Tezlik va ohang**~~ — **tayyor**. WSOLA (ohangni saqlab tezlashtirish)
   va Kayzer sarlavhali ko'p fazali interpolator (ohangni surish), 0.5x–2x va
   ±12 yarim ton, ikkalasi birga ham. Mustaqil tekshiruv ffmpeg bilan o'tdi
   (oltinchi tekshiruv, yuqorida). **Qolgani (faqat qurilmada tekshiriladi):**
   ekranning o'zi va tovush silliqligi — pastda.
4. ~~**Shovqin tozalash**~~ — **tayyor**. Spektral ayirish (FFT + Berouti
   qoidasi), shovqin namunasi foydalanuvchi tomonidan belgilanadi, kuch va
   qoldiq chegarasi qo'lda kiritiladi. Mustaqil tekshiruv ffmpeg'ning
   `afftdn` filtri bilan o'tdi (yettinchi tekshiruv, yuqorida).
   **Qolgani (faqat qurilmada tekshiriladi):** ekranning o'zi — TalkBack bilan
   namuna oraliqlarini kiritib, natijani eshitish.
   **Aytilmagan, lekin muhim:** bu **statistik usul, neyron tarmoq emas**.
   Tavsifdagi «AI shovqin tozalash» shu bilan chegaralanadi — quyida 11-band.
5. ~~**Ovoz dvigateli abstraksiyasi**~~ — **tayyor** (`VoiceEngine` +
   `DeviceTtsEngine`, `media/voice/`). Hozircha faqat qurilma TTS'i ulangan;
   bulut AI ovozi keyin shu interfeys ortiga ulanadi — ekranlar o'zgarmaydi.
   Uzun matn jumla chegarasida bo'laklarga bo'linadi, til matnning yozuvidan
   (lotin/kirill) tanlanadi. Mustaqil tekshiruv yo'q va **bo'lishi ham mumkin
   emas**: `TextChunker` va `ScriptDetector` 33 ta JVM testi bilan qoplangan,
   lekin ovozning o'zi — sintezatorning talaffuzi — faqat quloq bilan
   baholanadi. **Qolgani (faqat qurilmada tekshiriladi):** pastda.
6. ~~**Hujjat → audio-kitob**~~ — **tayyor** (5-bandga tayanadi).
   PDF/DOCX/EPUB/TXT o'qiladi, matn boblarga bo'linadi, har bob alohida MP3
   bo'ladi, belgilar varaqasi (CUE) yoziladi, `ui/book/` ekrani jarayonni
   ko'rsatadi va to'xtatish mumkin. `SleepTimer` (uxlash taymeri) media
   qatlamida tayyor va testlangan.
   **Hozircha yo'q:** taymer va belgilar varaqasi ilovaning pleyerida hali
   ishlatilmaydi — buning uchun kitobni boblar bo'ylab o'qiydigan pleyer
   kerak; u alohida band bo'lib turadi (hozir kitobni istalgan tashqi
   pleyerda tinglash mumkin, tartib fayl nomida saqlanadi).
   **Qolgani (faqat qurilmada tekshiriladi):** pastda.
7. **ID3 teglar va ulashish** — nom, ijrochi, albom, muqova; faylni boshqa
   ilovaga yuborish.
8. **Ko'p yo'lli aralashtirish** — har bir yo'lga ovoz balandligi, panorama,
   ducking.
9. **Sozlamalar ekrani** — til tanlash, soddalashtirilgan rejim, ilova haqida.
10. **Vokal/cholg'u ajratish** — qurilmada ishlaydigan model (ONNX/TFLite);
    eng og'ir band, shuning uchun oxirida.
11. **Neyron shovqin tozalash** — 4-band statistik usul bilan bajarildi, ya'ni
    shovqin namunasi kerak va nutq shovqindan *ajratilmaydi*, faqat pol
    ayiriladi. Neyron model (RNNoise yoki shunga o'xshash, ONNX/TFLite orqali
    qurilmada ishlaydigan) namunani talab qilmaydi va nutqni shovqindan
    ajratadi. 10-band bilan bir xil infratuzilmani (model yuklash, NPU/CPU
    tanlash) ishlatadi — shuning uchun ikkalasi birga qilinadi.
    **Halol cheklov:** model fayli ilovaga qo'shiladi va u bir necha MB bo'ladi;
    litsenziyasi GPL-3.0 bilan mos bo'lishi shart.

**Faqat egasi bajaradi**

- APK'ni qurilmada, TalkBack yoqilgan holda qo'lda sinash: yozish, kesish,
  bo'lish, ekran o'chganda yozuv davom etishi.
- Konvertorni qurilmada sinash: telefondagi MP3 ni import qilib M4A/Opus ga
  o'girish, WAV ni MP3 ga o'girish. Bu yo'l `MediaCodec` ga tayanadi, ya'ni
  uni faqat qurilma ko'rsata oladi.
- Ekvalayzer ekranini TalkBack bilan sinash: polosa maydonlari qanday
  o'qiladi, klaviatura bilan kiritish qulaymi, natija eshitiladimi.
- Tezlik va ohang ekranini sinash: 2x tezlikda tovush silliqmi (bo'laklar
  birikkan joyda shitirlash yo'qmi), ohang surilganda tabiiy eshitiladimi,
  0.5x da uzunlik to'g'ri chiqadimi. Bu — quloq bilan baholanadigan narsa,
  skript uni o'lchay olmaydi.
- Ovoz ekranini sinash: qurilmada o'zbek ovozi o'rnatilganmi (qurilma
  Sozlamalarida «Til va kiritish» → «Matnni ovozga aylantirish»), ekran buni
  to'g'ri ko'rsatyaptimi,
  uzun matn bo'laklarga bo'linib, tanaffussiz o'qilyaptimi, «to'xtat» darhol
  ishlayaptimi. O'zbek ovozi bo'lmasa — Sozlamalardan o'zbek ovozini o'rnatish
  kerak; ilova o'zi ovoz o'rnatib bera olmaydi, bu Android'ning ishi.
- Shovqin tozalash ekranini sinash: namuna oraliqlari vaqt maydonlarida
  kiritiladimi, kuch/qoldiq maydonlari o'qiladimi, tozalangandan keyin nutq
  tabiiy eshitiladimi va shovqin haqiqatan kamayganmi. Skript faqat
  shovqinning **kamayganini** o'lchaydi; nutqning **yaxshi eshitilishini**
  faqat quloq aytadi.
- Audio-kitob ekranini sinash: qurilmadan PDF/DOCX/EPUB/TXT tanlab, boblar
  to'g'ri bo'linganini ko'rish, o'zbek kitobini o'zbek ovozi o'qiyaptimi,
  yasalgan MP3 boblar pleyerda to'g'ri tartibda chalyaptimi, tezlik 2x da
  shitirlash yo'qmi, uzoq kitobda (bir necha soat) jarayon foizi
  yangilanib turyaptimi va «to'xtatish» darhol ishlayaptimi. Bu — quloq
  va qurilma ishi; skript faqat **fayl mazmunini** (MP3 kadrlari, CUE
  yozuvlari, bob nomlari) tekshira oladi.

**Ma'lum cheklovlar (keyingi ishlar)**

- Import qilingan manba nusxalari `manba/` papkasida saqlanadi va o'chirish
  tugmasi ularga tegmaydi (u faqat asosiy ro'yxatdagi fayllarni o'chiradi).
  Fayllar ko'payib ketsa, ular uchun tozalash kerak bo'ladi.
- Ilova ichidagi faylni boshqa ilovaga yuborish (ulashish) hali yo'q —
  konvertor natijani faqat ilovaning o'z papkasiga yozadi. Bu 7-band
  (ID3 va ulashish) bilan birga keladi.

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
