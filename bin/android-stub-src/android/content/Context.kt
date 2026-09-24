package android.content

import java.io.File

/** Sinov uchun `Context` ning minimal o'rnini bosuvchi. */
abstract class Context {
    abstract val packageName: String
    abstract val filesDir: File
    abstract val packageManager: PackageManager
    open val applicationContext: Context get() = this

    class PackageManager {
        class PackageInfo(val versionName: String?)
        fun getPackageInfo(name: String, flags: Int): PackageInfo = PackageInfo("test")
    }
}
