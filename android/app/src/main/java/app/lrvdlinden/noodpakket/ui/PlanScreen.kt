package app.lrvdlinden.noodpakket.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Call
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.EmergencyPlan
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The household emergency plan, available offline and as a printable PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(state: KitState, repo: KitRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val plan = state.plan
    var pickingDate by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf<InfoTopic?>(null) }
    var numbersOpen by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    fun set(transform: (EmergencyPlan) -> EmergencyPlan) = repo.update { it.copy(plan = transform(it.plan)) }

    if (numbersOpen) {
        NumbersScreen(plan, onPlan = { t -> set(t) }, onBack = { numbersOpen = false }, modifier = modifier)
        return
    }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.title_plan)) }, scrollBehavior = scroll, colors = brandTopBarColors()) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).clickable { numbersOpen = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brand.yellow), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Call, null, tint = Brand.purple)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.numbers_title), fontWeight = FontWeight.Bold)
                    val parts = mutableListOf<String>()
                    plan.place?.let { pl -> parts += pl.woonplaats; pl.noodzender?.fm?.firstOrNull()?.let { parts += "FM ${it.mhz}" } }
                        ?: run { parts += stringResource(R.string.numbers_add_postcode) }
                    parts += if (plan.contactList.isEmpty()) stringResource(R.string.numbers_no_contacts)
                        else stringResource(R.string.numbers_contacts_count, plan.contactList.size)
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeader(stringResource(R.string.info_title), Modifier.padding(start = 0.dp))
            Card {
                InfoTopic.entries.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                    Row(Modifier.fillMaxWidth().clickable { topic = t }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(t.title))
                    }
                }
            }
            Text(stringResource(R.string.plan_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlanField(R.string.plan_meeting, R.string.plan_meeting_hint, plan.meetingPoint) { v -> set { it.copy(meetingPoint = v) } }
            PlanField(R.string.plan_contacts, R.string.plan_contacts_hint, plan.contacts) { v -> set { it.copy(contacts = v) } }
            PlanField(R.string.plan_arrangements, R.string.plan_arrangements_hint, plan.arrangements) { v -> set { it.copy(arrangements = v) } }
            PlanField(R.string.plan_medical, R.string.plan_medical_hint, plan.medical) { v -> set { it.copy(medical = v) } }
            PlanField(R.string.plan_evacuation, R.string.plan_evacuation_hint, plan.evacuation) { v -> set { it.copy(evacuation = v) } }
            PlanField(R.string.plan_radio, R.string.plan_radio_hint, plan.radioStation) { v -> set { it.copy(radioStation = v) } }
            PlanField(R.string.plan_utilities, R.string.plan_utilities_hint, plan.utilities) { v -> set { it.copy(utilities = v) } }
            PlanField(R.string.plan_notes, R.string.plan_notes_hint, plan.notes) { v -> set { it.copy(notes = v) } }

            SectionHeader(stringResource(R.string.plan_check_section), Modifier.padding(start = 0.dp))
            Text(stringResource(R.string.plan_interval))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(1 to R.string.interval_1, 3 to R.string.interval_3, 6 to R.string.interval_6, 12 to R.string.interval_12)
                options.forEachIndexed { i, (months, label) ->
                    SegmentedButton(selected = plan.checkIntervalMonths == months,
                        onClick = { set { it.copy(checkIntervalMonths = months).withNextCheck() } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(stringResource(label)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_next_check), Modifier.weight(1f))
                TextButton(onClick = { pickingDate = true }) {
                    Text(plan.nextCheck?.let { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) } ?: "–")
                }
            }
            Text(stringResource(R.string.plan_check_footer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(onClick = { sharePlanPdf(context, plan) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Print, null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.plan_pdf))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    topic?.let { InfoDialog(it) { topic = null } }
    if (pickingDate) {
        val initial = (plan.nextCheck?.let { LocalDate.parse(it) } ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        set { it.copy(nextCheck = date.toString()) }
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun PlanField(title: Int, hint: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(title)) },
        placeholder = { Text(stringResource(hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
    )
}

/** Renders the card with important numbers (and, with [includePlan], the plan) to an A4 PDF and opens the share sheet. */
fun sharePlanPdf(context: Context, plan: EmergencyPlan, includePlan: Boolean = true) {
    val doc = PdfDocument()
    val card = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    drawNumbersPage(context, card.canvas, plan)
    doc.finishPage(card)
    if (includePlan) drawPlanPage(context, doc, plan)
    val name = context.getString(if (includePlan) R.string.title_plan else R.string.numbers_title)
    val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
    val file = File(dir, "$name.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.plan_pdf)))
}

private fun drawPlanPage(context: Context, doc: PdfDocument, plan: EmergencyPlan) {
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
    val canvas = page.canvas
    val margin = 48f
    val width = (595 - 2 * margin).toInt()
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF121726.toInt() }
    val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF6B7280.toInt() }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; color = 0xFF121726.toInt() }
    var y = margin + 26f
    canvas.drawText(context.getString(R.string.title_plan), margin, y, title)
    y += 28f
    listOf(
        R.string.plan_meeting to plan.meetingPoint, R.string.plan_contacts to plan.contacts,
        R.string.plan_arrangements to plan.arrangements, R.string.plan_medical to plan.medical,
        R.string.plan_evacuation to plan.evacuation, R.string.plan_radio to plan.radioStation,
        R.string.plan_utilities to plan.utilities,
        R.string.plan_notes to plan.notes,
    ).forEach { (res, value) ->
        y += 18f
        canvas.drawText(context.getString(res).uppercase(), margin, y, label)
        y += 6f
        val layout = StaticLayout.Builder.obtain(value.ifBlank { "–" }, 0, value.ifBlank { "–" }.length, body, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.save(); canvas.translate(margin, y); layout.draw(canvas); canvas.restore()
        y += layout.height + 8f
    }
    canvas.drawText(context.getString(R.string.pdf_footer), margin, 842 - margin / 2, label)
    doc.finishPage(page)
}
