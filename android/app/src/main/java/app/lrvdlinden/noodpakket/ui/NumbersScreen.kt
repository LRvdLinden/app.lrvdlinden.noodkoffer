package app.lrvdlinden.noodpakket.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.ContactRole
import app.lrvdlinden.noodpakket.data.EmergencyPlan
import app.lrvdlinden.noodpakket.data.LocalInfo
import app.lrvdlinden.noodpakket.data.LocalLookup
import app.lrvdlinden.noodpakket.data.NationalNumber
import app.lrvdlinden.noodpakket.data.PlanContact
import kotlinx.coroutines.launch

fun ContactRole.icon(): ImageVector = when (this) {
    ContactRole.gp -> Icons.Filled.MedicalServices
    ContactRole.gpOutOfHours -> Icons.Filled.LocalHospital
    ContactRole.dentist -> Icons.Filled.SentimentSatisfied
    ContactRole.pharmacy -> Icons.Filled.LocalPharmacy
    ContactRole.family -> Icons.Filled.FamilyRestroom
    ContactRole.neighbours -> Icons.Filled.House
    ContactRole.work -> Icons.Filled.Work
    ContactRole.school -> Icons.Filled.School
    ContactRole.vet -> Icons.Filled.Pets
    ContactRole.insurance -> Icons.Filled.Description
    ContactRole.other -> Icons.Filled.Person
}

private fun NationalNumber.icon(): ImageVector = when (this) {
    NationalNumber.POLICE -> Icons.Filled.DirectionsCar
    NationalNumber.FIRE -> Icons.Filled.LocalFireDepartment
    NationalNumber.POWER -> Icons.Filled.Bolt
    NationalNumber.CRIME -> Icons.Filled.VisibilityOff
    NationalNumber.FRAUD -> Icons.Filled.Security
    NationalNumber.SUICIDE -> Icons.Filled.Favorite
    NationalNumber.ANIMAL -> Icons.Filled.Pets
}

fun dial(context: Context, number: String) {
    val digits = number.filter { it.isDigit() || it == '+' }
    if (digits.isNotEmpty()) runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
}

private fun openUrl(context: Context, url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

/** Important numbers: 112, your place, your own contacts and the national numbers, plus the card for the meter cupboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumbersScreen(plan: EmergencyPlan, onPlan: ((EmergencyPlan) -> EmergencyPlan) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var editing by remember { mutableStateOf<PlanContact?>(null) }
    var changingPlace by remember { mutableStateOf(false) }
    var waterMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.numbers_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = brandTopBarColors(),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 112
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brand.yellow).clickable { dial(context, "112") }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("112", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Brand.purple)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nr_112), fontWeight = FontWeight.Bold, color = Brand.purple)
                    Text(stringResource(R.string.nr_112_note), style = MaterialTheme.typography.bodySmall, color = Brand.purple.copy(alpha = 0.75f))
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Call, stringResource(R.string.call_112), tint = Brand.purple)
                }
            }

            SectionHeader(stringResource(R.string.numbers_near_you), Modifier.padding(start = 0.dp))
            val place = plan.place
            if (place == null || changingPlace) {
                Card { Box(Modifier.padding(16.dp)) {
                    PostcodeCard(initial = place?.postcode.orEmpty(), dark = false) { info ->
                        val keep = place?.waterKeuze?.takeIf { id -> info.water.any { it.id == id } }
                        onPlan { it.copy(place = info.copy(waterKeuze = keep)) }
                        changingPlace = false
                    }
                } }
            } else {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("${place.woonplaats} · ${place.postcode}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { changingPlace = true }) { Text(stringResource(R.string.change)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val g = place.gemeente
                            if (g.telefoon != null) {
                                Tile(stringResource(R.string.nr_gemeente, g.naam), g.telefoon, Icons.Filled.AccountBalance,
                                    stringResource(R.string.nr_gemeente_note), Modifier.weight(1f)) { dial(context, g.telefoon) }
                            } else if (g.site != null) {
                                Tile(stringResource(R.string.nr_gemeente, g.naam), stringResource(R.string.website), Icons.Filled.AccountBalance,
                                    null, Modifier.weight(1f), link = true) { openUrl(context, g.site) }
                            }
                            place.chosenWater?.let { w ->
                                if (w.telefoon != null) {
                                    Tile(stringResource(R.string.nr_water, w.naam), w.telefoon, Icons.Filled.WaterDrop, w.toelichting,
                                        Modifier.weight(1f)) { dial(context, w.telefoon) }
                                } else if (w.site != null) {
                                    Tile(stringResource(R.string.nr_water, w.naam), stringResource(R.string.website), Icons.Filled.WaterDrop,
                                        w.toelichting, Modifier.weight(1f), link = true) { openUrl(context, w.site) }
                                }
                            }
                        }
                        place.wijkagent?.let { url ->
                            Tile(stringResource(R.string.nr_wijkagent), "0900-8844", Icons.Filled.Security,
                                stringResource(R.string.nr_wijkagent_note), Modifier.fillMaxWidth(), link = true) { openUrl(context, url) }
                        }
                        place.noodzender?.let { BroadcasterCard(it) }
                        if (place.water.size > 1) {
                            Box {
                                TextButton(onClick = { waterMenu = true }) { Text(stringResource(R.string.water_other)) }
                                DropdownMenu(expanded = waterMenu, onDismissRequest = { waterMenu = false }) {
                                    place.water.forEach { w ->
                                        DropdownMenuItem(
                                            text = { Text(if (w.id == place.chosenWater?.id) "✓ ${w.naam}" else w.naam) },
                                            onClick = { waterMenu = false; onPlan { it.copy(place = place.copy(waterKeuze = w.id)) } },
                                        )
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.water_check), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionHeader(stringResource(R.string.numbers_own), Modifier.padding(start = 0.dp))
            Card {
                plan.contactList.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp))
                    Row(Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Brand.yellow), contentAlignment = Alignment.Center) {
                            Icon(c.role.icon(), null, tint = Brand.purple, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(c.role.title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(c.name.ifBlank { c.phone }, fontWeight = FontWeight.SemiBold)
                            if (c.name.isNotBlank() && c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.phone.isNotBlank()) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(okColor()).clickable { dial(context, c.phone) },
                                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
                if (plan.contactList.isNotEmpty()) HorizontalDivider(Modifier.padding(start = 14.dp))
                Row(Modifier.fillMaxWidth().clickable {
                    val used = plan.contactList.map { it.role }.toSet()
                    val role = listOf(ContactRole.gp, ContactRole.gpOutOfHours, ContactRole.family, ContactRole.neighbours,
                        ContactRole.pharmacy, ContactRole.dentist).firstOrNull { it !in used } ?: ContactRole.other
                    editing = PlanContact(role = role)
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.contact_add), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (plan.contactList.isEmpty()) Text(stringResource(R.string.contact_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader(stringResource(R.string.numbers_national), Modifier.padding(start = 0.dp))
            NationalNumber.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { n ->
                        Tile(stringResource(n.title), n.number, n.icon(), stringResource(n.note), Modifier.weight(1f)) { dial(context, n.number) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Button(onClick = { sharePlanPdf(context, plan, includePlan = false) }, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.purple, contentColor = Color.White)) {
                Icon(Icons.Outlined.Print, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.card_make), fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.card_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { c ->
        ContactDialog(c, onDismiss = { editing = null }) { updated ->
            onPlan { p ->
                val list = p.contactList.toMutableList()
                if (updated == null) list.removeAll { it.id == c.id }
                else { val i = list.indexOfFirst { it.id == updated.id }; if (i >= 0) list[i] = updated else list += updated }
                p.copy(contactList = list)
            }
            editing = null
        }
    }
}

@Composable
private fun Tile(title: String, value: String, icon: ImageVector, note: String?, modifier: Modifier = Modifier, link: Boolean = false,
                 onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(14.dp).heightIn(min = 104.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Brand.yellow), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Brand.purple, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(if (link) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Filled.Call, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp))
        }
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (!note.isNullOrBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BroadcasterCard(z: LocalInfo.Zender) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Brand.purple, Brand.purpleDeep))).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Radio, null, tint = Brand.yellow)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.nr_radio).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Text(z.omroep, fontWeight = FontWeight.Bold, color = Color.White)
            z.fm.firstOrNull()?.let {
                Text("FM ${it.mhz}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Brand.yellow)
                Text(it.gebied, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
            z.dab?.let { Text("DAB+ $it", fontWeight = FontWeight.SemiBold, color = Color.White) }
            if (z.fm.size > 1) Text(z.fm.drop(1).joinToString(" · ") { "FM ${it.mhz} ${it.gebied}" },
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
            Text(stringResource(R.string.radio_tune), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

/** Postcode lookup, used in the plan and in the welcome screens. */
@Composable
fun PostcodeCard(initial: String = "", dark: Boolean, onFound: (LocalInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    var postcode by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    fun find() {
        scope.launch {
            busy = true; error = null
            try { onFound(LocalLookup.lookup(postcode)) } catch (e: LocalLookup.Failure) {
                error = when (e.code) { "bad_postcode" -> R.string.postcode_bad; "unknown_postcode" -> R.string.postcode_unknown; else -> R.string.acct_err_network }
            } catch (e: Exception) { error = R.string.acct_err_network }
            busy = false
        }
    }
    val sub = if (dark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.postcode_intro), style = MaterialTheme.typography.bodyMedium, color = sub)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                postcode, { postcode = it.take(7) }, label = { Text(stringResource(R.string.postcode)) }, placeholder = { Text("1234 AB") },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (LocalLookup.clean(postcode) != null) find() }),
            )
            Button(onClick = { find() }, enabled = !busy && LocalLookup.clean(postcode) != null,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.yellow, contentColor = Brand.purple)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.purple)
                else Text(stringResource(R.string.postcode_lookup), fontWeight = FontWeight.Bold)
            }
        }
        error?.let { Text(stringResource(it), color = if (dark) Brand.yellow else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.postcode_privacy), style = MaterialTheme.typography.labelSmall, color = sub)
    }
}

@Composable
private fun ContactDialog(start: PlanContact, onDismiss: () -> Unit, onDone: (PlanContact?) -> Unit) {
    var c by remember { mutableStateOf(start) }
    var roleMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    TextButton(onClick = { roleMenu = true }) {
                        Icon(c.role.icon(), null); Spacer(Modifier.width(8.dp)); Text(stringResource(c.role.title))
                    }
                    DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                        ContactRole.entries.forEach { r ->
                            DropdownMenuItem(text = { Text(stringResource(r.title)) }, leadingIcon = { Icon(r.icon(), null) },
                                onClick = { c = c.copy(role = r); roleMenu = false })
                        }
                    }
                }
                OutlinedTextField(c.name, { c = c.copy(name = it) }, label = { Text(stringResource(R.string.acct_name)) }, singleLine = true)
                OutlinedTextField(c.phone, { c = c.copy(phone = it) }, label = { Text(stringResource(R.string.contact_phone)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(c.note, { c = c.copy(note = it) }, label = { Text(stringResource(R.string.contact_note)) })
                TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.contact_delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(c) }, enabled = c.name.isNotBlank() || c.phone.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Draws the card for the meter cupboard on an A4 PDF page (595 × 842 points). */
fun drawNumbersPage(context: Context, canvas: Canvas, plan: EmergencyPlan) {
    val purple = 0xFF3C175B.toInt(); val yellow = 0xFFF4B943.toInt(); val cream = 0xFFFCF2DB.toInt(); val grey = 0xFF6B7280.toInt()
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fun text(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    fun fit(s: String, p: TextPaint, w: Float): String { var t = s; while (t.length > 1 && p.measureText(t) > w) t = t.dropLast(2) + "…"; return t }
    val place = plan.place
    val m = 30f

    // header
    fill.color = purple; canvas.drawRect(0f, 0f, 595f, 92f, fill)
    canvas.drawText(context.getString(R.string.numbers_title), m, 46f, text(24f, 0xFFFFFFFF.toInt(), true))
    canvas.drawText(place?.let { "${it.woonplaats} · ${it.postcode}" } ?: context.getString(R.string.card_hang), m, 66f,
        text(11f, 0xCCFFFFFF.toInt()))
    fill.color = yellow; canvas.drawRoundRect(RectF(475f, 20f, 565f, 72f), 12f, 12f, fill)
    canvas.drawText(context.getString(R.string.nr_112).uppercase(), 487f, 36f, text(8f, purple, true))
    canvas.drawText("112", 487f, 66f, text(28f, purple, true))

    // boxes: municipality, water, national numbers
    val boxes = mutableListOf<Pair<String, String>>()
    place?.gemeente?.let { g -> g.telefoon?.let { boxes += context.getString(R.string.nr_gemeente, g.naam) to it } }
    place?.chosenWater?.let { w -> boxes += context.getString(R.string.nr_water, w.naam) to (w.telefoon ?: w.site.orEmpty()) }
    NationalNumber.entries.forEach { boxes += context.getString(it.title) to it.number }
    val colW = (595f - 2 * m - 20f) / 3f
    var y = 110f
    boxes.chunked(3).forEach { row ->
        row.forEachIndexed { i, (title, nr) ->
            val x = m + i * (colW + 10f)
            fill.color = cream; canvas.drawRoundRect(RectF(x, y, x + colW, y + 52f), 10f, 10f, fill)
            canvas.drawText(fit(title, text(8.5f, purple, true), colW - 20f), x + 10f, y + 18f, text(8.5f, purple, true))
            canvas.drawText(fit(nr, text(14f, 0xFF000000.toInt(), true), colW - 20f), x + 10f, y + 40f, text(14f, 0xFF000000.toInt(), true))
        }
        y += 62f
    }

    // broadcaster
    place?.noodzender?.let { z ->
        fill.color = purple; canvas.drawRoundRect(RectF(m, y, 595f - m, y + 66f), 12f, 12f, fill)
        canvas.drawText(context.getString(R.string.nr_radio).uppercase(), m + 14f, y + 18f, text(8f, 0xBFFFFFFF.toInt(), true))
        canvas.drawText(z.omroep, m + 14f, y + 35f, text(13f, 0xFFFFFFFF.toInt(), true))
        val detail = z.fm.joinToString(" · ") { "FM ${it.mhz} ${it.gebied}" } + (z.dab?.let { " · DAB+ $it" } ?: "")
        canvas.drawText(fit(detail, text(9f, 0xD9FFFFFF.toInt()), 380f), m + 14f, y + 52f, text(9f, 0xD9FFFFFF.toInt()))
        z.fm.firstOrNull()?.let { canvas.drawText("FM ${it.mhz}", 595f - m - 120f, y + 44f, text(24f, yellow, true)) }
        y += 80f
    }

    // own contacts with lines
    canvas.drawText(context.getString(R.string.numbers_own).uppercase(), m, y + 8f, text(10f, grey, true))
    y += 18f
    val contacts = plan.contactList
    val rows = contacts.map { Triple(context.getString(it.role.title), it.name, it.phone) } +
        listOf(ContactRole.gp, ContactRole.gpOutOfHours, ContactRole.family, ContactRole.neighbours, ContactRole.pharmacy, ContactRole.dentist)
            .filter { r -> contacts.none { it.role == r } }.map { Triple(context.getString(it.title), "", "") }
    val line = Paint().apply { color = 0x59808080; strokeWidth = 0.6f }
    rows.take(10).forEach { (role, name, phone) ->
        y += 22f
        canvas.drawText(fit(role, text(10f, purple, true), 120f), m, y, text(10f, purple, true))
        canvas.drawText(fit(name, text(11f, 0xFF000000.toInt()), 260f), m + 130f, y, text(11f, 0xFF000000.toInt()))
        val p = text(12f, 0xFF000000.toInt(), true)
        canvas.drawText(phone, 595f - m - p.measureText(phone), y, p)
        canvas.drawLine(m, y + 6f, 595f - m, y + 6f, line)
    }

    val foot = StaticLayout.Builder.obtain(context.getString(R.string.card_footer), 0, context.getString(R.string.card_footer).length,
        text(8f, grey), (595f - 2 * m).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    canvas.save(); canvas.translate(m, 842f - 22f - foot.height); foot.draw(canvas); canvas.restore()
}
