package com.ctint.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What the admin entered in the officer dialog. */
data class StaffForm(
    val group: String,
    val cadre: String,
    val post: String,
    val name: String,
    val mobile: String,
    val direct: Boolean,
    /** Add only: save as a vacant post with no officer yet. */
    val vacantOnly: Boolean,
    /** Add only: an existing vacant post (same unit and cadre) that the officer will fill. */
    val fillTarget: Staff?
)

/**
 * Add (initial == null) or edit one officer. For a new officer, if the chosen unit has a vacant post
 * of the chosen cadre the officer fills it (Sanctioned stays the same); otherwise a new post is created.
 */
@Composable
fun StaffDialog(
    initial: Staff?,
    dir: Directory,
    defaultGroup: String?,
    onDismiss: () -> Unit,
    onSave: (StaffForm) -> Unit
) {
    val unitOptions = remember(dir) { dir.staff.map { it.group }.distinct() }
    val cadreOptions = remember(dir) { dir.cadres.map { it.name } }
    val adding = initial == null

    var name by remember { mutableStateOf(if (initial != null && !initial.isVacant) initial.name else "") }
    var mobile by remember { mutableStateOf(initial?.mobile ?: "") }
    var direct by remember { mutableStateOf(initial?.isDirect ?: false) }
    var group by remember { mutableStateOf(initial?.group ?: defaultGroup ?: unitOptions.firstOrNull() ?: "") }
    var cadre by remember { mutableStateOf(initial?.cadre ?: cadreOptions.firstOrNull() ?: "") }
    var post by remember { mutableStateOf(initial?.post ?: "") }
    var vacantOnly by remember { mutableStateOf(false) }
    var createNew by remember { mutableStateOf(false) }

    val slots = remember(dir, group, cadre) {
        dir.staff.filter { it.isVacant && it.group == group.trim() && it.cadre == cadre }
    }
    val fillTarget: Staff? = if (adding && !vacantOnly && !createNew) slots.firstOrNull() else null

    val mobileOk = mobile.isEmpty() || mobile.length == 10
    val needsName = !(adding && vacantOnly)
    val canSave = (!needsName || name.isNotBlank()) && group.isNotBlank() && cadre.isNotBlank() && mobileOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    adding -> "Add officer"
                    initial!!.isVacant -> "Assign officer"
                    else -> "Edit officer"
                }
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (cadreOptions.isEmpty()) {
                    Text(
                        "There is no cadre yet. Close this and add a cadre from the Cadre Strength page first.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (adding) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vacantOnly = !vacantOnly },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = vacantOnly, onCheckedChange = { vacantOnly = it })
                        Text("Only add a vacant post (no officer yet)")
                    }
                }
                if (needsName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name of official") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 10) mobile = v },
                        label = { Text("Mobile number (10 digits)") },
                        singleLine = true,
                        isError = !mobileOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ChoiceField(
                    label = "Cadre",
                    options = cadreOptions,
                    value = cadre,
                    onValue = { cadre = it },
                    otherLabel = "",
                    allowOther = false
                )
                ChoiceField(
                    label = "Unit",
                    options = unitOptions,
                    value = group,
                    onValue = { group = it },
                    otherLabel = "New unit (type name)"
                )

                if (adding && !vacantOnly) {
                    if (slots.isNotEmpty()) {
                        Text(
                            "This unit has ${slots.size} vacant post(s) in this cadre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { createNew = false },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !createNew, onClick = { createNew = false })
                            Text("Fill the vacant post: ${slots.first().designation}\n(Sanctioned stays the same)")
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { createNew = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = createNew, onClick = { createNew = true })
                            Text("Create a new post\n(Sanctioned goes up by 1)")
                        }
                    } else {
                        Text(
                            "No vacant post here, so a new post is created (Sanctioned goes up by 1).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (fillTarget == null) {
                    OutlinedTextField(
                        value = post,
                        onValueChange = { post = it },
                        label = { Text("Post / designation (optional)") },
                        placeholder = { Text("e.g. Deputy State Tax Officer - 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (needsName) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = direct, onCheckedChange = { direct = it })
                        Text("Direct recruit")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        StaffForm(
                            group = group.trim(),
                            cadre = cadre,
                            post = post.trim(),
                            name = name.trim(),
                            mobile = if (needsName) mobile else "",
                            direct = direct,
                            vacantOnly = adding && vacantOnly,
                            fillTarget = fillTarget
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Add (initial == null) or edit a cadre. The sanctioned number is the number of posts: raising it adds
 * vacant posts to the chosen unit, lowering it removes vacant posts only.
 */
@Composable
fun CadreDialog(
    initial: Cadre?,
    dir: Directory,
    onDismiss: () -> Unit,
    onSave: (name: String, short: String, sanctioned: Int, group: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val unitOptions = remember(dir) { dir.staff.map { it.group }.distinct() }
    val posts = if (initial == null) 0 else dir.staff.count { it.cadre == initial.name }
    val filled = if (initial == null) 0 else dir.staff.count { it.cadre == initial.name && !it.isVacant }

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var short by remember { mutableStateOf(initial?.short ?: "") }
    var sanctioned by remember { mutableStateOf(if (initial == null) "" else posts.toString()) }
    var group by remember { mutableStateOf(unitOptions.firstOrNull() ?: "") }

    val duplicate = dir.cadres.any {
        it.name.equals(name.trim(), ignoreCase = true) && !it.name.equals(initial?.name, ignoreCase = true)
    }
    val count = sanctioned.toIntOrNull()
    val addingPosts = count != null && count > posts
    val canSave = name.isNotBlank() && short.isNotBlank() && !duplicate && count != null &&
        (!addingPosts || group.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add cadre" else "Edit cadre") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Cadre name") },
                    placeholder = { Text("e.g. Office Assistant, Driver") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("This cadre already exists") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = short,
                    onValueChange = { short = it },
                    label = { Text("Short name for the table") },
                    placeholder = { Text("e.g. OA, DRV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sanctioned,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) sanctioned = v },
                    label = { Text("Sanctioned strength (number of posts)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial != null) {
                    Text(
                        "Now: $posts posts, $filled filled, ${posts - filled} vacant.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (addingPosts) {
                    Text(
                        "${count!! - posts} new vacant post(s) will be added to:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    ChoiceField(
                        label = "Unit",
                        options = unitOptions,
                        value = group,
                        onValue = { group = it },
                        otherLabel = "New unit (type name)"
                    )
                } else if (count != null && count < posts) {
                    Text(
                        "Only vacant posts can be removed. If there are not enough vacant posts, " +
                            "remove or move the officers first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name.trim(), short.trim(), count ?: 0, group.trim()) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null && posts == 0 && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete cadre") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Pick from existing values; optionally the last item lets you type a new one. */
@Composable
private fun ChoiceField(
    label: String,
    options: List<String>,
    value: String,
    onValue: (String) -> Unit,
    otherLabel: String,
    allowOther: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(allowOther && value.isNotEmpty() && value !in options) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$label: ${if (custom) otherLabel else value}",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            custom = false
                            onValue(option)
                            expanded = false
                        }
                    )
                }
                if (allowOther) {
                    DropdownMenuItem(
                        text = { Text(otherLabel) },
                        onClick = {
                            custom = true
                            onValue("")
                            expanded = false
                        }
                    )
                }
            }
        }
        if (custom) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
