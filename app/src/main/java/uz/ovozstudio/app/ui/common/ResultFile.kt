package uz.ovozstudio.app.ui.common

/**
 * Tayyor natija: foydalanuvchiga ko'rsatiladigan va saqlanadigan yoki
 * ulashiladigan fayl.
 *
 * Kesish, birlashtirish va PDF ekranlarining hammasi natijani shu ko'rinishda
 * beradi — «Saqlash» va «Ulashish» tugmalari shu qiymat bilan ishlaydi.
 *
 * @property path fayl ilovaning vaqtinchalik papkasida turadi.
 * @property name foydalanuvchi ko'radigan nom (`ovoz-kesilgan.mp3`) —
 *   «Saqlash» oynasida taklif qilinadi.
 * @property mimeType fayl turi (`audio/mpeg`, `application/pdf`).
 */
data class ResultFile(
    val path: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
)
