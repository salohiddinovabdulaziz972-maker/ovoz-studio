package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, (name, selected) ->
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(index) },
                    enabled = enabled,
                    label = { Text(text = name, style = MaterialTheme.typography.bodyMedium) },
                    // Chip ham barmoq uchun kamida 48 dp bo'lishi kerak.
                    //
                    // `defaultMinSize(minHeight = 48.dp)` ATAYLAB `fillMaxWidth()`
                    // bilan birga ishlatilmaydi: kenglikni teng bo'lishish uchun
                    // `Row` ichidagi har bir chip `weight(1f)` oladi. Avval
                    // `fillMaxWidth()` bor edi — u har bir chipni butun qator
                    // kengligiga cho'zib, uchtasini ustma-ust tashlab qo'yardi.
                    // Ko'rish bilan ishlaydigan foydalanuvchi buni darhol
                    // ko'radi, ekran o'quvchi foydalanuvchisi esa faqat
                    // tartibsiz ovoz eshitadi — farq sezilmasligi kerak.
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            role = Role.RadioButton
                            selected = selected
                        },
                )
            }
        }
    }
}
