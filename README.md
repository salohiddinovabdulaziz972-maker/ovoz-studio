# Ovoz Studio

Android uchun ochiq kodli, **internetsiz** ishlaydigan ilova: audio va PDF
fayllarni tahrirlash, hujjatlarni o'qish. Ekran o'quvchi (TalkBack) bilan
ishlash uchun yozilgan; ilova matni sodda o'zbek tilida.

Versiya: **0.2.0**. Litsenziya: **GPL-3.0**.

## Nima qiladi

| Bo'lim | Imkoniyat |
|---|---|
| Audio | belgilangan qismni **kesib olish** — faqat shu qism qoladi |
| Audio | belgilangan qismni **o'chirish** — qolgan qismlar birlashadi |
| Audio | bir nechta audioni **birlashtirish** |
| PDF | belgilangan sahifalarni **kesib olish** — yangi PDF |
| PDF | belgilangan sahifalarni **o'chirish** — yangi PDF |
| Hujjat | hujjatni **o'qish**: matn ekranda, ekran o'quvchi bilan yoki ilovaning ovozida (Microsoft Sardor/Madina ham, agar qurilmada bo'lsa) |
| Jurnal | **xatolar jurnali**: nima buzilgani yozib boriladi, foydalanuvchi uni ulashishi mumkin |

Boshqa hech narsa yo'q: yozib olish, konvertatsiya, ekvalayzer, shovqin
tozalash va shunga o'xshashlar olib tashlangan.

## Format qoidasi (qat'iy)

**Audio qaysi formatda yuklansa, natija aynan shu formatda qaytadi.**
Boshqa formatga «taklif» ham, jimgina «zaxira» ham yo'q.

- MP3, M4A/AAC, WAV, FLAC va (Android 10+, 48 kHz) OGG Opus shu formatga qayta yoziladi.
- Android yoza olmaydigan format (OGG Vorbis, WMA) **ish boshlanmasdan oldin**
  rad etiladi va sababi aytiladi — foydalanuvchi soatlab ishlab, oxirida
  «saqlab bo'lmadi» degan xabarni ko'rmaydi.
- Bir nechta faylni birlashtirishda hamma fayl **bir xil formatda** bo'lishi
  shart. Chastotasi yoki kanali (mono/stereo) boshqa fayllar moslashtiriladi.
- Yo'qotishli formatda (MP3, AAC) bit tezligi asl fayl hajmi va uzunligidan
  taxmin qilinadi: 64 kbit/s lik yozuv bir necha baravar kattalashmaydi.

Ichkarida tahrirlash yo'qotishsiz PCM (WAV) ustida ketadi; faqat oxirida
natija asl formatga yoziladi. Kesilgan va tutashgan joylarda 5 ms lik silliq
o'tish bor: «chiqillash» eshitilmaydi.

## PDF

Sahifalar bitta maydonda yoziladi: `3, 5-8, 12`. Natija yangi hujjatga
ko'chirish yo'li bilan yasaladi — o'chirilgan sahifalarning mazmuni faylda
**qolmaydi**. Asl fayl o'zgarmaydi. Parol bilan ochiladigan yoki muallif
ajratishni taqiqlagan PDF rad etiladi. Kutubxona: PDFBox (Android porti, Apache-2.0).

## Hujjatni o'qish

Qo'llab-quvvatlanadi: **PDF, DOCX, ODT (ODS, ODP), RTF, EPUB, FB2, HTML, PPTX, TXT**
(va `.md`, `.log`, `.csv` kabi oddiy matn). Fayl kengaytmasi noto'g'ri bo'lsa
ham (masalan RTF `.doc` nomi bilan) imzosiga qarab tanib olinadi.

- Matn abzatslarga bo'linadi va ekranda turadi: **ekran o'quvchi** o'qiydi.
- «O'qishni boshlash» — ilovaning ovozi. Ovoz dvigateli va ovoz tanlanadi.
  Nomida «Sardor» yoki «Madina» bor ovoz bo'lsa, u avtomatik tanlanadi.
- PDF da har bir sahifa — bob. Matni buzuq (shrift kodlashi noma'lum) PDF
  «buzuq matn» deb aytiladi, tushunarsiz shovqin o'qib berilmaydi.

**Microsoft Sardor/Madina haqida.** Bu bulutli ovozlar; Android'da ular faqat
ularni tizim sintezatori sifatida ochib beradigan dvigatel dasturi orqali
ko'rinadi. Ilovaning o'zi internetga chiqmaydi, shuning uchun bulutga ulanmaydi.

Qo'llab-quvvatlanmaydi: eski Word `.doc`, `.xls`, `.ppt` (DOCX/PPTX qilib
saqlang), parol bilan himoyalangan PDF, matn qatlami yo'q skaner PDF (OCR yo'q).

## Til

Ilova tili **tizim tilidan** aniqlanadi (`LocaleContext.followSystem`):
o'zbekcha (lotin), o'zbekcha (kirill), ruscha, inglizcha. Tizim tili
o'zbekcha bo'lsa ilova to'liq o'zbekcha. Qo'llab-quvvatlanmagan til —
o'zbekcha. Sozlamalarda til tanlash yo'q; Android 13+ da «Ilova tili» ishlaydi.

## Xatolar jurnali

`Bosh ekran → Xatolar jurnali`. Ilova xatolarni qurilmadagi matn fayliga
yozadi (`filesDir/jurnal/`), shu jumladan kutilmagan to'xtashlarni.

- hajmi cheklangan (256 KB + eski nusxa), cheksiz o'smaydi;
- **fayl nomlari, `content://` havolalari, yo'llar va hujjat matni yozilmaydi**;
- o'zi hech qayerga yuborilmaydi: foydalanuvchi «Ulashish» yoki «Saqlash» ni bosadi.

## Maxfiylik va xavfsizlik

- **Hech qanday ruxsat so'ralmaydi**, `INTERNET` ham yo'q. Fayllar tizim
  tanlagichi orqali olinadi va saqlanadi.
- Zaxira nusxa o'chirilgan (`allowBackup=false`).
- `FileProvider` faqat tayyor natijalar papkasini (`cache/natija/`) ochadi.
- Ish fayllari kesh papkasida; ilova boshlanganda tozalanadi, natijalar bir kun saqlanadi.
- Batafsil: `SECURITY.md`.

## Yig'ish va sinash

```
gradle testDebugUnitTest     # birlik testlari (sof JVM)
gradle assembleDebug         # APK
```

GitHub Actions (`.github/workflows/android.yml`) shuni bajaradi. Qo'shimcha:

| Skript | Vazifasi |
|---|---|
| `bin/verify-locales.sh` | to'rt til fayli: kalitlar, `%1$s`, apostroflar, yozuv aralashuvi |
| `bin/run-tests.sh` | Android'siz JVM testlari (kotlinc bilan) |
| `bin/typecheck-android.sh` | butun manbani haqiqiy kompilyatordan o'tkazish |
| `bin/verify-mp3.sh` | MP3 kodlovchisini `ffmpeg` bilan tekshirish |
| `bin/dump-source.py` | butun manbani bitta matn fayliga yig'ish |

## Tuzilish

```
app/src/main/java/uz/ovozstudio/app/
  OvozApp.kt, MainActivity.kt
  log/            xatolar jurnali (ErrorJournal, ErrorLog)
  settings/       til aniqlash (AppLanguage, LocaleContext), About
  media/          WavFile, WavWriter, AudioTrimmer, AudioPlayer, WorkStore
    format/       import/eksport, qat'iy format qoidasi (StrictFormat, AudioOpener)
    merge/        AudioMerger
    dsp/          Resampler
    pdf/          PdfPageTools (PDFBox), PageRange
    doc/          hujjat o'quvchilari, ReadingText, TextQuality
    voice/        ovoz sintezatori (DeviceTtsEngine, VoiceChoice, VoicePrefs)
  ui/             home, trim (kesish/o'chirish), merge, pdf, reader, log, nav, common, theme
  util/           vaqt, son formati, Sharing, ResultFiles
```

## Ma'lum cheklovlar

- OGG Vorbis va WMA audio ochilmaydi (yozib bo'lmaydi — qoida shuni talab qiladi).
- Mixed-format birlashtirish yo'q (MP3 + M4A): qaysi biriga o'xshatish noaniq.
- Birinchi PDF ochilganda PDFBox resurslarni yuklaydi — bir necha soniya.
- PDF ni yangi hujjatga ko'chirishda xatcho'plar, tegli tuzilma va shakl maydonlari saqlanmaydi.
- Ilova hali telefonda sinalmagan: qarang `docs/PROGRESS.md`, «Tekshiruv holati».
