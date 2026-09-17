package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.R
import uz.ovozstudio.app.util.TimeParts

/**
 * Vaqt uchun to'rtta raqamli maydon. Har bir maydon o'z nomi bilan
 * o'qiladi — ekran o'quvchi foydalanuvchisi uchun yagona ishonchli usul.
 */
@Composable
fun TimeInput(
    label: String,
    parts: TimeParts,
    onPartsChange: (TimeParts) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val errorText = stringResource(R.string.time_input_invalid)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.a11yHeading(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimePartField(
                value = parts.hours,
                onValueChange = { onPartsChange(parts.copy(hours = it)) },
                suffix = stringResource(R.string.time_unit_hours),
                maxLength = 3,
                isError = isError,
                enabled = enabled,
                modifier = Modifier.width(72.dp),
            )
            TimePartField(
                value = parts.minutes,
                onValueChange = { onPartsChange(parts.copy(minutes = it)) },
                suffix = stringResource(R.string.time_unit_minutes),
                maxLength = 2,
                isError = isError,
                enabled = enabled,
                modifier = Modifier.width(72.dp),
            )
            TimePartField(
                value = parts.seconds,
                onValueChange = { onPartsChange(parts.copy(seconds = it)) },
                suffix = stringResource(R.string.time_unit_seconds),
                maxLength = 2,
                isError = isError,
                enabled = enabled,
                modifier = Modifier.width(72.dp),
            )
            TimePartField(
                value = parts.millis,
                onValueChange = { onPartsChange(parts.copy(millis = it)) },
                suffix = stringResource(R.string.time_unit_millis),
                maxLength = 3,
                isError = isError,
                enabled = enabled,
                modifier = Modifier.width(88.dp),
            )
        }

        if (isError) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TimePartField(
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    maxLength: Int,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // Faqat raqamlar va uzunlik chegarasi — noto'g'ri kiritishning oldini oladi.
            val digits = input.filter(Char::isDigit).take(maxLength)
            onValueChange(digits)
        },
        label = { Text(suffix) },
        singleLine = true,
        isError = isError,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
