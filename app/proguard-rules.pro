# Standart yig'ishda minify o'chirilgan. `-Povoz.fastRelease=true` bilan
# (tez reliz) R8 yoqiladi va shu qoidalar ishlatiladi.
-keep class uz.ovozstudio.app.** { *; }

# --- jump3r (MP3 kodlovchisi) ---
# Kutubxonaning `mp3` va `mpg` paketlari ishlatiladi.
# `de.sciss.jump3r.lowlevel` o'rami esa `javax.sound.sampled` ga tayanadi —
# Android'da u yo'q, shuning uchun o'ram butunlay chiqarib tashlanadi.
# Aks holda yig'uvchi mavjud bo'lmagan sinflarga havola qolib ketadi.
-dontwarn de.sciss.jump3r.lowlevel.**
-dontwarn javax.sound.**
-dontwarn de.sciss.jump3r.**
-keep class de.sciss.jump3r.mp3.** { *; }
-keep class de.sciss.jump3r.mpg.** { *; }

# --- pdfbox-android (PDF sahifalari va matni) ---
# Kutubxona ixtiyoriy bog'liqliklarga (JPEG2000, BouncyCastle) havola qiladi;
# ular ilovada yo'q va kerak ham emas (rasm chizilmaydi, faqat sahifalar
# ko'chiriladi va matn olinadi). Aks holda R8 «Missing class» bilan to'xtaydi.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.commons.logging.**
-keep class com.tom_roush.pdfbox.** { *; }
