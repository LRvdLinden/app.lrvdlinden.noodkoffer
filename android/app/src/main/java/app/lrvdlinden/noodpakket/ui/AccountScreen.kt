package app.lrvdlinden.noodpakket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.Account
import kotlinx.coroutines.launch

/** Text for an error code of the server. */
@Composable
fun accountError(code: String?): String? = when (code) {
    null -> null
    "bad_credentials" -> stringResource(R.string.acct_err_credentials)
    "not_confirmed" -> stringResource(R.string.acct_err_not_confirmed)
    "bad_email" -> stringResource(R.string.acct_err_email)
    "password_short" -> stringResource(R.string.acct_err_pw_short)
    "password_weak" -> stringResource(R.string.acct_err_pw_weak)
    "password_long" -> stringResource(R.string.acct_err_pw_long)
    "rate_limited" -> stringResource(R.string.acct_err_rate)
    "already_member" -> stringResource(R.string.acct_err_member)
    "household_full" -> stringResource(R.string.acct_err_full)
    "self" -> stringResource(R.string.acct_err_self)
    "invite_not_found" -> stringResource(R.string.acct_err_invite)
    "not_owner" -> stringResource(R.string.acct_err_owner)
    "network" -> stringResource(R.string.acct_err_network)
    "unauthorized" -> stringResource(R.string.acct_err_signed_out)
    else -> stringResource(R.string.acct_err_server)
}

/** Settings card: the account and the household, or the offer to make one. */
@Composable
fun AccountSettingsCard(onOpen: () -> Unit, onSignIn: (Boolean) -> Unit) {
    val state by Account.state.collectAsState()
    LaunchedEffect(state.signedIn) { if (state.signedIn) runCatching { Account.refresh() } }
    SectionHeader(stringResource(R.string.acct_section))
    Card {
        if (state.signedIn) {
            val me = state.me
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(me?.household?.name ?: stringResource(R.string.acct_my_household), fontWeight = FontWeight.SemiBold)
                    val n = me?.household?.members?.size ?: 1
                    Text(if (n > 1) stringResource(R.string.acct_members_email, n, me?.user?.email.orEmpty()) else me?.user?.email.orEmpty(),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!me?.invites.isNullOrEmpty()) Icon(Icons.Outlined.MarkEmailUnread, null, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            AccountRow(Icons.Outlined.PersonAdd, stringResource(R.string.acct_create)) { onSignIn(true) }
            HorizontalDivider(Modifier.padding(start = 56.dp))
            AccountRow(Icons.Outlined.AccountCircle, stringResource(R.string.acct_sign_in)) { onSignIn(false) }
        }
    }
    val footer = when {
        !state.signedIn -> stringResource(R.string.acct_footer)
        state.syncing -> stringResource(R.string.acct_syncing)
        state.syncError != null -> accountError(state.syncError).orEmpty()
        else -> stringResource(R.string.acct_shared)
    }
    Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp))
}

@Composable
private fun AccountRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text)
    }
}

/** Full-screen dialog to sign in or create an account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInDialog(startWithSignUp: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var signUp by remember { mutableStateOf(startWithSignUp) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<Int?>(null) }
    var checkMail by remember { mutableStateOf(false) }

    fun run(work: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null; info = null
            try { work() } catch (e: Account.Failure) { error = e.code } catch (e: Exception) { error = "network" }
            busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (signUp) R.string.acct_new else R.string.acct_sign_in)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) } },
                    colors = brandTopBarColors(),
                )
            },
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (checkMail) {
                    Text(stringResource(R.string.acct_check_mail), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.acct_check_mail_text, email))
                    Button(onClick = { checkMail = false; signUp = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.acct_confirmed_sign_in))
                    }
                    OutlinedButton(onClick = { run { Account.resendConfirmation(email); info = R.string.acct_mail_again_sent } },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.acct_mail_again)) }
                    Text(stringResource(R.string.acct_no_mail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !signUp, onClick = { signUp = false; error = null },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.acct_sign_in)) }
                        SegmentedButton(selected = signUp, onClick = { signUp = true; error = null },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.acct_new)) }
                    }
                    if (signUp) {
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.acct_first_name)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.acct_email)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.acct_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    if (signUp) {
                        Text(stringResource(R.string.acct_pw_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            run {
                                if (signUp) {
                                    Account.signUp(name, email, password); checkMail = true
                                } else {
                                    try { Account.signIn(email, password); onDismiss() }
                                    catch (e: Account.Failure) { if (e.code == "not_confirmed") checkMail = true else throw e }
                                }
                            }
                        },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text(stringResource(if (signUp) R.string.acct_create_button else R.string.acct_sign_in)) }
                    if (!signUp) {
                        TextButton(onClick = { run { Account.forgotPassword(email); info = R.string.acct_forgot_sent } },
                            enabled = !busy && email.isNotBlank()) { Text(stringResource(R.string.acct_forgot)) }
                    }
                }
                accountError(error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                info?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** The account: household, members, invitations, sync and the account itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val state by Account.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var inviteOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf<Account.Invite?>(null) }
    var removing by remember { mutableStateOf<Account.Member?>(null) }
    var leaveOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { runCatching { Account.refresh() } }
    LaunchedEffect(state.signedIn) { if (!state.signedIn) onBack() }

    fun act(work: suspend () -> Unit) {
        scope.launch {
            error = null; info = null
            try { work() } catch (e: Account.Failure) { error = e.code } catch (e: Exception) { error = "network" }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.acct_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = brandTopBarColors(),
            )
        },
    ) { inner ->
        val me = state.me
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (!me?.invites.isNullOrEmpty()) {
                SectionHeader(stringResource(R.string.acct_invitations))
                Card {
                    me!!.invites.forEachIndexed { i, inv ->
                        if (i > 0) HorizontalDivider()
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.acct_invite_from, inv.from, inv.householdName), fontWeight = FontWeight.SemiBold)
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { joining = inv }) { Text(stringResource(R.string.acct_join)) }
                                OutlinedButton(onClick = { act { Account.decline(inv) } }) { Text(stringResource(R.string.acct_decline)) }
                            }
                        }
                    }
                }
                Note(stringResource(R.string.acct_join_note))
            }

            me?.household?.let { h ->
                SectionHeader(stringResource(R.string.acct_members))
                Card {
                    Row(Modifier.fillMaxWidth().clickable { renameOpen = true }.padding(16.dp)) {
                        Text(stringResource(R.string.acct_name), Modifier.weight(1f))
                        Text(h.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    h.members.forEach { m ->
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (m.me) stringResource(R.string.acct_you, m.displayName) else m.displayName)
                                if (m.name.isNotBlank()) Text(m.email, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (m.owner) Text(stringResource(R.string.acct_owner), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else if (h.isOwner && !m.me) TextButton(onClick = { removing = m }) { Text(stringResource(R.string.acct_remove)) }
                        }
                    }
                    h.invites.forEach { inv ->
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(inv.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.acct_invited), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { act { Account.cancelInvite(inv.id) } }) { Text(stringResource(R.string.acct_cancel_invite)) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    AccountRow(Icons.Outlined.GroupAdd, stringResource(R.string.acct_invite)) { inviteOpen = true }
                }
                Note(stringResource(R.string.acct_members_note))
            }

            SectionHeader(stringResource(R.string.acct_sync))
            Card {
                AccountRow(Icons.Outlined.Sync, stringResource(if (state.syncing) R.string.acct_syncing else R.string.acct_sync_now)) {
                    scope.launch { Account.syncNow() }
                }
            }
            state.lastSync?.let {
                Note(stringResource(R.string.acct_last_sync,
                    android.text.format.DateUtils.getRelativeTimeSpanString(it).toString()))
            }

            SectionHeader(stringResource(R.string.acct_account))
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.acct_email), Modifier.weight(1f))
                    Text(me?.user?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if ((me?.household?.members?.size ?: 1) > 1) {
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    PlainRow(stringResource(R.string.acct_leave)) { leaveOpen = true }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
                PlainRow(stringResource(R.string.acct_sign_out)) { act { Account.signOut() } }
                HorizontalDivider(Modifier.padding(start = 16.dp))
                PlainRow(stringResource(R.string.acct_delete), MaterialTheme.colorScheme.error) { deleteOpen = true }
            }
            accountError(error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (inviteOpen) {
        var email by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { inviteOpen = false },
            title = { Text(stringResource(R.string.acct_invite)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.acct_invite_text))
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.acct_email)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    inviteOpen = false
                    val mail = email
                    act { Account.invite(mail); info = context.getString(R.string.acct_invite_sent, mail) }
                }, enabled = email.isNotBlank()) { Text(stringResource(R.string.acct_send_invite)) }
            },
            dismissButton = { TextButton(onClick = { inviteOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (renameOpen) {
        var name by remember { mutableStateOf(state.me?.household?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.acct_household_name)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renameOpen = false; val n = name; act { Account.renameHousehold(n) } }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    joining?.let { inv ->
        AlertDialog(
            onDismissRequest = { joining = null },
            title = { Text(stringResource(R.string.acct_join_title)) },
            text = { Text(stringResource(R.string.acct_join_note)) },
            confirmButton = { TextButton(onClick = { joining = null; act { Account.accept(inv) } }) { Text(stringResource(R.string.acct_join_replace)) } },
            dismissButton = { TextButton(onClick = { joining = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.acct_remove_title)) },
            text = { Text(stringResource(R.string.acct_remove_text)) },
            confirmButton = { TextButton(onClick = { removing = null; act { Account.remove(m) } }) { Text(stringResource(R.string.acct_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (leaveOpen) {
        AlertDialog(
            onDismissRequest = { leaveOpen = false },
            title = { Text(stringResource(R.string.acct_leave_title)) },
            text = { Text(stringResource(R.string.acct_leave_text)) },
            confirmButton = { TextButton(onClick = { leaveOpen = false; act { Account.leave() } }) { Text(stringResource(R.string.acct_leave)) } },
            dismissButton = { TextButton(onClick = { leaveOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleteOpen) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.acct_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.acct_delete_text))
                    OutlinedTextField(pw, { pw = it }, label = { Text(stringResource(R.string.acct_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false; val p = pw; act { Account.deleteAccount(p) } }, enabled = pw.isNotBlank()) {
                    Text(stringResource(R.string.acct_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PlainRow(text: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified, onClick: () -> Unit) {
    Text(text, color = color, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp))
}
