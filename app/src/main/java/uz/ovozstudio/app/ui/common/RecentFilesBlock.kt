package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.R

/**
 * Oxirgi ochilgan fayllar va ular ichidan nom bo'yicha qidiruv.
 *
 * Nima uchun kerak: qurilmada papkalar chuqur, nomlari uzun. Ko'zi ojiz
 * foydalanuvchi uchun har safar papkalar ichida qo'lda yurish — eng
 * charchatadigan qism. Fayl nomi esda qolgan bo'lsa, shu yerdan bir
 * bosishda topiladi.
 *
 * Nima uchun qidiruv faqat shu ro'yxat ichida: butun qurilmani ko'rib chiqish
 * uchun alohida keng ruxsat (`MANAGE_EXTERNAL_STORAGE`) kerak, ilova esa uni
 * so'ramaydi — bunday ruxsat foydalanuvchidan juda ko'p narsani so'raydi.
 *
 * Nima uchun ro'yxatdagi tugma faylni to'g'ridan-to'g'ri ochmaydi: saqlangan
 * manzil (`content://…`) qurilma qayta ishga tushganda kuchini yo'qotadi.
 * Shuning uchun tugma tizim tanlagichini ochadi — fayl har safar tizim
 * orqali qayta tanlanadi, nom esa eslatma bo'lib qoladi.
 */
@Composable
fun RecentFilesBlock(
    files: List<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.a11yGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.pick_recent_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = stringResource(R.string.pick_recent_hint),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (files.isEmpty()) {
            Text(
                text = stringResource(R.string.pick_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            // Har bir nom alohida tugma: ekran o'quvchi foydalanuvchisi
            // ro'yxatni bittalab yurib, kerakli faylni tanlaydi.
            //
            // Tugma yorlig'i — faylning O'ZI, izohi esa «ochish» + ro'yxatdagi
            // o'rni. Ilgari yorliq «Ochish» bo'lgani uchun qaysi fayl
            // ochilishini eshitishdan bilib bo'lmasdi.
            val openLabel = stringResource(R.string.pick_recent_open)
            files.forEachIndexed { index, name ->
                A11yOutlinedButton(
                    label = name,
                    onClick = onPick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    description = "$openLabel — ${index + 1}/${files.size}",
                )
            }
            A11yOutlinedButton(
                label = stringResource(R.string.pick_clear_recent),
                onClick = onClear,
                enabled = enabled,
            )
        }

        Text(
            text = stringResource(R.string.pick_search_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = stringResource(R.string.pick_search_history_only),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.pick_search_label)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        if (files.isEmpty() && query.trim().isNotEmpty()) {
            val noneText = stringResource(R.string.pick_search_none)
            // Qidiruv natijasi ko'ruvchiga darhol ko'rinadi, eshitish bilan
            // esa faqat so'ralganda aytiladi — aks holda foydalanuvchi
            // filtrlash ishlaganini bilmay qolardi.
            val announce = rememberAnnouncer()
            LaunchedEffect(noneText) { announce(noneText) }
            Text(
                text = noneText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (files.isNotEmpty()) {
            val foundText = stringResource(R.string.pick_search_found, files.size)
            val announce = rememberAnnouncer()
            LaunchedEffect(foundText) { announce(foundText) }
            Text(
                text = foundText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
