# Ovoz Studio

Android uchun ochiq kodli, **internetsiz** ishlaydigan ilova: audio va PDF
fayllarni tahrirlash. Ekran o'quvchi (TalkBack) bilan ishlash uchun
yozilgan; ilova matni sodda o'zbek tilida.

Versiya: **0.7.1**. Litsenziya: **GPL-3.0**.

## Nima qiladi

| Bo'lim | Imkoniyat |
|---|---|
| Audio | belgilangan qismni **kesib olish** — faqat shu qism qoladi |
| Audio | belgilangan qismni **o'chirish** — qolgan qismlar birlashadi |
| Audio | bir nechta audioni **birlashtirish** |
| PDF | belgilangan sahifalarni **kesib olish** — yangi PDF |
| PDF | belgilangan sahifalarni **o'chirish** — yangi PDF |
| PDF | bir nechta PDF ni **birlashtirish** |
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

## Fayl tanlash

Fayl tanlash oynasi **to'g'ridan-to'g'ri kerakli papkadan** ochiladi:

- **Audio** — ilovaning o'z ro'yxati qurilmaning `Music` papkasidan boshlanadi
  (birinchi marta audio fayllarni o'qishga ruxsat so'raydi: Android 13 va
  undan yuqorida `READ_MEDIA_AUDIO`, quyida `READ_EXTERNAL_STORAGE`). Shu
  ekranda «Tizim oynasidan tanlash» tugmasi ham bor: Android'ning o'z
  tanlagichi ham `Music` papkasidan ochiladi (ruxsat kerak emas, SD karta
  uchun ham shu yo'l).
- **PDF** — faqat tizim tanlagichi, `Documents` papkasidan ochiladi. Android
  11 dan boshlab ilova boshqa ilovalar yaratgan PDF larni o'zi ko'ra olmaydi
  va buni hech qanday oddiy ruxsat o'zgartirmaydi.

Boshlang'ich papka — Android'ga «iloji boricha» ko'rsatma
(`EXTRA_INITIAL_URI`). Ba'zi qurilmalarda (masalan Samsung) tanlagich uni
e'tiborsiz qoldirishi mumkin; bunday holda oyna odatdagidek ochiladi.

Tizim oynasidan kelgan fayl ilovaning ish papkasiga **nusxalanadi** (asl fayl
o'zgarmaydi). Ilova ichidagi ro'yxatdan tanlangan fayl esa nusxalanmaydi va
**hech qachon o'chirilmaydi**: faqat ilovaning o'z vaqtinchalik fayllari o'chadi.

## Hujjat o'quvchilari (ekransiz)

`media/doc/` da PDF, DOCX, ODT, RTF, EPUB, FB2, HTML, PPTX, TXT o'quvchilari va
ularning testlari bor, lekin ularni ishlatadigan ekran (o'qish, audio-kitob)
hozirgi versiyada bosh ekranda yo'q. Kod saqlangan; ekranlar qaytarilmaguncha
ilovada ishlatilmaydi.

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

- `INTERNET` ruxsati yo'q — ilova internetga chiqmaydi.
- Ruxsatlar faqat shular: audio fayllarni **o'qish** (`READ_MEDIA_AUDIO`,
  Android 12 va pastda `READ_EXTERNAL_STORAGE`) — ilova ichidagi audio ro'yxati
  uchun; Android 9 va pastda natijani yozish uchun `WRITE_EXTERNAL_STORAGE`.
  Mikrofon, kontaktlar, joylashuv va bildirishnoma ruxsatlari yo'q. Tizim
  tanlagichi orqali olingan fayl ruxsat talab qilmaydi.
- Foydalanuvchining asl fayli hech qachon o'chirilmaydi: o'chirish faqat
  ilovaning kesh papkasidagi fayllarga ruxsat etilgan (`WorkStore.deleteIfOwned`).
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
  media/          WavFile, WavWriter, AudioTrimmer, AudioPlayer, WorkStore, FilePaths
    format/       import/eksport, qat'iy format qoidasi (StrictFormat, AudioOpener)
    merge/        AudioMerger
    dsp/          Resampler
    pdf/          PdfPageTools (PDFBox), PageRange
    doc/          hujjat o'quvchilari (hozir ekransiz), ReadingText, TextQuality
  ui/             home, trim (kesish/o'chirish), merge, pdf (kesish/o'chirish va
                  birlashtirish), log, nav, common (FolderPicker, OpenDocumentAt), theme
  util/           vaqt, son formati, Sharing, ResultFiles, MediaSaver, FolderOpener,
                  DeviceFiles, UriImport, RecentFiles
```

## Ma'lum cheklovlar

- OGG Vorbis va WMA audio ochilmaydi (yozib bo'lmaydi — qoida shuni talab qiladi).
- Mixed-format birlashtirish yo'q (MP3 + M4A): qaysi biriga o'xshatish noaniq.
- Birinchi PDF ochilganda PDFBox resurslarni yuklaydi — bir necha soniya.
- PDF ni yangi hujjatga ko'chirishda xatcho'plar, tegli tuzilma va shakl maydonlari saqlanmaydi.
- Ilova hali telefonda sinalmagan: qarang `docs/PROGRESS.md`, «Tekshiruv holati».
- WAV (ichki ish fayli) 4 GB gacha: 44,1 kHz stereo 16 bit uchun taxminan 6,7
  soat. Undan uzun yozuv ochilmaydi.
- Chastotasi har xil fayllarni birlashtirishda qayta namunalash ancha sekin
  (har kadr uchun filtr hisoblanadi): uzun fayllarda bir necha daqiqa ketishi mumkin.
- PDF faqat tizim tanlagichi orqali tanlanadi (Android 11+ da boshqa yo'l yo'q).
