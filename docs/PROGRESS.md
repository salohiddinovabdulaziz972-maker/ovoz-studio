# Holat va reja

Oxirgi yangilanish: 2026-09-17

## Bajarildi

1. **Loyiha skeleti** — Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3,
   minSdk 24, targetSdk 35. Gradle version catalog, manifest, launcher ikonkalari
   (barcha zichliklar uchun generatsiya qilingan), 4 til: uz (lotin),
   uz-Cyrl, ru, en — 157 ta satr, hammasi to'liq tarjima qilingan.
2. **Accessibility qatlami** — `ui/common/A11y.kt` va `ChoiceRow.kt`:
   yorliqsiz tugma bo'lishi mumkin emas (yorliq majburiy parametr), minimal
   tegish maydoni 48 dp, radio guruhlar `selectableGroup()` bilan, kalitlar
   `toggleable` qator sifatida (TalkBack butun qatorni o'qiydi).
3. **Yozib olish yadrosi** — `media/AudioRecorderEngine.kt`, `WavWriter.kt`,
   `WavFile.kt`, `RecorderConfig.kt`.
4. **Kesish yadrosi** — `media/AudioTrimmer.kt`, `media/AudioPlayer.kt`,
   `media/RecordingStore.kt`.
5. **Ekranlar** — bosh, yozib olish, kesish (+ uch ViewModel).
6. **Testlar** — 171 ta sof JVM testi (`app/src/test/…`), hammasi o'tadi.
   Yurgizish: `bash bin/run-tests.sh` (Android SDK kerak emas).
   CI'da ham ishlaydi: `.github/workflows/android.yml` → `testDebugUnitTest`.
   Fayl ro'yxati skriptda qo'lda yuritiladi (hamma manba fayl oddiy
   `kotlinc` bilan yig'ilavermaydi — Android'ga bog'liqlari bor), lekin
   ro'yxatga tushmay qolgan test endi **jimgina o'tib ketmaydi**: skript
   har bir `*Test.kt` ni ro'yxatda qidiradi va topmasa `exit 2` beradi.
7. **Android qatlamining kompilyatsiyasi** — `bin/typecheck-android.sh`:
   android.jar + AndroidX/Compose + Compose kompilyator plagini bilan barcha
   48 manba fayl kompilyatsiya qilinadi. Ilgari ekranlar va ViewModel'lar
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
- Konvertorni qurilmada sinash: telefondagi MP3 ni import qilib M4A/Opus ga
  o'girish, WAV ni MP3 ga o'girish. Bu yo'l `MediaCodec` ga tayanadi, ya'ni
  uni faqat qurilma ko'rsata oladi.
- Ekvalayzer ekranini TalkBack bilan sinash: polosa maydonlari qanday
  o'qiladi, klaviatura bilan kiritish qulaymi, natija eshitiladimi.

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
