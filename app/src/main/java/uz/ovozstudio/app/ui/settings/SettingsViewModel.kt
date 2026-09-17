package uz.ovozstudio.app.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uz.ovozstudio.app.settings.AppLanguage
import uz.ovozstudio.app.settings.AppSettings
import uz.ovozstudio.app.settings.AppSettingsStore
import uz.ovozstudio.app.settings.LanguageMatch
import uz.ovozstudio.app.settings.LocaleContext

/**
 * Sozlamalar ekranining mantiqi.
 *
 * Saqlash **sinxron** va shu sababli `suspend` emas: tilni almashtirish
 * Activity'ni qayta yaratadi, u esa `attachBaseContext` da faylni darhol
 * o'qiydi. Yozuv fonda qolsa, qayta ochilgan ekran eski tilni o'qib
 * qo'yardi (batafsil: [AppSettingsStore]).
 *
 * Shu sababli `setLanguage` **muvaffaqiyatni qaytaradi**: ekran faqat yozuv
 * haqiqatan o'tganda qayta ochiladi. Aks holda «tanladim, lekin hech narsa
 * o'zgarmadi» degan jimgina holat qolardi.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AppSettingsStore.inFiles(application.filesDir)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        val settings = runCatching { store.load() }.getOrDefault(AppSettings())
        _state.value = SettingsUiState(
            language = settings.language,
            // Qurilma tili bir marta, ekran ochilganda aniqlanadi: foydalanuvchi
            // shu ekranda turib qurilma tilini o'zgartira olmaydi.
            systemLanguage = LanguageMatch.resolve(LocaleContext.deviceTags()),
            simplified = settings.simplified,
            version = appVersion(),
        )
    }

    /** Tilni tanlaydi va saqlaydi. `true` — o'zgarish qo'llandi. */
    fun setLanguage(language: AppLanguage): Boolean {
        val current = _state.value
        if (language == current.language) return false
        val saved = save(AppSettings(language = language, simplified = current.simplified))
        if (!saved) return false
        _state.update { it.copy(language = language, error = null) }
        return true
    }

    /** Soddalashtirilgan rejimni yoqadi/o'chiradi. `true` — o'zgarish qo'llandi. */
    fun setSimplified(enabled: Boolean): Boolean {
        val current = _state.value
        if (enabled == current.simplified) return false
        val saved = save(AppSettings(language = current.language, simplified = enabled))
        if (!saved) return false
        _state.update { it.copy(simplified = enabled, error = null) }
        return true
    }

    /** Havolani ochib bo'lmaganda ekranga aytish uchun. */
    fun linkFailed() {
        _state.update { it.copy(error = SettingsError.LINK_FAILED) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun save(settings: AppSettings): Boolean {
        val result = runCatching { store.save(settings) }
        if (result.isFailure) {
            Log.w(TAG, "Sozlama saqlanmadi", result.exceptionOrNull())
            _state.update { it.copy(error = SettingsError.SAVE_FAILED) }
            return false
        }
        return true
    }

    /**
     * Versiya paketdan olinadi.
     *
     * `BuildConfig` ataylab ishlatilmaydi: AGP 8 da u sukut bo'yicha
     * o'chirilgan, ya'ni uni yoqish uchun build sozlamasini o'zgartirish
     * kerak bo'lardi. Paket ma'lumoti esa har doim joyida.
     */
    private fun appVersion(): String = runCatching {
        val app = getApplication<Application>()
        app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
