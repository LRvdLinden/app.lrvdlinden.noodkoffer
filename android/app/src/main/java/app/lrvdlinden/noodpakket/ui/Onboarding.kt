package app.lrvdlinden.noodpakket.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.Household
import app.lrvdlinden.noodpakket.notify.Messages
import app.lrvdlinden.noodpakket.notify.Reminders

/** Three welcome screens: what the app does, the household, and reminders. */
@Composable
fun OnboardingScreen(household: Household, onHousehold: (Household) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var reminders by remember { mutableStateOf(Reminders.isEnabled(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Reminders.setEnabled(context, granted); reminders = granted
    }
    var messages by remember { mutableStateOf(Messages.isEnabled(context)) }
    val messagesPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Messages.setEnabled(context, granted); messages = granted
    }

    BrandDarkTheme {
    Surface(Modifier.fillMaxSize(), color = Brand.purple) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            AnimatedContent(targetState = page, modifier = Modifier.weight(1f), label = "onboarding") { p ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    when (p) {
                        0 -> {
                            AppIconTile(120.dp)
                            Page(R.string.onb_title_1, R.string.onb_text_1)
                        }
                        1 -> {
                            ArtImage("blankets", Modifier.size(140.dp))
                            Page(R.string.onb_title_2, R.string.onb_text_2)
                            Spacer(Modifier.height(20.dp))
                            Card {
                                Count(stringResource(R.string.adults), household.adults, 1..12) { onHousehold(household.copy(adults = it)) }
                                HorizontalDivider()
                                Count(stringResource(R.string.children), household.children, 0..12) { onHousehold(household.copy(children = it)) }
                                HorizontalDivider()
                                Count(stringResource(R.string.pets), household.pets, 0..12) { onHousehold(household.copy(pets = it)) }
                            }
                        }
                        else -> {
                            ArtImage("batteries", Modifier.size(140.dp))
                            Page(R.string.onb_title_3, R.string.onb_text_3)
                            Spacer(Modifier.height(20.dp))
                            Card {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.reminders_toggle), Modifier.weight(1f))
                                    Switch(checked = reminders, onCheckedChange = { on ->
                                        if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.hasPermission(context)) {
                                            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            Reminders.setEnabled(context, on); reminders = on
                                        }
                                    })
                                }
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.messages_toggle))
                                        Text(stringResource(R.string.messages_onboarding), style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f))
                                    }
                                    Switch(checked = messages, onCheckedChange = { on ->
                                        if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.hasPermission(context)) {
                                            messagesPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            Messages.setEnabled(context, on); messages = on
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i ->
                    Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape)
                        .background(if (i == page) Brand.yellow else Color.White.copy(alpha = 0.3f)))
                }
            }
            Button(
                onClick = { if (page < 2) page++ else onDone() }, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.yellow, contentColor = Brand.purple),
            ) {
                Text(stringResource(if (page < 2) R.string.next else R.string.get_started), fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

@Composable
private fun Page(title: Int, text: Int) {
    Spacer(Modifier.height(24.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun Count(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f))
        Text("$value", style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Outlined.Remove, stringResource(R.string.less))
        }
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Outlined.Add, stringResource(R.string.more))
        }
    }
}
