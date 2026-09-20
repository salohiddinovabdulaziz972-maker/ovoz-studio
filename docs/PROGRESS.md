# Holat va reja

Oxirgi yangilanish: 2026-09-19

## Bajarildi

1. **Loyiha skeleti** — Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3,
   minSdk 24, targetSdk 35. Gradle version catalog, manifest, launcher ikonkalari
   (barcha zichliklar uchun generatsiya qilingan), 4 til: uz (lotin),
   uz-Cyrl, ru, en — 342 ta satr, hammasi to'liq tarjima qilingan.
2. **Accessibility qatlami** — `ui/common/A11y.kt` va `ChoiceRow.kt`:
   yorliqsiz tugma bo'lishi mumkin emas (yorliq majburiy parametr), minimal
   tegish maydoni 48 dp, radio guruhlar `selectableGroup()` bilan, kalitlar
   `toggleable` qator sifatida (TalkBack butun qatorni o'qiydi).
3. **Yozib olish yadrosi** — `media/AudioRecorderEngine.kt`, `WavWriter.kt`,
   `WavFile.kt`, `RecorderConfig.kt`.
4. **Kesish yadrosi** — `media/AudioTrimmer.kt`, `media/AudioPlayer.kt`,
   `media/RecordingStore.kt`.
5. **Ekranlar** — bosh, yozib olish, kesish (+ uch ViewModel).
6. **Testlar** — 593 ta sof JVM testi (`app/src/test/…`), hammasi o'tadi.
   Yurgizish: `bash bin/run-tests.sh` (Android SDK kerak emas).
   CI'da ham ishlaydi: `.github/workflows/android.yml` → `testDebugUnitTest`.
   Fayl ro'yxati skriptda qo'lda yuritiladi (hamma manba fayl oddiy
   `kotlinc` bilan yig'ilavermaydi — Android'ga bog'liqlari bor), lekin
   ro'yxatga tushmay qolgan test endi **jimgina o'tib ketmaydi**: skript
   har bir `*Test.kt` ni ro'yxatda qidiradi va topmasa `exit 2` beradi.
7. **Android qatlamining kompilyatsiyasi** — `bin/typecheck-android.sh`:
   android.jar + AndroidX/Compose + Compose kompilyator plagini bilan barcha
   120 manba fayl kompilyatsiya qilinadi. Ilgari ekranlar va ViewModel'lar
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

18. **Kitob pleyeri va uxlash taymeri** — `media/book/BookPlaylist.kt`,
    `BookPlaybackStore.kt`, `ui/book/` (pleyer bo'limi), `AudioPlayer.seekTo`.
    Yasalgan kitob ilovaning o'zida tinglanadi: boblar ketma-ket o'tadi,
    bob tugaganda keyingisi o'zi boshlanadi, oxirgi bobdan keyin pleyer
    to'xtaydi va kitob boshiga qaytadi. Belgilar (bo'lak sarlavhalari)
    bo'ylab oldinga/orqaga sakrash, 15 soniya orqaga/oldinga, boblar
    bo'ylab o'tish — hammasi matnli yorliqli tugmalar bilan; slayder yo'q.
    **Qoldirilgan joy** kitob nomi bilan `filesDir` dagi `Properties`
    faylida saqlanadi (30 soniyada bir marta, pauzada va ekran yopilganda) —
    kitob qayta yasalsa, tugma «Davom etish» bo'lib turadi.
    **Uxlash taymeri** 15/30/60 daqiqa: vaqt faqat o'qish paytida sanaydi
    (tanaffusda to'xtaydi), «bob oxirigacha» rejimida esa gap o'rtasida
    uzilmaydi. Taymerning o'zi (`SleepTimer`) allaqachon yozilgan va
    testlangan edi — endi ekranga ulangan.
    Butun pleyer mantig'i **tovushsiz qatlamda**: `BookPlaylist` (qaysi bob
    keyin, belgi bo'ylab sakrash, kitob bo'ylab vaqt hisobi) va
    `BookPlaybackStore` (buzuq fayl, yo'q papka, g'alati nom — hech biri
    tinglashga xalaqit bermaydi) 21 ta JVM testi bilan qoplangan.
    `AudioPlayer`/`MediaPlayer` bilan bog'liq qism ViewModel'da qoladi.
    **Halol cheklov:** pleyer faqat shu seansda yasalgan kitobni tinglaydi.
    Ilova qayta ochilsa, fayllar joyida turadi, lekin ro'yxatni tiklash
    uchun kitobni qayta yasash kerak. Bu — keyingi ish (papkadan o'qish).

19. **ID3 teglar va ulashish** — `media/tag/` (`Id3v2Reader`, `Mp3Tagger`,
    `TagDraft`) + `ui/tag/` + `util/Sharing.kt`. MP3 faylning nomi, ijrochisi,
    albomi, yili, janri, tartib raqami («3/12» ko'rinishida ham) va muqova
    rasmi tahrirlanadi. Manba fayl **o'zgarmaydi**: teg yangi faylga yoziladi
    (kesish va konvertatsiyadagi bilan bir xil naqsh — telefonda «bekor qilish»
    yo'q, asl nusxa joyida qolishi kerak). Fayl boshqa ilovaga
    `FileProvider` orqali `content://` havola bilan, faqat o'qish uchun va bir
    marta beriladi.
    **Nega o'quvchi ham yozildi:** yozuvchi tegni **butunlay almashtiradi**
    (bo'sh maydon — yo'q maydon). Ekran fayldagi mavjud tegni o'qimasa,
    foydalanuvchi faqat muqova qo'shmoqchi bo'lib, nom va ijrochini jimgina
    o'chirib qo'yardi. Shuning uchun `Id3v2Reader` yozildi va u alohida
    tekshiriladi.
    **Mustaqil tekshiruv** (`bin/verify-tag.sh`): ilova yozgan tegni **ffprobe**
    o'qiydi (tashqi o'quvchi), so'ng o'sha fayl ilovaning o'z o'quvchisi bilan
    qayta o'qiladi va maydonlar solishtiriladi. To'rt holat: ffmpeg yozgan
    eski teg (o'qish uni ko'radi, boshqa maydonlar bo'sh qoladi), to'liq teg
    (oltita maydon + muqova bayt-bayt), kirill teg, va audio qismi
    o'zgarmaganini ffprobe tasdiqlashi.
    27 ta JVM testi: teg matni ramkalari, UTF-16 (BOM ikki tartibda), UTF-8,
    ISO-8859-1, v2.4 ning sinxsaflangan ramka o'lchami va ma'lumot uzunligi
    belgisi, to'ldirish (padding), buzilgan teg (ilova yiqilmasligi shart),
    siqilgan ramka, muqovaning o'z baytlari, `read(File)` da muqovadan keyingi
    audio teg ichiga sizmasligi.
    **Halol cheklovlar:** ID3 faqat MP3 da (WAV/M4A/FLAC boshqa standart —
    ekran buni aytadi va konvertorga havola beradi); ID3v2.2 teglari
    o'qilmaydi (uch belgili ramka nomlari, boshqa tuzilma) — bunday fayl
    ochilsa maydonlar bo'sh chiqadi.
    **Ilova papkasidagi fayllar ro'yxati** ham shu ekranga qo'shildi
    (`RecordingStore.listMp3()`): tizim tanlagichi `Android/data/…` ni
    ko'rmaydi, ya'ni konvertor va audio-kitob yasagan MP3 ni foydalanuvchi
    boshqa yo'l bilan topa olmasdi.

20. **Ko'p yo'lli aralashtirish** — `media/mix/` (`AudioMixer`, `MixTrack`,
    `MixSource`, `MixEditor`, `MixProject`, `MixProjectStore`) + `ui/mix/`
    ekrani. Bir necha yozuv bitta faylga qo'shiladi; har bir yo'lga
    balandlik (−60…+12 dB), chap/o'ng joylashuv (−1…+1) va siljish
    (0…600 s) beriladi, yo'lni o'chirish (mute) va faqat bittasini eshitish
    (solo) bor, umumiy balandlik esa alohida.
    **Barcha sozlamalar qo'lda kiritiladi** (ilova bo'ylab yagona qoida):
    sirg'anma bilan aniq desibelni qo'yib bo'lmaydi — ekran o'quvchi uchun ham,
    barmoq uchun ham. Matn va son orasidagi qoidalar alohida faylda
    (`MixTrackText`), shuning uchun ular JVM'da tekshiriladi: «−60» maydonga
    sig'adimi, chegaradan oshib ketmaydimi, matn va son orasidagi aylanish
    **aynan**mi (sozlama saqlanib, qayta ochilganda o'sha ovoz eshitilishi
    kerak).
    **Natija har doim stereo**: panorama faqat ikki kanalda ma'noga ega.
    Mono yo'l o'rtada turganda har bir kanalga **−3 dB** bilan qo'yiladi
    (quvvat saqlanadi), chetga surilganda 0 dB — ya'ni «o'rtaga qo'ydim,
    ovoz balandroq bo'lib ketdi» holati yo'q.
    **Kesish himoyasi**: yo'llar yig'indisi chegaradan oshsa, har bir namuna
    alohida qisilmaydi (bu buzilish ovozi berardi) — butun fayl oldindan
    o'lchangan cho'qqi bo'yicha bitta koeffitsientga tushiriladi. Buning
    uchun mikser fayllarni **ikki marta** o'qiydi: birinchisi cho'qqini
    o'lchaydi, ikkinchisi yozadi. Ekran tushirish miqdorini aytadi, lekin
    faqat sezilarli bo'lsa: to'liq shkaladagi bitta yo'l ham 0.999 ga
    tushadi (0.01 dB) — buni «0 dB tushirildi» deb aytish ma'nosiz bo'lardi.
    **Loyiha avtomatik saqlanadi** (`filesDir`, har o'zgarishdan keyin,
    `Mutex` bilan — sekin yozuv keyingisini bosib ketmasligi uchun), ya'ni
    ekrandan chiqib qaytilganda yo'llar joyida turadi. Saqlanadigan narsa —
    manba faylning **nomi**, to'liq yo'li emas: ilovaning papkasi
    yangilanishdan keyin o'zgarishi mumkin, nom esa qoladi. Fayl topilmasa,
    yo'l jimgina tushib qolmaydi — ochiq xato beriladi va aralashtirish
    to'xtatiladi. «Orqaga qaytarish» oxirgi 30 qadamni tiklaydi.
    **Nega WAV:** mikser har bir faylni ikki marta o'qiydi, siqilgan
    formatda bu mumkin emas (ochish bir marta oqim bo'lib keladi). Shuning
    uchun ekranda konvertorga o'tish tugmasi bor — boshqa dastur qidirish
    shart emas. Chastotalar har xil bo'lsa, jimgina qayta namunalash
    **qilinmaydi**: ochiq xato beriladi (aks holda foydalanuvchi eshitgan
    natija bilan kutgani mos kelmasdi).
    **Mustaqil tekshiruv:** `bin/verify-mix.sh` (sakkizinchi tekshiruv,
    pastda).
21. **Sozlamalar ekrani** — `settings/` (til mantig'i, sozlama fayli) va
    `ui/settings/` (ekran). Til: «tizim tili bilan bir xil», o'zbekcha
    (lotin), o'zbekcha (kirill), ruscha, inglizcha.
    **Qurilma tilidan aniqlash** (`LanguageMatch`) — teglar ro'yxati afzallik
    bo'yicha o'qiladi va **birinchi mos kelgani** olinadi; kirill yozuvi
    `uz-Cyrl` tegining ichidan ajratiladi (`uz-UZ` — lotin, `uz-Cyrl-UZ` —
    kirill), notanish til o'zbekchaga tushadi. Pastki chiziqli teg
    (`uz_Cyrl_UZ`) ham o'qiladi: Android teglarni har xil shaklda beradi.
    **Ekranda amalda ishlayotgan til yoziladi** — «tizim tili» qaysi tilga
    olib kelishini foydalanuvchi oldindan ko'radi, taxmin qilmaydi.
    **Til almashtirish appcompat'siz** bajariladi: loyihada `appcompat`
    yo'q, ya'ni `AppCompatDelegate.setApplicationLocales` mavjud emas —
    shuning uchun `attachBaseContext` da `createConfigurationContext` bilan
    yangi `Context` yasaladi va Activity `recreate()` qilinadi. Tizim tili
    tanlanganda `Locale.setDefault` **qurilma tiliga qaytariladi**: aks
    holda oldin tanlangan til raqam va sana formatlashda qolib ketardi.
    Qurilma tili `Resources.getSystem()` dan o'qiladi, `Locale.getDefault()`
    dan emas — ikkinchisini o'zimiz o'zgartirgan bo'lamiz.
    **Yozuv sinxron** (`AppSettingsStore.save`): Activity qayta ochilganda
    faylni `attachBaseContext` da **darhol** o'qiydi, ya'ni fondagi yozuv
    bilan poyga chiqardi va foydalanuvchi tanlagan til «o'z-o'zidan qaytib
    ketgandek» ko'rinardi. Fayl ~100 bayt, yozuv bir millisekunddan qisqa.
    Shuning uchun `setLanguage` mantiqiy qiymat qaytaradi va ekran faqat
    yozuv **haqiqatan** o'tganda qayta ochiladi; o'tmasa — ochiq xato
    ko'rsatiladi, jimgina eski tilga qaytish bo'lmaydi.
    **Buzuq sozlama fayli — xato emas, zaxira qiymat**: yo'q fayl, bo'sh
    fayl, buzuq fayl va notanish til tegi — hammasi standart holatga
    tushadi. Sozlama fayli uchun xato oynasi ko'rsatish noto'g'ri bo'lardi:
    foydalanuvchi ilovani ochib ishlatishi kerak, faylni esa o'zi tuzata
    olmaydi.
    **Soddalashtirilgan rejim** ikkinchi darajali tugmalarni yashiradi (bosh
    ekranda 7 ta asbob tugmasi «Boshqa imkoniyatlar» ortiga, har bir yozuv
    qatorida 4 ta ikonka), lekin **funksiyani yo'qotmaydi**: har bir amal
    bosh ekranda ham bor, rejim yoniqligi ekranda yozib qo'yiladi va
    ochish tugmasi ko'rinib turadi. Maqsad — ekran o'quvchi bilan har bir
    yozuvdagi to'xtash nuqtalari sonini kamaytirish, imkoniyatni emas.
    **Ilova haqida**: versiya (paketdan, `runCatching` bilan — `BuildConfig`
    AGP 8 da o'chiq), litsenziya nomi va manba kod havolasi. Havola
    ochilmasa (brauzer yo'q) — ochiq xato.
    **Mustaqil tekshiruv:** `bin/verify-locales.sh` (to'qqizinchi tekshiruv,
    pastda).
    **Qolgani (faqat qurilmada tekshiriladi):** til almashtirishning
    haqiqiy qurilmada ishlashi (Activity qayta ochilishi, matn kirill
    yozuviga o'tishi) va soddalashtirilgan rejimning TalkBack bilan
    yengillashishi.
22. **Vokal va cholg'uni ajratish** — `media/dsp/StemSeparator.kt` va
    `ui/stem/` ekrani. Stereo yozuv ikkita faylga bo'linadi: **vokal** va
    **cholg'u**. Usul — **kanal usuli** (neyron model emas): har bir
    kadrda (1024 namuna, 75% qoplama, Hann oynasi) chap va o'ng kanal FFT
    orqali markaz (`M = (L+R)/2`) va yon (`S = (L−R)/2`) qismlarga
    ajratiladi, keyin har bir polosa uchun markazning ustunlik darajasi
    `d = |M| / (|M| + |S|)` o'lchanadi.
    **Ikki rejim**:
    «aniq ayirish» (`REMOVE_VOCALS`) markazni `1.0` koeffitsient bilan
    oladi — vokal butunlay o'chadi, spektral teshik qolmáydi;
    «qismiy ajratish» (`SPLIT`) esa `d^kuch` koeffitsientini qo'llaydi,
    ya'ni markaz butunlay ustun bo'lgan polosa deyarli o'chadi, markaz va
    yon teng bo'lgan polosa esa yarmidan ko'pi qoladi — keng yozilgan
    cholg'u saqlanadi.
    **Tuzilish xossasi:** vokal + cholg'u = manba, **namuna-darajada**.
    Ayirish simmetrik (`vokal = M·m`, `cholg'u = L − M·m` va `R − M·m`),
    shuning uchun hech narsa yo'qolmaydi va o'lchov ham aynan shuni
    tasdiqlaydi (1 LSB farq — 16-bitda yaxlitlash).
    **Manba o'zgarmaydi:** natija ikkita yangi fayl. Ikkalasi bir joyda
    yoziladi va xato bo'lsa **ikkalasi ham** o'chiriladi — aks holda
    foydalanuvchi «ajratdim» deb bitta fayl olardi.
    **Halol cheklovlar ekranda aytiladi**, chunki bu usulning chegarasi
    jimgina o'tkazib yuborilsa, foydalanuvchi natijani «buzuq» deb
    hisoblaydi: (1) **markazda turgan cholg'u ham o'chadi** — bas yoki
    baraban ham ko'pincha markazda turadi, bu usulning emas, **kanal
    usulining** chegarasi; (2) **mono yozuvda ajratadigan narsa yo'q** —
    ilova bitta kanalli faylni ham, kanallari bir xil bo'lgan faylni ham
    ochiq rad etadi (xato matni bilan, jimgina «natija» bermaydi);
    (3) yon qism juda kuchsiz bo'lsa (o'lchangan «yon/markaz» nisbati
    −20 dB dan past) natija yonida ogohlantirish chiqadi.
    **Shu sababdan ekran o'lchangan sonni ko'rsatadi** — «yon qism
    markazga nisbatan: X dB» — ya'ni foydalanuvchi natijani baholash uchun
    taxmin qilmaydi.
    **Kuch maydoni** faqat `SPLIT` rejimida ishlaydi (0.5–4.0, standart
    1.5); `REMOVE_VOCALS` da maydon o'chiriladi va **sababi yozib
    qo'yiladi** — sababsiz o'chiq maydon ekran o'quvchi uchun tuzoq.
    **Mustaqil tekshiruv:** `bin/verify-stem.sh` (o'ninchi tekshiruv,
    pastda) va `bin/falsify-stem.py`.
    Kodni ko'rib chiqish uchun `bin/dump-source.py` — butun loyihani
    mundarijali bitta matn faylga yig'adi.

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
- **Uxlash taymeri faqat o'qish paytida sanaydi.** Taymer alohida soat emas:
  u pleyerning yangilanish tsikli ichida sanaladi. Aks holda tanaffusda ham
  vaqt o'tib ketardi va kitob foydalanuvchi kutganidan erta to'xtardi.
- **Pleyer mantig'i `BookPlaylist` da, ViewModel'da emas.** Qaysi bob keyin
  keladi, belgi bo'ylab qanday sakraladi, kitob bo'ylab vaqt qanday
  hisoblanadi — sof mantiq va shu sababli JVM'da sinovdan o'tadi. Buni
  qo'lda, 300 bobli kitobda hamisha sinab bo'lmaydi; pleyerning eng ko'p
  uchraydigan xatosi esa aynan chegarada bo'ladi (oxirgi bobdan keyin
  birinchisiga qaytib ketish).
- **Qoldirilgan joy hech qachon tinglashga xalaqit bermaydi.** Yozuv
  fayli buzuq, papka yo'q, nom g'alati — har qanday holatda pleyer ishlaydi,
  eng yomoni kitob boshidan boshlanadi. Shuning uchun saqlash qatlami
  xatoni tashqariga chiqarmaydi va sinovlarining ko'pi xato yo'llari haqida.
- **Pozitsiya qisqa formatda ko'rsatiladi** (`TimeFormat.formatShort`:
  «12:04», soat bo'lsa «1:02:03»). Millisoniyali to'liq format har chorak
  soniyada yangilanib ekranni titratardi va ekran o'quvchi har safar
  keraksiz aniqlikni o'qib berardi.
- **To'xtatilganda sakrash tugmalari o'chiriladi.** `pause()` faylni
  yopadi, ya'ni pozitsiyani surish imkonsiz — tugma bosilib, hech narsa
  qilmasligidan ko'ra, o'chirib turgani rost.
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
- **Teg yozishdan oldin mavjud teg o'qiladi.** ID3 yozuvchisi tegni
  butunlay almashtiradi: yozilmagan maydon — tegda umuman yo'q maydon.
  Ya'ni o'quvchisiz ekran ma'lumot yo'qotardi (faqat muqova qo'shmoqchi
  bo'lgan foydalanuvchi nom va ijrochini yo'qotardi). Shuning uchun
  `Id3v2Reader` yozildi; u ham ffprobe bilan mustaqil tekshiriladi.
- **Teg faylning tarkibi bo'yicha tekshiriladi, kengaytmasi bo'yicha
  emas.** `supportsId3Tags()` `AudioFormatDetector` aniqlagan konteynerni
  oladi: `.mp3` deb nomlangan WAV faylga teg yozish jimgina buzuq fayl
  yasardi.
- **Raqamli maydon uchun raqamli klaviatura emas.** Tartib raqami maydonida
  «3/12» yozilishi mumkin — raqamli klaviaturaning «/» tugmasi yo'q, ya'ni
  jami sonni kiritib bo'lmasdi. Yil maydonidagina `KeyboardType.Number`.
- **Ulashish `FileProvider` orqali.** Android 7 dan boshlab `file://` yo'l
  boshqa ilovaga berilmaydi (`FileUriExposedException`), faqat `content://`.
  Ruxsat `FLAG_GRANT_READ_URI_PERMISSION` bilan, bir marta va faqat o'qish
  uchun beriladi; provayder `exported=false`, ko'rinadigan papkalar esa
  `res/xml/file_paths.xml` da sanab o'tilgan.
- **Muqova oqimdan chegara bilan o'qiladi** (`readCapped`): `readBytes()`
  butun rasmni xotiraga ko'tarardi va 40 megapikselli surat ilovani
  yiqitardi. Chegaradan oshsa — o'qish to'xtaydi va tushunarli xabar chiqadi.

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

### Sakkizinchi tekshiruv — aralashtirish (2026-09-17)

Bu yerda namuna-ba-namuna qiyoslash **mumkin**, shuning uchun o'lchov eng qat'iy
bo'ldi: manbalarni `ffmpeg` yasaydi, aralashmani ilova yozadi, natijani esa
ilovaning kodidan mustaqil python skript (`bin/verify-mix-compare.py` — RIFF
sarlavhasini o'zi o'qiydi, kerakli chastotadagi amplitudani Goertzel usuli bilan
o'lchaydi) tekshiradi.

**A. Boshqa kod bazasi bilan namuna-ba-namuna.** ffmpeg'ning `amix` filtri
(`normalize=0`) bilan solishtiriladi: ikkala yo'l ham stereo, panorama o'rtada,
ya'ni solishtirishda hech qanday «panorama qoidasi» qatnashmaydi — faqat yig'ish
va desibel qoladi. Eng katta farq **0.00000** (chegara ±0.0001) — ya'ni
namunalar farqi o'lchov aniqligidan ham kichik. Solishtirish namuna-ba-namuna,
kanal bo'yicha; uzunlik yoki kanal soni mos kelmasa — bu alohida xato.

**B. Panorama, siljish va balandlik — analitik javob bilan.** Bu yerda
qoida qog'ozda hisoblanadi va o'shanga qiyoslanadi.

| o'lchov | kutilgan | chiqqan |
|---|---|---|
| mono yo'l o'rtada, chap kanal (440 Hz) | 0.35355 (−3 dB) | 0.35355 |
| mono yo'l o'rtada, o'ng kanal | 0.35355 | 0.35355 |
| chetga surilgan yo'l (440 Hz chapda) | 0.50000 (0 dB) | 0.50000 |
| o'sha yo'l qarama-qarshi kanalda | 0.00000 | 0.00000 |
| master −6 dB dan keyin | 0.17720 | 0.17720 |
| 200 ms siljish (o'ng kanal boshlanishi) | 9600 kadr | 9601 kadr |

Siljish kadr aniqligida (9600 kadr = 200 ms × 48 kHz), ya'ni ±3 kadr ichida.
Qarama-qarshi kanaldagi **nol** ham tekshiriladi: yo'l «hamma joyda bir oz»
eshitilib qolmasligi kerak.

**C. Kesish himoyasi.** Ikki yo'l yig'indisi 1.6 bo'lganda chiqish cho'qqisi
aynan **0.999** bo'lishi, o'lchangan cho'qqi esa 1.6 chiqishi shart — ya'ni
fayl qisilmagan, balki butunlay bitta koeffitsientga tushirilgan. Koeffitsient
ham tekshiriladi: 0.62437 (kutilgan 0.999/1.6 = 0.62438, farq 1e-05).

**Nima tekshirilmaydi.** ffmpeg'ning o'z `pan` filtri bilan qiyoslash **ataylab**
qilinmaydi: u boshqa panorama qonunini ishlatadi (mononi kanalga 0 dB bilan
qo'yadi), ya'ni farq kodning xatosidan emas, ikki boshqa qoidadan chiqardi va
o'lchov ma'nosiz bo'lardi. Shuning uchun B qismi analitik javob bilan
solishtiriladi. Qolgan ikkisi — aralashma qanday **eshitilishi** (yo'llar
muvozanati, panorama ta'siri) va ekranning TalkBack bilan ishlashi — faqat
qurilmada, quloq bilan baholanadi.

### To'qqizinchi tekshiruv — til fayllari (2026-09-17)

Bu tekshiruv boshqalardan farq qiladi: u ovozni emas, **matnni** o'lchaydi.
Sababi — konteynerda APK yig'ilmaydi (`aapt2` faqat x86_64 uchun), ya'ni
til fayllaridagi xatoni ushlaydigan asbob ham yo'q. Xatolar esa jim:
ruscha faylda kalit yetishmasa, foydalanuvchi o'zbekcha satrni ko'radi va
buni hech kim xato deb hisoblamaydi. Shuning uchun
`bin/verify-locales.py` yozildi — tashqi haqiqat sifatida `values/strings.xml`
(tayanch til) olinadi.

**A. Kalitlar to'plami.** To'rt fayl (values, values-b+uz+Cyrl, values-ru,
values-en) 342 tadan kalitga ega, to'plamlari **aynan bir xil**: yetishmagan
ham, ortiqcha ham yo'q.

**B. O'rin egallovchilar.** Bitta kalit uchun `%1$s` kabi belgilar to'rt
faylda bir xil bo'lishi shart. Tarjimada tushib qolsa, foydalanuvchi
«Versiya: » kabi bo'sh satr ko'radi.

**C. Qochirilmagan apostrof.** Android resurslarida `'` faqat `\'` bo'lib
yozilishi kerak, aks holda aapt2 yig'ishni to'xtatadi. O'zbek tilida apostrof
ko'p (`o'zbek`, `to'g'ri`), ya'ni bu — shu loyihada eng ehtimolli yig'ish
xatosi, va uni bu konteynerda boshqa yo'l bilan topib bo'lmaydi.

**D. Yozuv aralashuvi.** Belgilar ataylab aniq tanlandi (keng qoida yolg'on
signal beradi — `OvozStudio`, `Telegram`, `MP3` ham lotin harflarida):
tayanch faylda kirill harfi bo'lmasligi, kirill faylida esa o'zbek
lotiniga xos `o'`/`g'` digrafi bo'lmasligi kerak (kirill yozuvida ular
`ў`/`ғ`), va kirill faylida butunlay lotin yozuvidagi satr bo'lmasligi kerak
(o'rin egallovchilar olib tashlangandan keyin).

**E. O'lik kalitlar.** Kodda va manifestda ishlatilmagan kalitlar
ogohlantirish sifatida chiqadi. Birinchi yurgizishda **9 ta** topildi —
`record_elapsed_a11y`, `record_level_label`, `record_format`, `record_channel`,
`record_channel_mono`, `record_stopped`, `record_error_start`,
`trim_fade_length`, `tag_share_missing`. Hammasi eski ekran qoralamalaridan
qolgan: keyingi versiyalarda o'rniga boshqa kalit ishlatilgan
(`record_error_generic`, `record_level_a11y`, `trim_fade_length_label`).
Har biri kotlinda ham, manifestda ham qo'lda tekshirildi va to'rt fayldan
o'chirildi (342 ta kalit qoldi). O'lik tarjima zararli: tarjimon uni ko'radi,
tarjima qiladi, u esa hech qachon ekranga chiqmaydi.

**Har bir qoida sun'iy xato bilan sinaldi** — hech narsani tutmaydigan
tekshiruv foydasiz, shuning uchun har bir qoida uchun fayl ataylab
buzildi va skript xatoni ko'rsatishi tasdiqlandi, so'ng fayl qaytarildi:

| kiritilgan xato | skript javobi |
|---|---|
| `values-en` dan kalit o'chirildi | «yetishmayotgan kalitlar: home_action_settings» |
| `values-en` da `%1$s` tushirildi | «o'rin egallovchilari mos emas: kutilgan ['%1$s'], topilgan yo`q» |
| `values-en` da yolg'iz apostrof | «qochirilmagan apostrof» |
| kirill faylga lotin matni | «o'zbek lotin digrafi (o'/g') bor — yozuv aralashgan» |
| tayanch faylga kirill matni | «kirill harfi bor — yozuv aralashgan» |

**Nima tekshirilmaydi.** Tilning **haqiqiy** almashishi: `attachBaseContext` +
`createConfigurationContext` + `Activity.recreate()` — bular Android
framework ishi, JVM'da ham, bu konteynerda ham yurgizilmaydi. Skript
fayllarning to'g'riligini kafolatlaydi; ekranda til haqiqatan o'zgarganini
faqat qurilma ko'rsatadi. Shuningdek tarjima **sifatini** (ma'nosi to'g'rimi,
tabiiy o'qiladimi) skript baholay olmaydi — u faqat shaklni tekshiradi.

### O'ninchi tekshiruv — vokal/cholg'u ajratish (2026-09-19)

Ajratishning eng oson yo'li — «ishlayotganga o'xshaydi» degan xulosaga
kelish: ikkita fayl chiqadi, biri balandroq, ikkinchisi pastroq. Shuning
uchun tekshiruv uchta **o'lchanadigan** da'voga bo'lindi va har biri
tashqi asbob bilan olchandi (`ffmpeg`, `ffprobe`, `python3`).

**A. Yig'indi manbani beradi.** Vokal va cholg'u fayllari namuna-ba-namuna
qo'shilib, manba bilan solishtiriladi. Ikkala rejim uchun ham farq
**1 LSB** (16-bitda yaxlitlash; chegaradan 30 baravar kam). Nazorat:
«vokal» fayli manbaning o'zi emas — farq 0.1 dan katta.

**B. «Aniq ayirish» ffmpeg bilan bir xil.** `REMOVE_VOCALS` natijasi
ffmpeg'ning `pan=mono|c0=0.5*c0-0.5*c1` va `pan=mono|c0=0.5*c0+0.5*c1`
filtrlari bilan solishtiriladi: farq **1 LSB** (chegara 5e-4). Ya'ni
«markazni ayirish» atamasi shu yerda taxmin emas — mustaqil dastur
aynan shu sonlarni beradi.

**C. Spektral tozalik.** 440 Hz markazda, 1200 Hz yonga qo'yilgan sinov
signalida Goertzel o'lchovi: vokal faylida 440 Hz bor (≥ 0.36), 1200 Hz
yo'q (≤ 0.1); cholg'u faylida 1200 Hz bor (≥ 0.36), 440 Hz deyarli yo'q
(≤ 0.063). «Yon/markaz» nisbati ham ±0.5 dB aniqlikda tasdiqlandi.

**D. Koeffitsient formulasi.** Eng nozik qism — `d^kuch` ko'rinishi.
Butunlay chapga surilgan ohang uchun `M = S = L/2`, ya'ni `d = 1/2` va
`mask = 0.353553`. Ilova o'lchagan `L` va `R` asosida bash bu sonni
**mustaqil hisoblaydi** va o'lchangan natija bilan solishtiradi (farq
7e-6, chegara 0.003). Faqat `|qiymat|` solishtiriladi: Goertzel ishorani
bermaydi — bu cheklov skriptda yozib qo'yilgan, ishora esa A bo'limida
allaqachon isbotlangan.

**E. Rad etish.** Bitta kanalli fayl ham, kanallari bir xil (mazmunan
mono) fayl ham **ochiq xato** bilan rad etilishi tekshiriladi — «jimgina
natija berish» yo'q.

**Topilgan xato (hujjatda).** D bo'limining o'lchovi `StemSeparator`
KDoc'idagi da'voni **rad etdi**: «qismiy ajratish markazda turgan
cholg'uni saqlaydi» deb yozilgan edi, o'lchov esa buning aksini
ko'rsatdi — butunlay chapga surilgan ohangda ham markazning `d^kuch`
qismigina olinadi, ya'ni markazda turgan baraban yoki bas **ham**
o'chadi. KDoc tuzatildi va ekran matnlariga ham shu cheklov yozildi:
noto'g'ri da'vo qolsa, foydalanuvchi natijani «buzuq» deb hisoblardi.

**Falsifikatsiya — tekshiruvning o'zini sinash.** `bin/falsify-stem.py`
`StemSeparator.kt` ga beshta haqiqiy nuqson kiritadi va
`bin/verify-stem.sh` har birini **o'sha qoidaning nomi bilan** ushlashini
talab qiladi (fayl `finally` da qaytariladi):

| kiritilgan nuqson | qaysi qoida ushladi |
|---|---|
| `REMOVE_VOCALS` maskasi 1.0 → 0.0 | B — «cholg'u (chap) = (L−R)/2» |
| maska teskari qo'llanadi (`1.0 − …`) | C — «vokalda markaz ohangi bor» |
| `d^kuch` → `d^1` (kuch e'tiborsiz) | D — «vokal (chap) = formula bo'yicha» |
| `ERROR_NOT_STEREO` boshqa so'z bilan | E — «bitta kanalli fayl» |
| `ERROR_MONO_CONTENT` boshqa so'z bilan | E — «kanallari bir xil fayl» |

Ikki nuqson bilan urinish muvaffaqiyatsiz bo'ldi va bu **o'zi foydali
natija**: `info.channels != 2` tekshiruvi olib tashlanganda dastur
assertgacha yetib bormasdan yiqilardi, ya'ni «shu qoida ishlayapti»
degan xulosani o'sha nuqson bilan olish mumkin emas edi. Shuning uchun
o'rniga xato **matnini** o'zgartiruvchi nuqsonlar olindi — ular
`expect_error` qoidasini to'g'ridan-to'g'ri falsifikatsiya qiladi.
Shuningdek `expect_error` xato satrini **stderr** ga yozishi aniqlanib,
skript endi ikki oqimni birga o'qiydi.

**Nima tekshirilmaydi.** Ajratish **sifatining** musiqiy bahosi — quloq
bilan tinglash. Skript sonlarni tekshiradi: yig'indi manbani beradimi,
markaz ohangi qayerda qoldi, koeffitsient to'g'rimi. «Bu qo'shiqda vokal
yaxshi ajraldimi» degan savolga faqat egasi qurilmada javob bera oladi.
Shuningdek bu **kanal usuli**, ya'ni cholg'u tembr bo'yicha
ajratilmaydi — markazda turgan cholg'u vokal bilan birga o'chadi (D
bo'limida o'lchandi).

### O'n birinchi tekshiruv — xavfsizlik, chidamlilik va tezlik (2026-09-20)

**Muhim ogohlantirish.** Bu tekshiruv kodni **o'qib chiqish** bilan bajarildi,
kompilyator va Android SDK yo'q muhitda: o'zgarishlar **yig'ilmadi**, sinovlar
**ishga tushirilmadi**. Birinchi ish — `bash bin/run-tests.sh`,
`bash bin/typecheck-android.sh` va CI. Yig'ishdan chiqadigan har qanday xato
shu bo'limdagi o'zgarishlardan biri bo'lishi mumkin — avval shularni
tekshiring. Tezlik haqidagi raqamlar **qurilmada o'lchanmagan**: ular
tuzilishdan chiqarilgan taxmin.

**Xavfsizlik**

25. **`FileProvider` ichki papkaning butun ildizini ochib qo'ygan edi**
    (`<files-path path="." />`). U yerda sozlamalar, aralashma loyihasi va
    tinglash joyi turadi. Ulashiladigan fayllar esa faqat `OvozStudio/` ichida.
    Endi yo'l `OvozStudio/` bilan cheklangan.
26. **`allowBackup="true"` edi.** Avto-zaxira tashqi papkadagi fayllarni ham
    oladi, ya'ni mikrofon yozuvlari va yuklangan hujjatlar bulutga yoki
    `adb backup` orqali kompyuterga chiqib ketishi mumkin edi. Endi
    `false`. (Kerak bo'lsa, faqat sozlama faylini qaytarib yoqish mumkin —
    `dataExtractionRules` bilan; hozircha keraksiz deb topildi.)
27. **PDF dekompressiya bombasi.** `PdfTextReader.inflate` ochilgan hajmni
    cheklamasdi: 32 MB'lik PDF o'nlab gigabaytga ochilib, `OutOfMemoryError`
    berardi. U `Exception` emas, `BookViewModel` esa faqat `Exception` ushlardi —
    ilova yiqilardi. Endi bitta oqim uchun 32 MB va butun hujjat uchun 256 MB
    chegara bor, oshsa `DocumentTooLargeException`. `Inflater.end()` endi xato
    bo'lganda ham chaqiriladi.
28. **EPUB'da umumiy hajm chegarasi yo'q edi.** Har bir fayl uchun 64 MB va
    2000 tagacha fayl — yig'indisi cheksiz. Endi umumiy chegara 64 MB.
29. **`OutOfMemoryError` import paytida ushlanmasdi.** Endi u «hujjat juda
    katta» xatosiga aylanadi.

**Chidamlilik**

30. **Yozish dvigateli xatodan keyin qotib qolardi.** Yozuvchi oqim xato
    bilan tugasa, `running` `true` qolardi, `AudioRecord` va fayl ochiq
    turardi, keyingi «Yozish» esa «allaqachon ketmoqda» deb rad etilardi.
    Endi oqim mikrofonni darrov to'xtatadi, `RecordViewModel` dvigatelni
    bo'shatadi va xatogacha yozilgan qismni saqlaydi.
31. **`AudioRecord.read` xato kodi qaytarsa (masalan, `ERROR_DEAD_OBJECT`),
    sikl protsessorni to'liq band qilib aylanardi** va foydalanuvchiga hech
    narsa aytilmasdi. Endi `ERROR_DEAD_OBJECT` yoki ketma-ket 50 ta xato
    yozishni to'xtatib, xabar beradi.
32. **`start()` va `stop()` xatoda resurs oqizardi.** Fayl ochilmasa
    (`WavWriter` istisno bersa) `AudioRecord` bo'shatilmasdi; disk to'lganda
    `stop()` ichida `writer.close()` istisno berib, ilovani yiqitardi va
    mikrofonni bo'shatmasdi. `WavWriter.close()` ham xatoda oqimni yopmasdi.
    Belgilar ro'yxati (`markers`) ikki oqimdan ishlatilardi — endi
    `CopyOnWriteArrayList`.
33. **Rad etilgan hujjat nusxasi `manba/` da qolib ketardi**
    (`BookViewModel.Imported.of`). `AndroidAudioImporter` buni to'g'ri
    o'chirardi — endi naqsh bir xil.
34. **`MixProjectStore.save` va `AppSettingsStore.save` faylni avval bo'shatib,
    keyin yozardi.** Uzilish yoki disk to'lishi butun loyiha yoki sozlamani
    yo'qotardi (va «yozib bo'lmasa o'zgarish qo'llanmaydi» qoidasini
    buzardi). Endi vaqtinchalik fayl + `fsync` + `rename`
    (`util/AtomicFileWriter.kt`).
35. **`tracks=2000000000` yozilgan buzuq loyiha fayli ochilishni osib qo'yardi**
    — endi qidiruv 256 indeks bilan cheklangan.

**Tezlik** (qurilmada o'lchanmagan)

36. **Bob kodlash sintez bilan bir vaqtda ketadi** (`BookBuilder`). Ilgari
    ikkalasi navbat bilan ishlardi: umumiy vaqt = sintez + kodlash. Endi bob
    kodlash alohida oqimda, keyingi bobning sintezi paytida bajariladi:
    umumiy vaqt ~ ulardan kattasi. Bir vaqtda bitta bob kodlanadi (navbat
    o'sib, diskni to'ldirmasligi uchun). Kodlash to'xtatishga javob beradi.
37. **Kitob MP3'i LAME sifat darajasi 2 → 5** (`ChapterAssembler.MP3_QUALITY`).
    Nutq uchun 128 kbit/s da farq eshitilmaydi, kodlash esa sezilarli tez.
    Musiqa eksporti va konvertor 2 da qoldi.
38. **Kitob yig'ish ekran o'chganda ham davom etadi** (`media/WorkService.kt`).
    Ilgari faqat yozish fon xizmatiga ega edi: kitob yig'ishda ekran
    o'chsa protsessor uxlab, ish to'xtab qolardi, jarayon esa o'ldirilishi
    mumkin edi. Endi `dataSync` turidagi foreground xizmat va
    `PARTIAL_WAKE_LOCK` (5,5 soat chegara bilan). Yangi ruxsatlar:
    `WAKE_LOCK`, `FOREGROUND_SERVICE_DATA_SYNC` (ikkalasi oddiy: so'ralmaydi).
39. **Bob bo'laklari bob tugashi bilanoq o'chadi.** Ilgari butun kitob
    tugagunga qadar keshda turardi (10 soatlik kitobda ~1,6 GB).
40. **Nusxalash buferi 8 KB → 64 KB** (import).
41. **`-Povoz.fastRelease=true` bayrog'i** (Gradle) va CI'da sinov job'i
    `release`: R8 bilan siqilgan reliz. `continue-on-error` — yiqilsa ham
    debug yig'ish buzilmaydi. Standart yig'ishlar o'zgarmagan.

**Tavsiya (qilinmadi).** Debug APK har CI yurishida yangi kalit bilan
imzolanadi — yangilash uchun oldingisini o'chirish kerak va yozuvlar ham
o'chadi: barqaror imzo (kalit GitHub Secrets'da) kerak. Reliz uchun R8
qoidalari qurilmada sinalishi shart. WSOLA `bestShift` va FFT tezlashtirish
mumkin (siljuvchi energiya, haqiqiy-kirish FFT), lekin natijani biroz
o'zgartiradi — avval qurilmada o'lchab, keyin qaror qilish kerak.

### O'n ikkinchi tekshiruv — CI (Gradle) muhiti (2026-09-20)

Birinchi marta push qilindi (18 commit, `cdc676d`) va GitHub Actions
ishga tushdi. `build` job'i yiqildi: **610 testdan 6 tasi** — beshta PDF
sinovi va `DocumentLoaderTest` ning PDF ishi. JVM to'plamida (o'sha 610
test) ular yashil edi, ya'ni farq muhitda edi.

**Sabab.** Namunalar `File("app/src/test/fixtures/...")` — ishchi
katalogga nisbatan — ochilardi. `bin/run-tests.sh` ildizdan ishga
tushiradi, Gradle esa test JVM'ini `app/` da ochadi: yo'l
`app/app/src/test/fixtures/...` bo'lib qolib, fayl topilmaydi. Yiqilgan
sinovlar ro'yxati buni aniq ko'rsatdi — qolgan hamma sinov namunasiz
(`File.createTempFile` yoki `TemporaryFolder`) ishlaydi.

**Tuzatish.** `PdfFixtures.kt` — yo'l ishchi katalogdan **yuqoriga qarab**
izlanadi, shuning uchun ikkala muhitda ham topiladi. Nusxa ko'chirmaslik
uchun yagona joyda (`PdfFixtures.kt`), `run-tests.sh` esa endi ro'yxatdagi
test bo'lmagan faylni ham yig'adi-yu, JUnit'ga bermaydi (aks holda
«No runnable methods»).

**Tekshirildi.** Ildizdan — 610 test OK; Gradle sharti
(`-Duser.dir=<loyiha>/app`) qo'lda takrorlanib — o'sha 20 sinov OK;
eski yo'l o'sha shartda mavjud emasligi ko'rsatildi. `typecheck-android.sh`
— 122 fayl.

**Saboq.** «Sinovlar yashil» degani «CI yashil» degani emas: ishchi
katalog, standart charset va resurs yo'li ikkala muhitda bir xil
tekshirilishi kerak.

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
- Ko'p yo'lli aralashtirish: balandlik, chap/o'ng joylashuv, siljish,
  mute/solo, umumiy balandlik, orqaga qaytarish, avtomatik saqlanadigan
  loyiha; natija stereo va kesishdan himoyalangan.
- Vokal/cholg'u ajratish: stereo yozuv ikki faylga bo'linadi (kanal usuli),
  ikki rejim va kuch maydoni, natija manbani namuna-darajada qoplaydi;
  mono manba ochiq rad etiladi.

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
   ko'rsatadi va to'xtatish mumkin.
   ~~Pleyer va taymer~~ — **tayyor** (18-band): kitob ilovaning o'zida
   boblar bo'ylab tinglanadi, belgilar bo'ylab sakraladi, qoldirilgan joy
   eslab qolinadi, uxlash taymeri 15/30/60 daqiqaga qo'yiladi.
   **Hozircha yo'q:** ilova qayta ochilgach, pleyer ro'yxati qaytadan
   tiklanmaydi — kitobni yana yasash kerak (fayllar joyida qoladi).
   **Qolgani (faqat qurilmada tekshiriladi):** pastda.
7. ~~**ID3 teglar va ulashish**~~ — **tayyor** (19-band). Nom, ijrochi,
   albom, yil, janr, tartib raqami va muqova; faylni tizim oynasi orqali
   boshqa ilovaga yuborish. Teg yangi faylga yoziladi, mavjud teg avval
   o'qiladi (aks holda tahrirlanmagan maydonlar jimgina o'chib ketardi).
   **Hozircha yo'q:** ID3 faqat MP3 da — M4A/FLAC teglari keyingi ish;
   ID3v2.2 o'qilmaydi.
   **Qolgani (faqat qurilmada tekshiriladi):** pastda.
8. ~~**Ko'p yo'lli aralashtirish**~~ — **tayyor** (20-band). Har bir yo'lga
   ovoz balandligi, chap/o'ng joylashuv, siljish, mute/solo va umumiy
   balandlik; natija har doim stereo, kesish himoyasi bilan. Mustaqil
   tekshiruv ffmpeg bilan o'tdi (sakkizinchi tekshiruv, pastda).
   **Hozircha yo'q:** tavsifda tilga olingan **ducking** (bitta yo'l
   ko'tarilganda boshqasini avtomatik tushirish) — u hozir qo'lda
   bajariladi: balandlik maydonini o'zgartirib. Avtomatik ducking
   (ohang bo'yicha aniqlash va yumshatish) alohida ish. Shuningdek
   manbalar faqat WAV va ularning chastotasi teng bo'lishi shart;
   har xil chastotali yo'llarni avtomatik qayta namunalash keyingi ish.
   **Qolgani (faqat qurilmada tekshiriladi):** pastda.
9. ~~**Sozlamalar ekrani**~~ — **tayyor** (21-band). Til tanlash (tizim /
   o'zbek lotin / o'zbek kirill / rus / ingliz), soddalashtirilgan rejim,
   ilova haqida (versiya, litsenziya, manba kod).
   **Mustaqil tekshiruv:** `bin/verify-locales.sh` (to'qqizinchi tekshiruv,
   pastda).
   **Hozircha yo'q:** til faqat ilova ekranlariga va yozish
   bildirishnomasiga ta'sir qiladi — tizim sozlagichlaridan keladigan satrlar
   (ruxsat oynasi kabi) qurilma tilida qolaveradi. Soddalashtirilgan rejim
   ikkinchi darajali tugmalarni yashiradi, funksiyani o'chirmaydi.
   **Qolgani (faqat qurilmada tekshiriladi):** pastda.
10. ~~**Vokal/cholg'u ajratish**~~ — **tayyor** (22-band), lekin **kanal
    usuli** bilan, neyron model bilan emas. Mustaqil tekshiruv o'tdi
    (o'ninchi tekshiruv, yuqorida), falsifikatsiya bilan birga. Ya'ni tavsifdagi imkoniyat bor:
    stereo yozuv vokal va cholg'u fayllariga bo'linadi, natija manbani
    namuna-darajada qoplaydi. Farq shundaki, usul *markazda turgan hamma
    narsani* oladi — markazda turgan bas yoki baraban ham vokal bilan birga
    o'chadi. **Qolgani — neyron model** (ONNX/TFLite, qurilmada ishlaydigan):
    u cholg'uni tembr bo'yicha ajratadi, ya'ni markazda turgan barabanni
    saqlab qoladi. Eng og'ir band: model fayli bir necha o'nlab MB bo'ladi va
    litsenziyasi GPL-3.0 bilan mos bo'lishi shart. Shu sababdan u alohida
    qadam sifatida qoldirildi — ekran va DSP qatlami tayyor, model shu
    interfeys ortiga ulanadi (xuddi TTS'dagi kabi).
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
  yangilanib turyaptimi va «to'xtatish» darhol ishlayaptimi. So'ng pleyer:
  bob tugaganda keyingisi o'zi boshlanadimi, oxirgi bobdan keyin to'xtaydimi,
  «15 soniya orqaga» va bob chegarasida kutilgan joyga tushadimi, uxlash
  taymeri qo'yilgan vaqtda to'xtatadimi, «bob oxirigacha» rejimida gap
  o'rtasida uzilmaydimi. Bu — quloq va qurilma ishi; skript faqat **fayl
  mazmunini** (MP3 kadrlari, CUE yozuvlari, bob nomlari) va pleyer
  mantig'ini tekshira oladi, ovozning o'zini emas.
- Teg va ulashish ekranini sinash: fayl tanlab, maydonlar fayldagi teg bilan
  to'ldirilganini ko'rish (masalan, telefonda o'zbekcha tegli MP3 bo'lsa),
  maydonlar klaviatura bilan kiritiladimi, muqova rasmi tanlanib faylga
  yozilyaptimi va pleyer uni ko'rsatyaptimi, saqlangandan keyin **manba fayl
  joyida qolganini** fayl menejerida tekshirish, «Ulashish» tugmasi tizim
  oynasini ochib, faylni Telegram'ga yuboryaptimi. Skript teglarni
  ffprobe bilan tekshiradi, lekin ulashish oynasini faqat qurilma ko'rsatadi.
- Aralashtirish ekranini sinash: ikki-uch yozuvni qo'shib, balandlik va
  chap/o'ng joylashuv maydonlarini TalkBack bilan kiritib, natijani
  tinglash; yo'lni o'chirib (mute) va «faqat shuni eshitish» bilan
  solishtirish; ekrandan chiqib qaytganda yo'llar joyida turishini
  tekshirish; «orqaga qaytarish» ishlayaptimi. Skript matematikani
  o'lchaydi, lekin **qaysi sozlama qanday eshitilishini** va ekranning
  o'qilishini faqat qurilma ko'rsatadi.
- Sozlamalar ekranini sinash: tilni «o'zbekcha (kirill)» ga o'zgartirib,
  ekran haqiqatan qayta ochilishini va butun interfeys kirill yozuviga
  o'tishini ko'rish; ilovani butunlay yopib qayta ochganda tanlov
  saqlanganini tekshirish; «tizim tili bilan bir xil» ga qaytarib, satrda
  qurilma tili ko'rsatilishini ko'rish; rus va ingliz tillarini ham.
  Soddalashtirilgan rejimni yoqib, ekranda kam tugma qolganini va
  «Boshqa imkoniyatlar» ularni qaytarishini, TalkBack bilan yozuv
  qatoridagi to'xtash nuqtalari kamayganini ko'rish. Til fayllarining
  shaklini skript tekshiradi, lekin ekranda til haqiqatan almashganini va
  soddalashtirilgan rejim **yengilroq** bo'lganini faqat qurilma ko'rsatadi.
- Vokal/cholg'u ajratish ekranini sinash: **vokali markazda yozilgan** haqiqiy
  stereo qo'shiqni tanlab, ikki rejimni solishtirish — «aniq ayirish» da
  vokal butunlay yo'qoladimi va cholg'u sun'iy eshitilmaydimi, «qismiy
  ajratish» da kuch maydonini 0.5 va 4.0 ga qo'yib farq seziladimi.
  Natijadagi ikkita faylni tinglab, «yon/markaz» nisbati ekranda yozgan
  songa mos kelishini ko'rish. **Mono yozuvni tanlab**, ilova uni ochiq rad
  etishini tekshirish. Skript sonlarni o'lchaydi, lekin **qaysi rejim qulog'ga
  yaxshi eshitilishini** va vokalning ajralish sifatini faqat quloq aytadi.

**Ma'lum cheklovlar (keyingi ishlar)**

- Import qilingan manba nusxalari `manba/` papkasida saqlanadi va o'chirish
  tugmasi ularga tegmaydi (u faqat asosiy ro'yxatdagi fayllarni o'chiradi).
  Fayllar ko'payib ketsa, ular uchun tozalash kerak bo'ladi. Teg ekranida
  tanlangan fayl ham shu yerga nusxalanadi (teg butun fayl baytlari bo'yicha
  yoziladi, oqim bilan bu mumkin emas) — ya'ni u ham tozalanmaguncha qoladi.
- Teg ekrani faylni **nusxalab** oladi: katta MP3 (yuzlab MB) uchun bu ikki
  barobar joy egallaydi. Xotira kartasi kam qolgan qurilmada buni yodda
  tutish kerak.
- ID3v2.2 tegli fayl ochilsa maydonlar bo'sh ko'rinadi, saqlash esa tegni
  almashtiradi — ya'ni o'sha fayldagi eski teg yo'qoladi. O'quvchi v2.2 ni
  qo'llashi keyingi ish.
- Aralashtirishda manbalar **faqat WAV** va ularning chastotasi **teng**
  bo'lishi shart. Har xil chastotali yo'l avtomatik qayta namunalanmaydi:
  ochiq xato beriladi va faylni konvertorda mos chastotaga o'tkazish kerak
  bo'ladi. Avtomatik qayta namunalash keyingi ish.
- Aralashma loyihasi `filesDir` da saqlanadi va faqat **nomi** bo'yicha
  manbaga bog'lanadi. Manba fayl o'chirilsa yoki nomi o'zgartirilsa, yo'l
  «manba topilmadi» bo'lib qoladi — yo'qolmaydi, lekin uni qaytadan
  qo'shish kerak.
- Ducking (bir yo'l ko'tarilganda boshqasini avtomatik tushirish) hozir
  qo'lda bajariladi — balandlik maydonini o'zgartirib. Avtomatik ducking
  alohida ish.
- Til ilova ekranlariga va yozish bildirishnomasiga ta'sir qiladi.
  Tizim sozlagichlaridan keladigan satrlar (ruxsat oynasi, fayl tanlagich
  kabi) qurilma tilida qolaveradi — ular ilovaning resurslari emas.
  `Application` konteksti bilan olingan satrlar uchun ham shu hol: til
  Activity yaratilishida qo'llanadi.
- Soddalashtirilgan rejim faqat ikkinchi darajali tugmalarni yashiradi;
  ekran tuzilishi o'zgarmaydi (masalan, kesish ekranidagi maydonlar soni
  o'sha-o'sha). Rejimning maqsadi — ekran o'quvchi bilan navigatsiyani
  yengillashtirish, interfeysni qaytadan loyihalash emas.
- Sozlama fayli (`filesDir/sozlamalar.properties`) yozilmasa, o'zgarish
  qo'llanmaydi va ekran buni aytadi — lekin sababini aniqlash imkoni yo'q
  (joy yo'qmi, ruxsatmi). Xato matni ikkalasini ham qamrab oladi.

## Server (kelajak) — qaror va reja

**Hozir: kod ham, `INTERNET` ruxsati ham yo'q — ataylab.** Server bo'lmaguncha
foydasi yo'q, ruxsat esa ilovaga ishonchni kamaytiradi va hujum yuzasini
oshiradi.

Server nimani tezlashtiradi: **matn → audio** (neyron ovoz) va og'ir neyron
DSP. Nimani **emas**: kesish, ekvalayzer, tezlik — ularni mahalliy bajarish
faylni yuklab-tushirishdan tezroq.

Qo'shilganda kerak bo'ladigan ish (ortiqcha qadamsiz):
- `VoiceEngine` interfeysi ortiga `RemoteVoiceEngine` (interfeys tayyor);
- faqat HTTPS (`network_security_config`: cleartext yo'q), token
  Android Keystore'da shifrlangan; ilova o'z-o'zidan hech narsa yubormaydi;
- so'rov: **matn ketadi, siqilgan audio (MP3/Opus) qaytadi** — yuklash kichik;
  natija oqim bilan (birinchi bo'lak darrov), bo'laklar parallel, natija
  keshlanadi;
- oflayn yoki sekin bo'lsa — qurilmaning o'z ovoziga qaytish;
- yoqish — bitta tugma (sozlamada), manzil `BuildConfig` da.

Ochiq kod xavfsizligi: sirlar (token, kalit) repoga tushmaydi; klientga
ishonib bo'lmaydi (uni istagan kishi o'zgartirib yig'adi), shuning uchun
autentifikatsiya, limit va hajm tekshiruvi **serverda**. Server kodi ochiq
bo'lsa, tarmoq orqali ishlatishni ham qamrab oladigan **AGPL-3.0** mos.

## Ochiq savollar

- Ilovaning yakuniy nomi va paket nomi (`uz.ovozstudio.app` — vaqtinchalik).
- ~~Litsenziya~~ — **hal qilindi: GPL-3.0** (`LICENSE`). Sabab: egasining
  talabi «hech kim reklama joylolmasin». GPL yopiq kodli forkni taqiqlaydi,
  ya'ni reklamali yopiq nusxa paydo bo'lishi huquqiy jihatdan mumkin emas.
  MIT bu talabni bajarmasdi.
  **Aniqlik (2026-09-20):** GPL faqat *yopiq kodli* forkni taqiqlaydi. U
  reklamali, lekin **ochiq kodli** forkni taqiqlamaydi — reklama qo'shish
  GPL bo'yicha ruxsat etilgan. «Hech kim reklama joylolmasin» talabi
  litsenziya bilan to'liq bajarilmaydi; nom va logotipni (товарный знак)
  himoyalash alohida yo'l. Muhim bo'lsa, yurist bilan maslahatlashing.
- Ovozlar: qurilma TTS'i bilan boshlanadi; bulut AI ovoziga o'tish qarori
  ovoz sifati sinovidan keyin.
- Fayllarni «Musiqa» papkasiga chiqarish (MediaStore) kerakmi — hozir
  ilovaning o'z papkasida, ruxsat so'ralmaydi.
