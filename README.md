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
- **Aniq kesish**: vaqtni slayder bilan emas, to'rt maydonda qo'lda kiritish
  (soat / daqiqa / soniya / millisoniya), tanlangan qismni eshitish,
  silliq boshlanish va tugash (fade in/out), orqaga va oldinga qaytarish.
- **Accessibility**: har bir interaktiv element matnli yorliqqa ega, minimal
  tegish maydoni 48 dp, vaqt va daraja faqat so'ralganda ovoz bilan e'lon qilinadi.
- **Tillar**: o'zbek (lotin va kirill), rus, ingliz. Til qurilmadan olinadi —
  kod yozish shart emas. Standart (zaxira) til — **o'zbekcha**: qurilma tili
  ro'yxatdagi tillardan biri bo'lmasa ham, ilova o'zbekcha ochiladi.

## Nima hali yo'q

Effektlar (ekvalayzer, shovqin tozalash), format konvertori, vokal/cholg'u
ajratish, TTS/STT, hujjatlarni audiolashtirish va audio-kitob. Bularning hammasi
reja bo'yicha keyingi bosqichlarda — tartibi `docs/PROGRESS.md` da.

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

Ikki qatlam bor — ikkalasi ham Android SDK'siz, oddiy kompyuterda ishlaydi.

**1. Mantiq testlari** — WAV, kesish va vaqt:

```
bash bin/run-tests.sh
```

36 ta test: vaqtni o'qish/yozish, WAV sarlavhasi va namunlarning aniqligi,
kesish, ko'p nuqtali o'chirish, bo'lish, fade. Gradle orqali ham ishlaydi
(`gradle testDebugUnitTest`) — CI shuni bajaradi.

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

Bu ikki qatlam shunchaki nazorat emas. Ular ustida ishlash davomida bir necha
jiddiy xato topildi: biri ilovani umuman yig'ib bo'lmas holga keltirgan,
biri har bir o'chirishda bir kadrni jimgina yo'qotardi, biri ekran
o'quvchisi uchun vaqtni noto'g'ri tilda o'qirdi. Batafsil: `docs/PROGRESS.md`.

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
  media/      WAV yozish/o'qish, kesish, pleyer, fayl saqlash
  ui/common/  Accessibility komponentlari, vaqt kiritish maydonlari
  ui/home/    bosh ekran va fayllar ro'yxati
  ui/record/  yozib olish ekrani
  ui/trim/    kesish ekrani
  util/       vaqt formatlash
```

Muhim texnik qarorlar `docs/PROGRESS.md` da qisqa izohlangan.
