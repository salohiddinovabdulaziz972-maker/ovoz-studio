package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Yorliqli matn maydoni.
 *
 * Yorliq — maydonning o'zi (`label`), shuning uchun ekran o'quvchi maydonga
 * o'tganda nima ustida turganini eshitadi: «Nom», «Ijrochi». Oldingi matnni
 * eslab qolish talab qilinmaydi — bu ilova bo'ylab yagona qoida.
 *
 * [hint] — maydon ostidagi qo'shimcha izoh (masalan, «Masalan: 3 yoki 3/12»).
 * U **alohida** matn bo'lib turadi, maydon ichidagi placeholder emas:
 * placeholder yo'qolib ketadi va ekran o'quvchi uni o'qib ulgurmaydi.
 */
@Composable
fun TextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            // «Keyingi» tugmasi klaviaturani yopmaydi: foydalanuvchi
            // maydonlar orasida ketma-ket yura oladi.
            imeAction = ImeAction.Next,
        ),
        supportingText = if (hint == null) null else {
            { Text(text = hint) }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
