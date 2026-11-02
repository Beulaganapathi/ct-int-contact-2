package com.ctint.contact

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.net.URLEncoder
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If the app crashed last time, show the error text instead of closing again.
        val crashFile = java.io.File(filesDir, "crash.txt")
        val lastCrash = try {
            if (crashFile.exists()) crashFile.readText().also { crashFile.delete() } else null
        } catch (e: Exception) {
            null
        }
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                crashFile.writeText(android.util.Log.getStackTraceString(e))
            } catch (t: Throwable) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        if (lastCrash != null) {
            setContent { CtIntTheme { CrashScreen(lastCrash, onContinue = { recreate() }) } }
            return
        }

        val store = DirectoryStore(applicationContext)
        val pins = PinStore(applicationContext)
        setContent {
            CtIntTheme {
                val role = Session.role
                var activated by remember { mutableStateOf(Activation.isActive(applicationContext)) }
                if (!activated) {
                    ActivationScreen(pins = pins, onActivated = { activated = true })
                } else if (role == ROLE_LOCKED) {
                    LockScreen(pins = pins, onUnlock = { Session.role = it })
                } else {
                    CtIntApp(store = store, pins = pins, role = role)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Rotating the phone also stops the activity; that must not count as leaving the app.
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > AUTO_LOCK_MS) {
            Session.role = ROLE_LOCKED
            // An activation with an end date may have run out while the app was in the background.
            if (!Activation.isActive(this)) recreate()
        }
        stoppedAt = 0L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CtIntApp(store: DirectoryStore, pins: PinStore, role: Int) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dir = store.directory
    val isAdmin = role == ROLE_ADMIN

    // Tab 0 = Cadre strength (first page), tab 1 = Contacts.
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    var menuOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Staff?>(null) }
    var deleting by remember { mutableStateOf<Staff?>(null) }
    var showAddCadre by remember { mutableStateOf(false) }
    var editingCadre by remember { mutableStateOf<Cadre?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showActivate by remember { mutableStateOf(false) }
    var showBroadcast by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // First admin login while a built-in PIN is still active: ask to change it.
    LaunchedEffect(isAdmin) {
        if (isAdmin && pins.usingDefaults) showChangePin = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CT INT CONTACT") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (isAdmin) {
                            DropdownMenuItem(
                                text = { Text("Share backup of list") },
                                onClick = {
                                    menuOpen = false
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, store.backupText())
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share backup"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore from copied backup") },
                                onClick = { menuOpen = false; confirmRestore = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset to original list") },
                                onClick = { menuOpen = false; confirmReset = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Activate a friend's phone") },
                                onClick = { menuOpen = false; showActivate = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Change PINs") },
                                onClick = { menuOpen = false; showChangePin = true }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Message to officers") },
                            onClick = { menuOpen = false; showBroadcast = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Lock app") },
                            onClick = { menuOpen = false; Session.role = ROLE_LOCKED }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add officer") }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Cadre Strength") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    label = { Text("Contacts") }
                )
            }
        }
    ) { padding ->
        if (tab == 0) {
            StrengthScreen(
                dir = dir,
                isAdmin = isAdmin,
                onOpenUnit = { unit -> query = unit; tab = 1 },
                onAddCadre = { showAddCadre = true },
                onEditCadre = { editingCadre = it },
                modifier = Modifier.padding(padding)
            )
        } else {
            ContactsScreen(
                dir = dir,
                query = query,
                onQueryChange = { query = it },
                canEdit = isAdmin,
                onEdit = { editing = it },
                onDelete = { deleting = it },
                modifier = Modifier.padding(padding)
            )
        }
    }

    // ---- Message to officers ----
    if (showBroadcast) {
        BroadcastDialog(dir = dir, fullOptions = isAdmin, onDismiss = { showBroadcast = false })
    }

    // ---- Activate a friend's phone ----
    if (showActivate && isAdmin) {
        ActivateFriendDialog(onDismiss = { showActivate = false })
    }

    // ---- Change PINs ----
    if (showChangePin && isAdmin) {
        ChangePinDialog(
            pins = pins,
            onDismiss = { showChangePin = false },
            onChanged = { toast("PIN changed") }
        )
    }

    // ---- Add ----
    if (showAdd && isAdmin) {
        val unitInSearch = if (tab == 1) {
            dir.units.firstOrNull { it.unit.equals(query.trim(), ignoreCase = true) }?.unit
        } else null
        StaffDialog(
            initial = null,
            dir = dir,
            defaultGroup = unitInSearch,
            onDismiss = { showAdd = false },
            onSave = { f ->
                when {
                    f.vacantOnly -> {
                        store.addPosts(f.group, f.cadre, f.post, 1)
                        toast("Vacant post added")
                    }
                    f.fillTarget != null -> {
                        store.fillPost(f.fillTarget, f.name, f.mobile, f.direct)
                        toast("Officer added to vacant post")
                    }
                    else -> {
                        store.addOfficerWithNewPost(f.group, f.cadre, f.post, f.name, f.mobile, f.direct)
                        toast("Officer added with a new post")
                    }
                }
                showAdd = false
            }
        )
    }

    // ---- Edit / assign ----
    editing?.let { s ->
        if (isAdmin) {
            StaffDialog(
                initial = s,
                dir = dir,
                defaultGroup = null,
                onDismiss = { editing = null },
                onSave = { f ->
                    store.update(s, f.group, f.cadre, f.post, f.name, f.mobile, f.direct)
                    editing = null
                    toast("Saved")
                }
            )
        }
    }

    // ---- Add / edit cadre ----
    if (showAddCadre && isAdmin) {
        CadreDialog(
            initial = null,
            dir = dir,
            onDismiss = { showAddCadre = false },
            onSave = { name, short, sanctioned, group ->
                store.addCadre(name, short, sanctioned, group)
                showAddCadre = false
                toast("Cadre added")
            },
            onDelete = null
        )
    }
    editingCadre?.let { c ->
        if (isAdmin) {
            CadreDialog(
                initial = c,
                dir = dir,
                onDismiss = { editingCadre = null },
                onSave = { name, short, sanctioned, group ->
                    val err = store.updateCadre(c, name, short, sanctioned, group)
                    if (err != null) {
                        toast(err)
                    } else {
                        editingCadre = null
                        toast("Saved")
                    }
                },
                onDelete = {
                    if (store.removeCadre(c)) toast("Cadre deleted")
                    editingCadre = null
                }
            )
        }
    }

    // ---- Delete officer / post ----
    deleting?.let { s ->
        if (isAdmin) {
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(if (s.isVacant) "Remove vacant post?" else "Remove ${s.name}?") },
                text = {
                    Text(
                        if (s.isVacant) {
                            "The vacant ${s.designation} post is deleted. Sanctioned goes down by one."
                        } else {
                            "Mark vacant: the officer leaves but the post stays (Filled goes down, " +
                                "Vacant goes up, Sanctioned unchanged).\n\n" +
                                "Remove post: the post is deleted too (Sanctioned goes down by one)."
                        }
                    )
                },
                confirmButton = {
                    Row {
                        if (!s.isVacant) {
                            TextButton(onClick = { store.markVacant(s); deleting = null; toast("Marked vacant") }) {
                                Text("Mark vacant")
                            }
                        }
                        TextButton(onClick = { store.removePost(s); deleting = null; toast("Post removed") }) {
                            Text("Remove post")
                        }
                    }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
            )
        }
    }


    // ---- Reset ----
    if (confirmReset && isAdmin) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset to original list?") },
            text = { Text("All additions, edits and deletions made on this phone will be lost.") },
            confirmButton = {
                TextButton(onClick = { store.reset(); confirmReset = false; toast("List reset") }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }

    // ---- Restore ----
    if (confirmRestore && isAdmin) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "First copy the whole backup message (the one you shared earlier). " +
                        "The current list on this phone will be replaced by the copied one."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = clipboard.getText()?.text
                    confirmRestore = false
                    if (text != null && store.restore(text)) toast("Backup restored")
                    else toast("Copied text is not a valid backup")
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------
// PIN lock (kept in this file so the app needs only MainActivity.kt)
// ---------------------------------------------------------------------------

const val ROLE_LOCKED = 0
const val ROLE_VIEWER = 1
const val ROLE_ADMIN = 2

/**
 * >>> CHANGE THESE TWO LINES BEFORE YOU SHARE THE APP <<<
 * OPEN_PIN_DEFAULT  : given to everyone; opens the app in view-only mode.
 * ADMIN_PIN_DEFAULT : only for you; opens the app with Add / Edit / Delete.
 * Each is 4 to 6 digits and the two must be different.
 */
private const val OPEN_PIN_DEFAULT = "1234"
private const val ADMIN_PIN_DEFAULT = "5678"

/** The app locks itself again if it stayed in the background longer than this. */
const val AUTO_LOCK_MS = 5 * 60 * 1000L

/** Who is using the app right now. Lives as long as the app process. */
object Session {
    var role by mutableStateOf(ROLE_LOCKED)
}

/** Stores only salted hashes of the PINs, never the PINs themselves. */
class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("pins", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains("salt")) {
            val salt = UUID.randomUUID().toString()
            prefs.edit()
                .putString("salt", salt)
                .putString("userHash", hashWith(salt, OPEN_PIN_DEFAULT))
                .putString("adminHash", hashWith(salt, ADMIN_PIN_DEFAULT))
                .apply()
        }
    }

    private fun salt(): String = prefs.getString("salt", "") ?: ""
    private fun userHash(): String = prefs.getString("userHash", "") ?: ""
    private fun adminHash(): String = prefs.getString("adminHash", "") ?: ""

    private fun hashWith(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun computeUsingDefaults(): Boolean =
        userHash() == hashWith(salt(), OPEN_PIN_DEFAULT) ||
            adminHash() == hashWith(salt(), ADMIN_PIN_DEFAULT)

    /** True while either PIN is still the one built into the app. */
    var usingDefaults: Boolean by mutableStateOf(computeUsingDefaults())
        private set

    fun check(pin: String): Int {
        val h = hashWith(salt(), pin)
        return when (h) {
            adminHash() -> ROLE_ADMIN
            userHash() -> ROLE_VIEWER
            else -> ROLE_LOCKED
        }
    }

    /** Empty string = keep that PIN. Returns an error message, or null when saved. */
    fun change(newOpen: String, newAdmin: String): String? {
        fun valid(p: String) = p.isEmpty() || (p.length in 4..6 && p.all { it.isDigit() })
        if (!valid(newOpen) || !valid(newAdmin)) return "PIN must be 4 to 6 digits"
        if (newOpen.isEmpty() && newAdmin.isEmpty()) return "Enter at least one new PIN"

        val salt = salt()
        val u = if (newOpen.isEmpty()) userHash() else hashWith(salt, newOpen)
        val a = if (newAdmin.isEmpty()) adminHash() else hashWith(salt, newAdmin)
        if (u == a) return "Open PIN and Admin PIN must be different"

        prefs.edit().putString("userHash", u).putString("adminHash", a).apply()
        usingDefaults = computeUsingDefaults()
        return null
    }
}

@Composable
fun LockScreen(pins: PinStore, onUnlock: (Int) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var fails by remember { mutableStateOf(0) }
    var blocked by remember { mutableStateOf(false) }

    // After 5 wrong tries, wait 30 seconds.
    LaunchedEffect(blocked) {
        if (blocked) {
            delay(30_000)
            blocked = false
            fails = 0
        }
    }

    fun tryUnlock() {
        if (blocked || pin.length < 4) return
        val role = pins.check(pin)
        if (role != ROLE_LOCKED) {
            onUnlock(role)
        } else {
            wrong = true
            pin = ""
            fails += 1
            if (fails >= 5) blocked = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CT INT CONTACT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Enter PIN to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { v ->
                    if (v.all { it.isDigit() } && v.length <= 6) {
                        pin = v
                        wrong = false
                    }
                },
                label = { Text("PIN") },
                singleLine = true,
                enabled = !blocked,
                isError = wrong || blocked,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { tryUnlock() }),
                supportingText = {
                    when {
                        blocked -> Text("Too many wrong tries. Wait 30 seconds.")
                        wrong -> Text("Wrong PIN")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { tryUnlock() },
                enabled = !blocked && pin.length >= 4,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Unlock") }
        }
    }
}

@Composable
fun ChangePinDialog(pins: PinStore, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var openPin by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PINs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pins.usingDefaults) {
                    Text(
                        "A default PIN is still in use. Please change it.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "Leave a box empty to keep that PIN. PINs are 4 to 6 digits. " +
                        "These changes apply to this phone only.",
                    style = MaterialTheme.typography.bodySmall
                )
                PinField(openPin, { openPin = it; error = null }, "New open PIN (for everyone)")
                PinField(adminPin, { adminPin = it; error = null }, "New admin PIN (only for you)")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = pins.change(openPin, adminPin)
                if (e == null) {
                    onChanged()
                    onDismiss()
                } else {
                    error = e
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 6) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}


// ---------------------------------------------------------------------------
// Activation: the app works only on phones you have approved
// ---------------------------------------------------------------------------

/**
 * >>> CHANGE THIS TEXT TO ANY LONG SECRET OF YOUR OWN BEFORE YOU SHARE THE APP <<<
 * Never tell it to anyone. Activation codes are made from it.
 */
private const val ACTIVATION_SECRET = "ct-int-contact-CHANGE-THIS-LONG-SECRET-2026"

object Activation {
    private const val PREFS = "activation"
    private const val DAY_MS = 86_400_000L

    /** Short id of this phone, shown to the owner by a friend, e.g. 1A2B-3C4D-5E6F. */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        if (raw.length < 12 || raw == "9774d56d682e549c") {
            raw = prefs.getString("fallbackId", null)
                ?: UUID.randomUUID().toString().replace("-", "").also {
                    prefs.edit().putString("fallbackId", it).apply()
                }
        }
        val hex = raw.filter { it.isLetterOrDigit() }.uppercase().take(12).padEnd(12, '0')
        return hex.chunked(4).joinToString("-")
    }

    private fun clean(id: String) = id.filter { it.isLetterOrDigit() }.uppercase()

    private fun today(): Long = System.currentTimeMillis() / DAY_MS

    private fun mac(deviceId: String, expiry: String): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(ACTIVATION_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return m.doFinal((clean(deviceId) + expiry).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
            .take(8)
    }

    /** Code for a friend's phone. [days] = null means it never runs out. */
    fun makeCode(friendDeviceId: String, days: Int?): String {
        val expiry = if (days == null) "0000" else
            java.lang.Long.toString(today() + days, 36).uppercase().padStart(4, '0')
        val m = mac(friendDeviceId, expiry)
        return "$expiry-${m.take(4)}-${m.drop(4)}"
    }

    /** null = good code. Otherwise the reason it was refused. */
    private fun problem(deviceId: String, code: String): String? {
        val c = clean(code)
        if (c.length != 12) return "That code is not valid"
        val expiry = c.take(4)
        if (mac(deviceId, expiry) != c.drop(4)) return "That code is not for this phone"
        if (expiry != "0000") {
            val last = expiry.toLongOrNull(36) ?: return "That code is not valid"
            if (today() > last) return "This code has expired. Ask the owner for a new one"
        }
        return null
    }

    fun isActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("owner", false)) return true
        val code = prefs.getString("code", null) ?: return false
        return problem(deviceId(context), code) == null
    }

    /** Tries an activation code (friend) or the Admin PIN (owner). Returns an error text, or null on success. */
    fun activate(context: Context, input: String, pins: PinStore): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = input.trim()
        if (text.isNotEmpty() && text.all { it.isDigit() } && pins.check(text) == ROLE_ADMIN) {
            prefs.edit().putBoolean("owner", true).apply()
            return null
        }
        val err = problem(deviceId(context), text)
        if (err == null) prefs.edit().putString("code", text).apply()
        return err
    }
}

@Composable
fun ActivationScreen(pins: PinStore, onActivated: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val id = remember { Activation.deviceId(context) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CT INT CONTACT", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "This phone is not activated yet. Send the ID below to the app owner and " +
                    "ask for an activation code.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Text("Phone ID", style = MaterialTheme.typography.labelMedium)
            Text(id, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(id))
                    Toast.makeText(context, "ID copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy ID") }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "CT INT CONTACT phone ID: $id")
                    }
                    context.startActivity(Intent.createChooser(send, "Send ID to owner"))
                }) { Text("Share ID") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = { Text("Activation code (owner: Admin PIN)") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    val err = Activation.activate(context, input, pins)
                    if (err == null) onActivated() else error = err
                }
            ) { Text("Activate") }
        }
    }
}

/** Owner only: turns a friend's phone ID into an activation code. */
@Composable
fun ActivateFriendDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var friendId by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    val idOk = friendId.filter { it.isLetterOrDigit() }.length == 12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activate a friend's phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = friendId,
                    onValueChange = { friendId = it; code = null },
                    label = { Text("Friend's phone ID") },
                    placeholder = { Text("XXXX-XXXX-XXXX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) { days = v; code = null } },
                    label = { Text("Valid for how many days? (empty = always)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                code?.let {
                    Text("Activation code", style = MaterialTheme.typography.labelMedium)
                    Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            if (code == null) {
                TextButton(enabled = idOk, onClick = {
                    code = Activation.makeCode(friendId, days.toIntOrNull())
                }) { Text("Make code") }
            } else {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "CT INT CONTACT activation code: $code")
                    }
                    context.startActivity(Intent.createChooser(send, "Send code"))
                }) { Text("Share code") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


// ---------------------------------------------------------------------------
// One message to many officers (for example all STO and DSTO)
// ---------------------------------------------------------------------------

@Composable
fun BroadcastDialog(dir: Directory, fullOptions: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val stoName = "State Tax Officer"
    val dstoName = "Deputy State Tax Officer"
    var chosen by remember {
        mutableStateOf(dir.cadres.map { it.name }.filter { it == stoName || it == dstoName }.toSet())
    }
    var message by remember { mutableStateOf("") }
    var oneByOne by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(setOf<String>()) }

    val inCadres = dir.ordered.filter { it.cadre in chosen && !it.isVacant }
    val people = inCadres.filter { it.mobile.length == 10 }.distinctBy { it.mobile }
    val skipped = inCadres.size - people.size

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (oneByOne) "WhatsApp, one by one" else "Message to officers") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!oneByOne) {
                    Text("Send to:", style = MaterialTheme.typography.labelMedium)
                    dir.orderedCadres.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosen = if (c.name in chosen) chosen - c.name else chosen + c.name
                                    sent = emptySet()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = c.name in chosen,
                                onCheckedChange = {
                                    chosen = if (it) chosen + c.name else chosen - c.name
                                    sent = emptySet()
                                }
                            )
                            Text(c.short)
                        }
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it; sent = emptySet() },
                        label = { Text("Message") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${people.size} officers will get it" +
                            if (skipped > 0) " ($skipped skipped: no number or duplicate)" else "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val enabled = people.isNotEmpty() && message.isNotBlank()
                    OutlinedButton(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
                        val to = people.joinToString(";") { it.mobile }
                        open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to")).apply {
                            putExtra("sms_body", message)
                        })
                    }) { Text("SMS to all at once") }
                    if (fullOptions) {
                    OutlinedButton(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
                        oneByOne = true
                    }) { Text("WhatsApp: send one by one") }
                    OutlinedButton(enabled = message.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        try {
                            context.startActivity(Intent(send).setPackage("com.whatsapp"))
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(send).setPackage("com.whatsapp.w4b"))
                            } catch (e2: Exception) {
                                open(Intent.createChooser(send, "Send message"))
                            }
                        }
                    }) { Text("WhatsApp group (1 tap)") }
                    OutlinedButton(enabled = people.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                        clipboard.setText(AnnotatedString(people.joinToString(", ") { it.mobile }))
                        Toast.makeText(context, "${people.size} numbers copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy all numbers") }
                    }
                } else {
                    Text(
                        "Tap Send for each officer. WhatsApp opens with the message ready. Press send there, then come back.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    people.forEach { s ->
                        val done = s.mobile in sent
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Medium)
                                Text(s.group, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                sent = sent + s.mobile
                                val url = "https://wa.me/91${s.mobile}?text=" +
                                    URLEncoder.encode(message, "UTF-8")
                                open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }) { Text(if (done) "Sent \u2713" else "Send") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (oneByOne) TextButton(onClick = { oneByOne = false }) { Text("Back") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


/** Shown after a crash so the error can be copied and sent for fixing. */
@Composable
fun CrashScreen(trace: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("The app closed because of an error", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Tap Copy error, then paste it in the chat.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(trace))
                    Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy error") }
                OutlinedButton(onClick = onContinue) { Text("Try again") }
            }
            Text(
                trace.take(3000),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
    }
}
