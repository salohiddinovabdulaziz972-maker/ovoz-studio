package android.util

/** Sinov uchun Logcat o'rnini bosuvchi: hech narsa qilmaydi. */
object Log {
    @JvmStatic fun i(tag: String, msg: String): Int = 0
    @JvmStatic fun e(tag: String, msg: String): Int = 0
    @JvmStatic fun e(tag: String, msg: String, error: Throwable?): Int = 0
}
