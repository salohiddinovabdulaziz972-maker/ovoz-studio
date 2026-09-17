package uz.ovozstudio.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import uz.ovozstudio.app.settings.AppLanguage
import uz.ovozstudio.app.settings.AppSettingsStore
import uz.ovozstudio.app.settings.LocaleContext
import uz.ovozstudio.app.ui.nav.AppNav
import uz.ovozstudio.app.ui.theme.OvozStudioTheme

class MainActivity : ComponentActivity() {

    /**
     * Tanlangan tilni Activity kontekstiga qo'llaydi.
     *
     * Shu joy — yagona to'g'ri nuqta: `attachBaseContext` `onCreate` dan
     * oldin chaqiriladi, ya'ni ekran qurilishidan oldin resurslar til
     * bo'yicha tanlangan bo'ladi. Keyinroq qo'llansa, ekran bir lahza eski
     * tilda chizilib, keyin «sakrab» ketardi.
     *
     * O'qish muvaffaqiyatsiz bo'lsa — tizim tanlovi: ilova baribir ochilishi
     * kerak, sozlama fayli esa buning uchun sabab emas.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = runCatching {
            AppSettingsStore.inFiles(newBase.filesDir).load().language
        }.getOrDefault(AppLanguage.SYSTEM)
        super.attachBaseContext(LocaleContext.apply(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OvozStudioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav()
                }
            }
        }
    }
}
