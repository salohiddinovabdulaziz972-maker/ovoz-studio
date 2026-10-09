package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Bir nechta variantdan bittasini tanlash — tezlik va balandlik uchun.
 *
 * Nima uchun slayder emas: ekran o'quvchi foydalanuvchisi slayderni aniq
 * nishonga olishi qiyin, qiymatni eshitib tushunishi esa yana qiyinroq.
 * Bir nechta tugma esa bittalab yuriladi, har birida aniq nom eshitiladi.
 *
 * Nima uchun bu alohida komponent: `ChoiceRow` ikkita variantga mo'ljallangan
 * va tanlovni almashtiradi. Bu yerda variantlar soni o'zgaruvchan (uchtadan
 * ko'p bo'lishi ham mumkin) va har birining o'z nomi bor.
 */
@Composable
fun A11yChoiceRow(
    label: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        description?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, (name, selected) ->
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(index) },
                    enabled = enabled,
                    label = { Text(text = name, style = MaterialTheme.typography.bodyMedium) },
                    // Chip ham barmoq uchun kamida 48 dp bo'lishi kerak.
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                        .semantics { role = Role.RadioButton },
                )
            }
        }
    }
}
