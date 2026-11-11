package app.lrvdlinden.noodpakket.ui

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodpakket.R

/** "What to do when…": short offline guidance, same content as the iOS app. */
enum class InfoTopic(@StringRes val title: Int, @ArrayRes val steps: Int, val icon: ImageVector) {
    NLALERT(R.string.info_nlalert, R.array.info_nlalert_steps, Icons.Outlined.Campaign),
    POWER(R.string.info_power, R.array.info_power_steps, Icons.Outlined.Bolt),
    WATER(R.string.info_water, R.array.info_water_steps, Icons.Outlined.WaterDrop),
    WEATHER(R.string.info_weather, R.array.info_weather_steps, Icons.Outlined.Thunderstorm),
    COMMS(R.string.info_comms, R.array.info_comms_steps, Icons.Outlined.SignalWifiOff),
    NUMBERS(R.string.info_numbers, R.array.info_numbers_steps, Icons.Outlined.Call),
}

@Composable
fun InfoDialog(topic: InfoTopic, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(topic.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stringArrayResource(topic.steps).forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                        Text(step)
                    }
                }
                Text(stringResource(R.string.info_source), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
