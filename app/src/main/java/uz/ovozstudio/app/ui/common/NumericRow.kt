package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Bitta sozlama: yorliqli raqamli maydon va ± tugmalari.
 *
 * Ilova bo'ylab bitta qoida: qiymat **qo'lda kiritiladi**, sirg'anma bilan
 * emas — ekran o'quvchi sirg'anmani aniq qiymatga qo'yib bo'lmaydi.
 *
 * Maydonning yorlig'i — sozlamaning o'zi («62 Gerts», «Tezlik»). Shu tufayli
 * ekran o'quvchi maydonga o'tganda nima ustida turganini eshitadi, oldingi
 * matnni eslab qolishi shart emas.
 *
 * ± tugmalari kerak, chunki raqamli klaviaturada minus yo'q: ularsiz
 * pasaytirish umuman kiritilmasdi. Har ikkisining yorlig'i sozlama nomini
 * aytadi — yorliqsiz tugma bo'lmasligi qoidasi.
 */
@Composable
fun NumericRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onNudge: (Double) -> Unit,
    step: Double,
    decreaseDescription: String,
    increaseDescription: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                // Kasrli klaviatura: o'nlik ajratgichi bor, ya'ni «3,5» ni
                // to'g'ridan-to'g'ri kiritish mumkin.
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onNudge(-step) }, enabled = enabled) {
            Icon(
                // material-icons-core da «minus» ikonkasi yo'q; pastga
                // strelka pasaytirishni bildiradi.
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = decreaseDescription,
            )
        }
        IconButton(onClick = { onNudge(step) }, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = increaseDescription,
            )
        }
    }
}
