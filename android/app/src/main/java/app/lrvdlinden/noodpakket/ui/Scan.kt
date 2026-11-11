package app.lrvdlinden.noodpakket.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.ExpiryDateParser
import app.lrvdlinden.noodpakket.data.KitItem
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState
import app.lrvdlinden.noodpakket.data.ProductLookup
import app.lrvdlinden.noodpakket.data.ScanResult
import app.lrvdlinden.noodpakket.data.ScannedCode
import app.lrvdlinden.noodpakket.data.ScannedProduct
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Opens Google's barcode scanner (no camera permission needed) and returns the raw value. */
fun scanBarcode(context: Context, onCode: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_CODE_128)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onCode) }
        .addOnFailureListener {
            Toast.makeText(context, context.getString(R.string.scan_unavailable), Toast.LENGTH_LONG).show()
        }
}

/** Takes a photo with the camera app and reads an expiry date from it with on-device text recognition. */
@Composable
fun rememberDateScanner(onDate: (LocalDate) -> Unit): () -> Unit {
    val context = LocalContext.current
    val uri = remember {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "date.jpg"))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        readDate(context, uri, onDate)
    }
    return { launcher.launch(uri) }
}

private fun readDate(context: Context, uri: Uri, onDate: (LocalDate) -> Unit) {
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull() ?: return
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
        .addOnSuccessListener { text ->
            val date = ExpiryDateParser.parse(text.text)
            if (date != null) onDate(date)
            else Toast.makeText(context, context.getString(R.string.date_not_found), Toast.LENGTH_LONG).show()
        }
}

/** After a scan: shows what was found, everything filled in, one tap to add it to the kit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(rawCode: String, state: KitState, repo: KitRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val code = remember(rawCode) { ScannedCode.parse(rawCode) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var targetKey by remember { mutableStateOf("") }       // catalog key, item id, or "" for a new item
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableDoubleStateOf(1.0) }
    var expires by remember { mutableStateOf<LocalDate?>(null) }
    val scanDate = rememberDateScanner { expires = it }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(code) {
        if (code == null) { onDismiss(); return@LaunchedEffect }
        val known = state.products.firstOrNull { it.barcode == code.gtin }
        val r = if (known != null) {
            ScanResult(code.gtin, known.name, null, known.imageUrl, known.catalogKey, known.amountPerUnit, code.expiry, false)
        } else ProductLookup.lookup(code)
        result = r
        name = r.name
        amount = r.amount
        expires = r.expiry
        targetKey = known?.itemId?.takeIf { id -> state.items.any { it.id == id } } ?: r.catalogKey ?: ""
    }

    val target: KitItem? = state.items.firstOrNull { it.catalogKey == targetKey || it.id == targetKey }
    val unit = target?.unit ?: app.lrvdlinden.noodpakket.data.KitUnit.PIECES
    val preview = target ?: KitItem(unit = unit)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val r = result
        if (r == null) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.scan_looking_up))
                }
            }
            return@ModalBottomSheet
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProductImage(r.imageUrl, 96.dp)
                Column(Modifier.weight(1f)) {
                    Text(r.name.ifBlank { stringResource(R.string.scan_unknown) }, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    r.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(r.barcode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(if (r.foundOnline) R.string.scan_source else R.string.scan_not_found),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            TargetPicker(state, targetKey) { targetKey = it }
            if (targetKey.isEmpty()) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan_amount))
                    Text(formatAmount(context, preview, amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalIconButton(onClick = { amount = (amount - unit.step).coerceAtLeast(unit.step) }) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.less))
                }
                FilledTonalIconButton(onClick = { amount += unit.step }) { Icon(Icons.Outlined.Add, stringResource(R.string.more)) }
            }
            target?.let {
                Text(stringResource(R.string.scan_after_adding,
                    formatAmount(context, it, it.quantity + amount), formatAmount(context, it, it.required(state.household))),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.has_expiry), Modifier.weight(1f))
                Switch(checked = expires != null, onCheckedChange = { on -> expires = if (on) LocalDate.now().plusYears(1) else null })
            }
            expires?.let {
                Text(stringResource(R.string.expires_on) + ": " + it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    fontWeight = FontWeight.Medium)
            }
            OutlinedButton(onClick = scanDate) {
                Icon(Icons.Outlined.CameraAlt, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.scan_date))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = targetKey.isNotEmpty() || name.isNotBlank(),
                    onClick = {
                        save(repo, state, r, target, name.trim(), amount, expires)
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.scan_add)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun save(repo: KitRepository, state: KitState, r: ScanResult, target: KitItem?, name: String, amount: Double, expires: LocalDate?) {
    repo.update { s ->
        val item = target ?: KitItem(customName = name, customRequired = amount.coerceAtLeast(1.0),
            sortOrder = (s.items.maxOfOrNull { it.sortOrder } ?: 0) + 1)
        val earliest = listOfNotNull(item.expiryDate, expires).minOrNull()
        val updated = item.copy(
            quantity = item.quantity + amount,
            expiresAt = earliest?.toString() ?: item.expiresAt,
            imageUrl = item.imageUrl ?: r.imageUrl,
            updatedAt = System.currentTimeMillis(),
        )
        val items = if (target == null) s.items + updated else s.items.map { if (it.id == updated.id) updated else it }
        val memory = ScannedProduct(
            barcode = r.barcode,
            name = if (target == null) name else r.name.ifBlank { name },
            catalogKey = updated.catalogKey,
            itemId = if (updated.catalogKey == null) updated.id else null,
            amountPerUnit = amount,
            imageUrl = r.imageUrl ?: state.products.firstOrNull { it.barcode == r.barcode }?.imageUrl,
        )
        s.copy(items = items, products = s.products.filterNot { it.barcode == r.barcode } + memory)
    }
}

@Composable
private fun TargetPicker(state: KitState, selected: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val options = listOf("" to stringResource(R.string.new_item)) +
        state.items.filter { !it.hidden }.sortedBy { it.sortOrder }.map { (it.catalogKey ?: it.id) to it.displayName(context) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.scan_add_to), Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(options.firstOrNull { it.first == selected }?.second ?: options.first().second)
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onPick(key); open = false })
                }
            }
        }
    }
}

/** Product photo from Open Food Facts, with a neutral placeholder. */
@Composable
fun ProductImage(url: String?, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(18.dp))
            .background(if (url != null) MaterialTheme.colorScheme.surfaceContainer else Brand.cream),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(6.dp).size(size))
        } else {
            ArtImage("other", Modifier.padding(10.dp).size(size))
        }
    }
}
