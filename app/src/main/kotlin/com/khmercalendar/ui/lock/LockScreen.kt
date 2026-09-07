package com.khmercalendar.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.SettingsStore

/**
 * The app lock.
 *
 * This guards a private calendar on a shared phone. It is not a security boundary against
 * someone with the device unlocked and a file explorer - the data lives in the app sandbox
 * either way - so the PIN is compared against a salted hash and the screen deliberately does
 * not pretend to be more than it is.
 */
@Composable
fun LockScreen(
    settings: AppSettings,
    settingsStore: SettingsStore,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val biometricAvailable = remember(settings.biometricUnlock) {
        settings.biometricUnlock && canUseBiometrics(context)
    }

    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable) promptBiometric(context, onUnlocked)
    }

    fun submit(candidate: String) {
        if (settingsStore.verifyPin(candidate, settings)) {
            onUnlocked()
        } else {
            error = true
            pin = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text("បញ្ចូលលេខសម្ងាត់", style = MaterialTheme.typography.titleMedium)
        Text(
            if (error) "លេខសម្ងាត់មិនត្រឹមត្រូវ" else " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(MAX_PIN) { index ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { digit ->
                    KeypadButton(KhmerNumerals.toKhmer(digit.toString())) {
                        if (pin.length < MAX_PIN) {
                            error = false
                            pin += digit
                            if (pin.length >= MIN_PIN) submit(pin)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (biometricAvailable) {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = "ស្នាមម្រាមដៃ",
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { promptBiometric(context, onUnlocked) },
                    )
                }
            }
            KeypadButton(KhmerNumerals.toKhmer("0")) {
                if (pin.length < MAX_PIN) {
                    error = false
                    pin += "0"
                    if (pin.length >= MIN_PIN) submit(pin)
                }
            }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "លុប",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { pin = pin.dropLast(1) },
                )
            }
        }

        // A PIN set on a device the user later cannot unlock would strand their own data;
        // submitting a full-length PIN is checked immediately, and this makes the shorter
        // case reachable too.
        if (pin.length in MIN_PIN..MAX_PIN) {
            TextButton(onClick = { submit(pin) }) { Text("បញ្ជាក់") }
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

private const val MIN_PIN = 4
private const val MAX_PIN = 8

private fun canUseBiometrics(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows the system biometric sheet.
 *
 * Silently does nothing when the host activity is not a FragmentActivity, which is the case
 * in previews and tests; the PIN keypad remains available either way.
 */
private fun promptBiometric(context: Context, onUnlocked: () -> Unit) {
    val activity = context.findFragmentActivity() ?: return
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("ដោះសោប្រតិទិនខ្មែរ")
            .setNegativeButtonText("ប្រើលេខសម្ងាត់")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build(),
    )
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
