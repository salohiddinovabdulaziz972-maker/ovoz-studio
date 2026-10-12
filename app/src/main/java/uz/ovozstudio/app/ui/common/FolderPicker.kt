package uz.ovozstudio.app.ui.common

import android.content.Context
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.R
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.util.DeviceFiles
import java.io.File

/**
 * Audio yoki PDF faylni tanlash oynasi — ilovaning o'z oynasi.
 *
 * Nima uchun tizim tanlagichi emas. Tizim oynasida fayl ochilsa, u
 * `content://` manzili bilan beriladi va faqat shu ilova ichida yashaydi:
 * qurilma qayta yoqilganda yo'l uziladi, faylni boshqa ilovada ochib
 * bo'lmaydi, faylning qayerdaligini esa umuman ko'rsatib bo'lmaydi.
 * Shuning uchun fayl nusxalanmaydi — [DeviceFiles] qurilmaning o'z
 * papkasidan to'g'ridan-to'g'ri, haqiqiy yo'l bilan o'qiydi. Shunda
 * foydalanuvchi o'z papkasi bilan ishlaydi va tanlagan fayli shu yerda
 * qoladi.
 *
 * Nima uchun `ListView`, Compose emas. Bu — mavzu emas, ro'yxat: unda
 * TalkBack uchun muhim bo'lgan narsa (qator balandligi, tanlangan qatorning
 * holati) tizim tomonidan o'zi to'g'ri beriladi. Bundan tashqari papkada
 * minglab fayl bo'lishi mumkin — `ListView` faqat ko'ringan qatorlarni
 * yasaydi, Compose ro'yxati esa hammasini birdan o'lchashga urinadi.
 *
 * Faqat ichki xotira ko'rsatiladi. SD karta va boshqa bo'limlar uchun
 * alohida ruxsat yo'li kerak — ular hozircha ochilmagan.
 */
@Composable
fun FolderPicker(
    isAudio: Boolean,
    onPick: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val announce = rememberAnnouncer()

    val root = remember(isAudio) { DeviceFiles.root(isAudio) }
    var folder by remember(isAudio) { mutableStateOf(root) }
    var entries by remember(isAudio) { mutableStateOf<List<DeviceFiles.Entry>>(emptyList()) }
    var isLoading by remember(isAudio) { mutableStateOf(true) }

    // Matnlar `reload` dan oldin hisoblanadi: `stringResource` faqat
    // @Composable kontekstda chaqiriladi, `reload` esa oddiy funksiya.
    val busyMessage = stringResource(R.string.picker_loading)
    val emptyMessage = stringResource(R.string.picker_empty)
    val noAccessMessage = stringResource(R.string.picker_no_access)
    val topMessage = stringResource(R.string.picker_at_top)

    // Ruxsat berilmagan bo'lsa: tizim so'rovi, keyin qayta o'qish.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        scope.launch { reload(folder) }
    }

    // Ro'yxat fon oqimida o'qiladi: papkada minglab fayl bo'lishi mumkin va
    // asosiy oqimda o'qish ekranni qotirib qo'yardi.
    suspend fun reload(target: File) {
        folder = target
        isLoading = true
        entries = read(context, target, announce)
        isLoading = false
        // Har yangi papkada joriy joy aytiladi: ko'ruvchi foydalanuvchi
        // qatorning joyidan buni biladi, eshitish bilan esa faqat shu
        // e'lon orqali bilinadi.
        announce(
            context.getString(
                R.string.picker_here,
                DeviceFiles.displayPath(target),
                entries.count { !it.isUp && it.isDirectory },
            ),
        )
    }

    // Papkaga kirish va yuqoriga chiqish — ikkalasi ham fonda o'qiydigan
    // `reload` ni chaqiradi.
    val openFolder: (File) -> Unit = { target -> scope.launch { reload(target) } }
    val up: () -> Unit = {
        val parent = folder.parentFile
        if (parent != null && folder != root) {
            scope.launch { reload(parent) }
        } else {
            announce(topMessage)
        }
    }

    // Har ochilishda amaldagi holatdan boshlanadi: tugma bosilganda
    // foydalanuvchi kutayotgan joy shu.
    LaunchedEffect(Unit) {
        val needed = DeviceFiles.neededPermission(isAudio)
        if (needed != null && !DeviceFiles.hasAccess(context, isAudio)) {
            permission.launch(needed)
        } else {
            reload(root)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                if (isAudio) R.string.picker_title_audio else R.string.picker_title_pdf,
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = DeviceFiles.displayPath(folder),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!DeviceFiles.hasAccess(context, isAudio) && !isLoading) {
            Text(text = noAccessMessage, style = MaterialTheme.typography.bodyMedium)
        } else if (isLoading) {
            Text(text = busyMessage, style = MaterialTheme.typography.bodyMedium)
        } else {
            if (entries.isEmpty()) {
                Text(text = emptyMessage, style = MaterialTheme.typography.bodyMedium)
            }
            // Ro'yxat adapteri `remember` ichida saqlanadi: `update` har
            // qayta chizishda chaqiriladi va har safar yangi adapter yasalsa,
            // ro'yxat joyi (scroll) yo'qolardi — ekran o'quvchi foydalanuvchi
            // uchun bu ayniqsa sezilarli.
            val adapter = remember { EntryAdapter(context, onOpenFolder = openFolder, onPickFile = onPick) }
            AndroidView(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                factory = { viewContext ->
                    ListView(viewContext).apply {
                        dividerHeight = 1
                        this.adapter = adapter
                    }
                },
                update = { list ->
                    list.adapter = adapter
                    adapter.submit(entries, folder)
                },
            )
        }

        A11yOutlinedButton(
            label = stringResource(R.string.picker_up),
            onClick = up,
            modifier = Modifier.fillMaxWidth(),
        )
        A11yButton(
            label = stringResource(R.string.common_close),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Ro'yxat adapteri: har qator — papka yoki fayl.
 *
 * Qator balandligi 56 dp: ekran o'quvchi foydalanuvchisi ham, barmog'i bilan
 * bosadigan foydalanuvchi ham bir xil qulaylikda bosadi (48 dp — eng kichik
 * me'yor).
 */
private class EntryAdapter(
    private val context: Context,
    private val onOpenFolder: (File) -> Unit,
    private val onPickFile: (File) -> Unit,
) : BaseAdapter() {

    private var items: List<DeviceFiles.Entry> = emptyList()
    private var folder: File? = null

    fun submit(entries: List<DeviceFiles.Entry>, current: File) {
        items = entries
        folder = current
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): DeviceFiles.Entry = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        val entry = items[position]
        val text1 = view.findViewById<TextView>(android.R.id.text1)
        val text2 = view.findViewById<TextView>(android.R.id.text2)

        view.minimumHeight = ROW_HEIGHT_DP * (context.resources.displayMetrics.density).toInt()

        if (entry.isUp) {
            text1.text = ".."
            text2.text = context.getString(R.string.picker_up)
            // «..» — tizim belgisi; ekran o'quvchi uni «nuqta nuqta» deb
            // o'qib qo'ymasligi uchun nom ovozda aniq aytiladi.
            text1.contentDescription = context.getString(R.string.picker_up)
            text2.text = DeviceFiles.displayPath(folder?.parentFile ?: folder ?: File("/"))
        } else {
            text1.text = entry.name
            text1.contentDescription = when {
                entry.isDirectory -> context.getString(R.string.picker_folder, entry.name)
                else -> entry.name
            }
            text2.text = when {
                entry.isDirectory -> context.getString(R.string.picker_folder_hint)
                else -> DeviceFiles.readableSize(entry.length)
            }
        }

        val icon = view.findViewById<ImageView>(android.R.id.icon)
        icon?.visibility = View.GONE
        view.setOnClickListener {
            if (entry.isUp) {
                folder?.parentFile?.let(onOpenFolder)
            } else if (entry.isDirectory) {
                onOpenFolder(entry.file)
            } else {
                onPickFile(entry.file)
            }
        }
        return view
    }

    private companion object {
        const val ROW_HEIGHT_DP = 56
    }
}

/**
 * Papkani fon oqimida o'qiydi. Muvaffaqiyatsizlik jim qolmaydi: sabab
 * jurnalga tushadi va ekranda ko'rinadi — aks holda bo'sh ro'yxat «papka
 * bo'sh» degan noto'g'ri taassurot qoldirardi.
 */
private suspend fun read(
    context: Context,
    folder: File,
    announce: (String) -> Unit,
): List<DeviceFiles.Entry> = withContext(Dispatchers.IO) {
    runCatching { DeviceFiles.list(context, folder) }
        .onFailure { ErrorLog.error("papka.ro'yxat", "Papka o'qilmadi: ${folder.name}", it) }
        .getOrElse { emptyList() }
}

/**
 * Tanlangan `content://` manzilni qurilmadagi haqiqiy faylga aylantiradi.
 *
 * Zaxira yo'li: eski, saqlangan manzillar uchun. Yangi tanlov [FolderPicker]
 * orqali keladi va u to'g'ridan-to'g'ri `File` beradi — buni ishlatish shart
 * emas, lekin eski yozuvlar uchun qoladi.
 */
internal object ContentFile {

    /** `content://` manzil ochadigan `File`; topilmasa `null`. */
    fun of(context: Context, uri: android.net.Uri): File? = runCatching {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val path = cursor.getString(0) ?: return@use null
            val file = File(path)
            if (file.exists()) file else null
        }
    }.getOrNull()
}
