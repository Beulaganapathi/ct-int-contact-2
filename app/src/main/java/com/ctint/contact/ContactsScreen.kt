@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.ctint.contact

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ContactsScreen(
    dir: Directory,
    query: String,
    onQueryChange: (String) -> Unit,
    canEdit: Boolean,
    onEdit: (Staff) -> Unit,
    onDelete: (Staff) -> Unit,
    modifier: Modifier = Modifier
) {
    val q = query.trim()
    val filtered = remember(dir, q) {
        if (q.isEmpty()) dir.ordered
        else dir.ordered.filter {
            it.name.contains(q, true) || it.post.contains(q, true) || it.cadre.contains(q, true) ||
                it.group.contains(q, true) || it.mobile.contains(q)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.group } }
    var expandedSl by rememberSaveable { mutableStateOf(-1) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search name, post, unit or number") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (filtered.isEmpty()) {
            Text(
                "No officers match “$q”.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            grouped.forEach { (unit, members) ->
                stickyHeader(key = "h_$unit") {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            unit,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                items(members, key = { it.sl }) { s ->
                    StaffRow(
                        s = s,
                        canEdit = canEdit,
                        expanded = expandedSl == s.sl,
                        onToggle = { expandedSl = if (expandedSl == s.sl) -1 else s.sl },
                        onEdit = { onEdit(s) },
                        onDelete = { onDelete(s) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffRow(
    s: Staff,
    canEdit: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hasNumber = s.mobile.isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = hasNumber || canEdit, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        s.isVacant -> "Vacant"
                        s.name.isBlank() -> "Name not added"
                        else -> s.name
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        s.isVacant -> badColor()
                        s.name.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color.Unspecified
                    }
                )
                Text(
                    s.designation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasNumber) {
                    Text(
                        s.mobile,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (s.isDirect) {
                    AssistChip(onClick = {}, label = { Text("Direct recruit") }, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (hasNumber) {
                IconButton(onClick = { dial(context, s.mobile) }) {
                    Icon(Icons.Default.Call, contentDescription = "Call ${s.name}", tint = goodColor())
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasNumber) {
                    OutlinedButton(onClick = { whatsApp(context, s.mobile) }) { Text("WhatsApp") }
                    OutlinedButton(onClick = { sms(context, s.mobile) }) { Text("SMS") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(s.mobile))
                        Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                }
                if (canEdit) {
                    OutlinedButton(onClick = onEdit) { Text(if (s.isVacant) "Assign officer" else "Edit") }
                    OutlinedButton(onClick = onDelete) { Text(if (s.isVacant) "Remove post" else "Delete") }
                }
            }
        }
    }
}

// ---- Intents ---------------------------------------------------------------
// The dialer opens with the number filled in, so no CALL_PHONE permission is needed.

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this", Toast.LENGTH_SHORT).show()
    }
}

private fun dial(context: Context, number: String) =
    launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

private fun sms(context: Context, number: String) =
    launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))

private fun whatsApp(context: Context, number: String) =
    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$number")))
