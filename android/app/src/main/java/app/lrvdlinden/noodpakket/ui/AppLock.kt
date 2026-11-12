package app.lrvdlinden.noodpakket.ui

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.lrvdlinden.noodpakket.R

/** Optional app lock with fingerprint or face unlock, falling back to the screen lock (PIN, pattern). */
object AppLock {
    private const val KEY = "lock.enabled"

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun isEnabled(context: Context) = context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    fun isAvailable(context: Context) =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the system prompt; [onResult] gets true when the person is verified. */
    fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
            .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText(activity.getString(R.string.cancel)) }
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onResult(false) }
    }
}

/** Covers the app while it is locked. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val title = stringResource(R.string.lock_prompt)
    fun unlock() {
        if (activity == null) return onUnlock()
        AppLock.authenticate(activity, title) { ok -> if (ok) onUnlock() }
    }
    LaunchedEffect(Unit) { unlock() }
    BrandDarkTheme {
        Surface(Modifier.fillMaxSize(), color = Brand.purple) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                AppIconTile(96.dp)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = Color.White)
                Spacer(Modifier.height(40.dp))
                Button(onClick = { unlock() }, modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.yellow, contentColor = Brand.purple)) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.lock_unlock), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
