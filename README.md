# Ovoz Studio

O'zbek tilidagi, ekran o'quvchilarga (TalkBack / VoiceOver) to'liq moslashgan
professional audio tahrirlovchi — Android uchun.

> Bu ishchi nom. Yakuniy nom va brendni egasi tanlaydi.

## Ilovani yuklab olish

Tayyor APK GitHub Actions artifact sifatida turadi — kompyuter kerak emas,
telefondan ham yuklab olish mumkin:

https://github.com/salohiddinovabdulaziz972-maker/ovoz-studio/actions

Eng yuqoridagi yashil yozuvni oching → pastdagi **Artifacts** → `ovozstudio-debug-apk`.
Yuklangan fayl ustiga bosib o'rnatasiz (Android «noma'lum manba» ruxsatini
so'raydi — bu normal, ilova hali do'konga qo'yilmagan).

## Nima bor (V1, hozirgi holat)

- **Yozib olish**: PCM WAV, 44.1 / 48 / 96 kHz, 16 yoki 24-bit, mono/stereo,
  pauza-davom ettirish, belgilar (marker), shovqin bostirish va exo yo'qotish.
  Ekran o'chsa yoki ilova fonda qolsa ham yozuv davom etadi.
- **Aniq kesish**: vaqtni slayder bilan emas, to'rt maydonda qo'lda kiritish
  (soat / daqiqa / soniya / millisoniya), tanlangan qismni eshitish,
  silliq boshlanish va tugash (fade in/out), orqaga va oldinga qaytarish.
- **Bo'lish va ko'p nuqtali o'chirish**: faylni ixtiyoriy nuqtadan ikki
  qismga bo'lish; bir nechta oraliqni ro'yxatga yig'ib, hammasini bir marta
  o'chirish.
- **Format konvertori**: MP3, WAV, M4A, FLAC, AAC, OPUS. Fayl import qilinsa,
  tahrirdan keyin **o'z formatida** qaytadi (talab shu edi). WMA va OGG
  yozilmaydi — Android'da ular uchun kodlovchi yo'q, buni import paytida ochiq
  aytiladi.
- **Ekvalayzer**: 10 va 31 polosa, 6 tayyor profil, past chastota kesish,
  kesish himoyasi (cho'qqi ko'tarilsa butun fayl bir xil tushiriladi). Har bir
  polosa raqamli maydonda kiritiladi.
- **Tezlik va ohang**: 0.5x–2x, ohang ±12 yarim ton; ohang o'zgarganda uzunlik
  o'zgarmaydi, ya'ni ikkalasini birga qo'llash mumkin.
- **Shovqin tozalash**: shovqin namunasini belgilaysiz (odatda yozuv boshi),
  ilova o'sha profilni butun fayldan ayiradi. Kuch va qoldiq qo'lda kiritiladi.
  Halol aytilgan cheklov: bu **statistik spektral ayirish**, neyron tarmoq
  emas — batafsil `docs/PROGRESS.md` da.
- **Ovoz bilan o'qish (TTS)**: `VoiceEngine` abstraksiyasi va uning ustida
  «Ovoz sinovi» ekrani — qurilmada qanday ovozlar bor, o'zbek ovozi topildimi,
  uzun matn to'g'ri bo'laklarga bo'linyaptimi. Uzun matn jumla chegarasida
  bo'linadi (sintezator chegaradan uzun matnni jimgina tashlab ketadi), til
  esa matnning yozuvidan (lotin/kirill) tanlanadi. Halol cheklov: hozircha
  bu **qurilmaning o'z ovozi** — o'zbek ovozi o'rnatilmagan bo'lsa, u zaxira
  tilga o'tadi. Neyron AI ovozi keyin shu interfeys ortiga ulanadi.
- **Hujjatdan audio-kitob**: PDF, DOCX, EPUB yoki TXT hujjatni tanlaysiz —
  ilova matnni boblarga bo'lib, har bir bobni alohida **MP3** fayl qilib
  o'qiydi. Fayl nomida bob tartib raqami turadi (`Kitob - 01 - BIRINCHI
  BOB.mp3`), har bir bob uchun belgilar varaqasi (CUE) yoziladi. Boblar
  ro'yxati yasashdan oldin ko'rsatiladi — bo'linish to'g'rimi, foydalanuvchi
  o'zi ko'radi. Tezlik va balandlik qo'lda kiritiladi, jarayon foizda
  ko'rinadi, to'xtatish istalgan paytda ishlaydi. Halol cheklovlar:
  skaner qilingan PDF'da matn qatlami yo'q (OCR ilovada yo'q) va `ToUnicode`
  jadvalisiz murakkab shriftli PDF **ataylab** o'qilmaydi — «savatcha» matn
  o'qigandan ko'ra ochiq xato yaxshiroq.
- **Kitob pleyeri va uxlash taymeri**: yasalgan kitob ilovaning o'zida
  tinglanadi — boblar ketma-ket o'tadi, bob tugaganda keyingisi o'zi
  boshlanadi, oxirgi bobdan keyin pleyer to'xtaydi. Har bir tugma nima
  qilishini aytadi: 15 soniya orqaga/oldinga, oldingi/keyingi bo'lim
  (belgi bo'ylab), oldingi/keyingi bob. To'xtatilgan joy eslab qolinadi —
  kitob qayta yasalsa, tugma «Davom etish» bo'lib turadi. Uxlash taymeri
  15/30/60 daqiqaga qo'yiladi, «bob oxirigacha» rejimida esa gap
  o'rtasida uzilmaydi: vaqt tugasa ham joriy bob oxirigacha o'qiladi.
  Vaqt faqat o'qish paytida sanaydi — tanaffusda taymer ham to'xtaydi.
- **ID3 teglar**: MP3 faylning nomi, ijrochisi, albomi, yili, janri, tartib
  raqami va muqova rasmi tahrirlanadi. Maydonlar **fayldagi mavjud teg bilan**
  to'ldiriladi — aks holda faqat muqova qo'shmoqchi bo'lgan foydalanuvchi
  nomini jimgina yo'qotardi. Manba fayl o'zgarmaydi, teg yangi faylga
  yoziladi. Halol cheklov: ID3 faqat MP3 da bor — WAV/M4A/FLAC uchun bu
  ekran formatni aytadi va konvertorga havola beradi (M4A va FLAC teglari
  boshqa standartda, ular alohida qo'shiladi). ID3v2.2 teglari o'qilmaydi:
  eski fayl ochilsa maydonlar bo'sh chiqadi.
- **Ko'p yo'lli aralashtirish**: bir necha yozuv bitta faylga qo'shiladi.
  Har bir yo'lga balandlik (desibelda), chap/o'ng joylashuv va siljish
  beriladi; yo'lni o'chirish (mute), faqat bittasini eshitish (solo) va
  butun aralashmaning umumiy balandligi bor. Natija **har doim stereo** —
  panorama faqat ikki kanalda ma'noga ega. Yo'llar yig'indisi chegaradan
  oshsa, har bir namuna alohida qisilmaydi: butun fayl bitta koeffitsientga
  tushiriladi va ekran buni aytadi. Har bir o'zgarish darhol saqlanadi,
  «orqaga qaytarish» esa oxirgi 30 qadamni tiklaydi. Halol cheklovlar:
  manbalar **WAV** bo'lishi kerak (boshqa format konvertorda o'tkaziladi),
  yo'llarning chastotasi teng bo'lishi shart — har xil chastota jimgina
  qayta namunalanmaydi, ochiq xato beriladi. Sakkiz yo'lgacha.
- **Ulashish**: tayyor faylni tizim oynasi orqali boshqa ilovaga yuborish
  (Telegram, pochta, bulut). Fayl `content://` havola bilan, faqat o'qish
  uchun va bir marta beriladi.
- **Sozlamalar**: til qo'lda tanlanadi — «tizim tili bilan bir xil», o'zbekcha
  (lotin), o'zbekcha (kirill), ruscha yoki inglizcha. Ekranda **hozir amalda
  ishlayotgan til** ham yozib qo'yiladi: «tizim tili» tanlanganda qaysi til
  ochilishini foydalanuvchi oldindan biladi (qurilma tili ro'yxatdagi
  tillardan biri bo'lmasa — o'zbekcha). Til darhol qo'llanadi, ekran qayta
  ochiladi va tanlov keyingi ishga tushirishda ham saqlanadi; yozib
  bo'lmasa (joy yo'q) — o'zgarish qo'llanmaydi va ekran buni aytadi, jimgina
  «eski tilga qaytib qolish» bo'lmaydi. Soddalashtirilgan rejim ikkinchi
  darajali tugmalarni yashiradi (ekran o'quvchi bilan har bir amalga yetish
  osonlashadi), lekin **hech narsani yo'qotmaydi**: bosh ekranda «Boshqa
  imkoniyatlar» tugmasi ochib beradi va rejim yoniqligi ekranda yozib
  qo'yiladi. Ilova haqida bo'limida versiya, litsenziya nomi va manba kod
  havolasi bor. Halol cheklov: til ilova ekranlariga va yozish bildirishnomasiga
  ta'sir qiladi; tizim sozlagichlaridan keladigan bir necha satr (masalan,
  ruxsat oynasi) qurilma tilida qolaveradi.
- **Accessibility**: har bir interaktiv element matnli yorliqqa ega, minimal
  tegish maydoni 48 dp, vaqt va daraja faqat so'ralganda ovoz bilan e'lon qilinadi.
- **Tillar**: o'zbek (lotin va kirill), rus, ingliz. Til qurilmadan olinadi —
  kod yozish shart emas, xohlasa sozlamalarda qo'lda tanlanadi. Standart
  (zaxira) til — **o'zbekcha**: qurilma tili ro'yxatdagi tillardan biri
  bo'lmasa ham, ilova o'zbekcha ochiladi. Kirill yozuvini tanlash alohida:
  o'zbekcha ikki yozuvda yoziladi, ya'ni «o'zbek tili» o'zi yetarli emas.

## Nima hali yo'q

Nutqni matnga aylantirish (STT),
vokal/cholg'u ajratish va neyron shovqin
tozalash. Aralashtirishda manbalar hozircha faqat WAV (boshqa format
konvertorda o'tkaziladi) va ularning chastotasi teng bo'lishi shart.
ID3 teg faqat MP3 faylga yoziladi (M4A/FLAC teglari keyingi
qadamda). Kitob pleyeri faqat shu seansda yasalgan kitobni tinglaydi:
ilova qayta ochilgach, kitobni yana yasash kerak (fayllar joyida qoladi).
Ovoz sintezi (TTS) bor, lekin hozircha faqat
qurilma ovozi bilan — o'zbekcha neyron AI ovozi shu interfeys ortiga keyin
ulanadi. Hammasi reja bo'yicha ketma-ket qo'shiladi — to'liq ro'yxat va
tartib `docs/PROGRESS.md` da.

## Qurish

**Android Studio orqali (eng oson).** Loyihani oching, Gradle sinxronlanishini
kuting va `Run` tugmasini bosing. Studio gradle wrapper'ni o'zi yaratadi —
repoda `gradlew` va `gradle-wrapper.jar` ataylab saqlanmagan (binary fayl).

**Buyruq qatoridan**, Gradle 8.11.1 o'rnatilgan bo'lsa:

```
gradle assembleDebug
```

Tayyor APK: `app/build/outputs/apk/debug/app-debug.apk`

**GitHub Actions orqali.** `.github/workflows/android.yml` har push'da
testlarni yurgizadi, debug APK yig'adi va artifact sifatida beradi — kompyuter
shart emas, telefondan yuklab olish kifoya.

## Tekshirish (testlar)

Uch qatlam bor — uchalasi ham Android SDK'siz, oddiy kompyuterda ishlaydi.

**1. Mantiq testlari** — WAV, kesish, vaqt, DSP jadvallari:

```
bash bin/run-tests.sh
```

568 ta test: vaqtni o'qish/yozish, WAV sarlavhasi va namunlarning aniqligi,
kesish, ko'p nuqtali o'chirish, bo'lish, fade, «butun fayl o'chirilmoqda»
holatini oldindan aniqlash, ekvalayzer va shovqin sozlamalarining chegaralari,
matnni bo'laklarga bo'lish va yozuvni (lotin/kirill) aniqlash, hujjat
o'qish (PDF/DOCX/EPUB/TXT), boblarga bo'lish va kitob yig'ish (soxta
sintezator bilan: tartib, pauza, to'xtatish, xato bob raqami), kitob
pleyerining mantig'i (boblar bo'ylab o'tish chegaralari, belgi bo'ylab
sakrash, qoldirilgan joyni saqlash, uxlash taymeri), ID3 tegining to'g'ri
yozilishi va **o'qilishi** (kirill, UTF-8, UTF-16, v2.4 ramka o'lchami,
buzilgan teg ilovani yiqitmasligi), aralashtirish ekranining hisoblari (aralashma
uzunligi, tugma yoqilganmi) va yo'l sozlamalari matnining chegaralari
(-60 dB maydonga sig'adimi, matn va son orasidagi aylanish aynanmi).
Sozlamalar mantig'i ham shu yerda: qurilma tilidan ilova tilini aniqlash
(kirill yozuvi `uz-Cyrl` tegidan ajraladi, tartib saqlanadi, notanish til —
o'zbekchaga tushadi), sozlama faylining har bir holati (yo'q, bo'sh, buzuq,
notanish teg — hech biri ilovani yiqitmaydi) va «hozir ishlatilayotgan til»
satrining hisobi.
Gradle orqali ham ishlaydi (`gradle testDebugUnitTest`) — CI shuni bajaradi.

**2. Butun kodni kompilyatsiya qilish** — ekranlar, ViewModel'lar,
accessibility qatlami:

```
bash bin/typecheck-android.sh
```

Bu skript android.jar, AndroidX/Compose sinflari va Compose kompilyator
plagini yuklab oladi (bir marta, keshda saqlanadi) va **barcha** manba
fayllarni haqiqiy kompilyatordan o'tkazadi. APK yig'ilmaydi — u uchun
`aapt2` kerak, u esa faqat x86_64 uchun chiqariladi — lekin Kotlin xatolari
CI'ni kutmasdan, bir necha soniyada topiladi.

**3. Mustaqil tekshiruvlar** — qayta ishlangan ovozni **boshqa** dastur
o'lchaydi. Bu uchinchi qatlam eng muhimi: ilovaning o'zi o'z natijasini
tekshirsa, bu o'z-o'zini tekshirish bo'lardi — «to'g'ri ko'rinadi» deganidan
nariga o'tmaydi.

```
bash bin/verify-mp3.sh      # MP3 kodlovchisi ffmpeg bilan
bash bin/verify-eq.sh       # ekvalayzer ffmpeg'ning equalizer filtri bilan
bash bin/verify-speed.sh    # tezlik/ohang ffmpeg'ning atempo zanjiri bilan
bash bin/verify-noise.sh    # shovqin tozalash ffmpeg'ning afftdn filtri bilan
bash bin/verify-tag.sh      # ID3 teglarini ffprobe o'qiydi, tegni ilova
                            # o'z o'quvchisi bilan qayta o'qib solishtiradi
bash bin/verify-mix.sh      # aralashmani ffmpeg o'qiydi: balandlik va
                            # panorama analitik javobga mosmi, kesish
                            # himoyasi ishlayaptimi
bash bin/verify-locales.sh  # to'rt til fayli: kalitlar to'plami, o'rin
                            # egallovchilar, qochirilmagan apostrof, yozuv
                            # aralashuvi
```

`verify-locales.sh` boshqalardan farq qiladi: u ilovani ishga solmaydi,
balki uni **yig'ish** imkonsizligini qoplaydi. APK bu konteynerda
yig'ilmaydi (`aapt2` faqat x86_64 uchun), ya'ni til fayllaridagi xatoni
ushlaydigan aapt2 ham yo'q. Xatolar esa jim: ruscha faylda kalit yetishmasa,
foydalanuvchi buni «xato» deb hisoblamaydi — shunchaki o'zbekcha satrni
ko'radi. Shuning uchun har bir qoida sun'iy xato kiritib tekshirildi.

Har biri kerakli hollarni o'zi yaratadi, ilovani ishga soladi va natijani
mustaqil o'lchaydi (`ffmpeg`, `python3`). Batafsil natijalar va topilgan
xatolar `docs/PROGRESS.md` da.

Bu qatlamlar shunchaki nazorat emas. Ular ustida ishlash davomida bir necha
jiddiy xato topildi: biri ilovani umuman yig'ib bo'lmas holga keltirgan,
biri har bir o'chirishda bir kadrni jimgina yo'qotardi, biri ekran
o'quvchisi uchun vaqtni noto'g'ri tilda o'qirdi, biri 24-bit faylni butunlay
buzardi. Batafsil: `docs/PROGRESS.md`.

## Litsenziya — GPL-3.0

Bu loyiha **GNU GPL v3** ostida tarqatiladi (`LICENSE` fayli). Litsenziya
ataylab shunday tanlangan: u ilovani yopiq kodli qilib olishni taqiqlaydi.
Kim ushbu kodni olib o'zgartirsa va tarqatsa, manba kodini ham xuddi shu
litsenziya ostida ochiq berishga majbur. Ya'ni **hech kim bu ilovaga reklama
joylashtirib, yopiq mahsulot sifatida sotolmaydi** — bu huquqiy chegara,
shunchaki va'da emas.

Loyihaga hissa qo'shish ham shu shart bilan qabul qilinadi.

## Nega o'zbekcha

Ilovaning standart tili — o'zbekcha, va bu tasodif emas. Ekran o'quvchi
foydalanuvchisi uchun interfeys tili — bu qulaylik emas, balki ilovadan
foydalanish imkoniyatining o'zi. Qurilma tili ro'yxatdagi tillardan biri
bo'lmasa ham, ilova o'zbekcha ochiladi; qurilma o'zbekcha bo'lsa — lotin
yoki kirill yozuvi avtomatik tanlanadi.

## Talablar

- minSdk 24 (Android 7.0), targetSdk 35
- JDK 17
- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), AGP 8.7.3

## Tuzilish

```
app/src/main/java/uz/ovozstudio/app/
  media/      WAV yozish/o'qish, kesish, pleyer, fayl saqlash, fon xizmati
  media/dsp/  ekvalayzer, tezlik/ohang (WSOLA), shovqin tozalash, FFT
  media/format/ format aniqlash, kodlovchilar, formatni saqlash
  media/voice/ ovoz dvigateli interfeysi, qurilma TTS'i, matnni bo'laklash
  media/doc/  hujjat o'qish: TXT, DOCX, EPUB, PDF (o'zimizning o'quvchi)
  media/book/ boblarga bo'lish, kitob yig'ish, belgilar, pleyer tartibi,
              qoldirilgan joy, uxlash taymeri
  media/tag/  ID3 tegini yozish va o'qish, foydalanuvchi kiritgan qiymatlar
  media/mix/  ko'p yo'lli aralashtirish: mikser, yo'l sozlamalari,
              loyihani saqlash
  settings/   til tanlash mantig'i, sozlamalar fayli, litsenziya havolalari
  ui/common/  Accessibility komponentlari, vaqt va raqam kiritish maydonlari
  ui/home/    bosh ekran va fayllar ro'yxati
  ui/record/  yozib olish ekrani
  ui/trim/    kesish ekrani
  ui/convert/ format konvertori ekrani
  ui/eq/      ekvalayzer ekrani
  ui/speed/   tezlik va ohang ekrani
  ui/noise/   shovqin tozalash ekrani
  ui/voice/   ovoz sinovi ekrani (qurilmada qanday ovozlar bor)
  ui/book/    hujjatdan audio-kitob ekrani va pleyer
  ui/tag/     ID3 teg muharriri va ulashish ekrani
  ui/mix/     ko'p yo'lli aralashtirish ekrani
  ui/settings/ sozlamalar ekrani: til, soddalashtirilgan rejim, ilova haqida
  ui/nav/     ekranlar orasidagi yo'l
  util/       vaqt va raqam formatlash
bin/          testlar, kompilyatsiya va mustaqil tekshiruv skriptlari
tools/        tekshiruv dasturlari (ilovani buyruq qatoridan ishga soladi)
```

Muhim texnik qarorlar `docs/PROGRESS.md` da qisqa izohlangan.
