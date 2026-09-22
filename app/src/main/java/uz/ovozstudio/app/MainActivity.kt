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
import uz.ovozstudio.app.settings.LocaleContext
import uz.ovozstudio.app.ui.nav.AppNav
import uz.ovozstudio.app.ui.theme.OvozStudioTheme

class MainActivity : ComponentActivity() {

    /**
     * Ilova tilini tizim tilidan aniqlab, Activity kontekstiga qo'llaydi.
     *
     * Shu joy — yagona to'g'ri nuqta: `attachBaseContext` `onCreate` dan
     * oldin chaqiriladi, ya'ni ekran qurilishidan oldin resurslar til
     * bo'yicha tanlangan bo'ladi. Keyinroq qo'llansa, ekran bir lahza eski
     * tilda chizilib, keyin «sakrab» ketardi.
     *
     * Til aniqlanmasa (kutilmagan xato) — kontekst o'zgarishsiz qoladi:
     * ilova baribir ochilishi kerak, til esa buning uchun sabab emas.
     */
    override fun attachBaseContext(newBase: Context) {
        val localized = runCatching { LocaleContext.followSystem(newBase) }.getOrDefault(newBase)
        super.attachBaseContext(localized)
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
