package com.ctint.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StrengthScreen(
    dir: Directory,
    isAdmin: Boolean,
    onOpenUnit: (String) -> Unit,
    onAddCadre: () -> Unit,
    onEditCadre: (Cadre) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HeaderCard(dir) }
        item { OverallCard(dir.total) }
        item {
            CadreCard(
                title = "Officers",
                totalLabel = "Total officers",
                rows = dir.officerStrengths,
                isAdmin = isAdmin,
                showAdd = false,
                onAddCadre = onAddCadre,
                onEditCadre = onEditCadre
            )
        }
        item {
            CadreCard(
                title = "Office staff",
                totalLabel = "Total staff",
                rows = dir.staffStrengths,
                isAdmin = isAdmin,
                showAdd = true,
                onAddCadre = onAddCadre,
                onEditCadre = onEditCadre
            )
        }
        item {
            Text(
                "Unit-wise strength",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Tap a unit to see its officers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(dir.units, key = { it.unit }) { u ->
            UnitRow(u, onClick = { onOpenUnit(u.unit) })
        }
    }
}

/** Today's date on the phone, e.g. 29.10.2026, so the page always shows the current day. */
private fun todayText(): String = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())

@Composable
private fun HeaderCard(dir: Directory) {
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(dir.office, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = on)
            Text(dir.division, style = MaterialTheme.typography.bodyMedium, color = on)
            Text(
                "In position as on ${todayText()}",
                style = MaterialTheme.typography.labelLarge,
                color = on,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun OverallCard(total: Strength) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "CADRE STRENGTH",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BigNumber("Sanctioned", total.sanctioned, MaterialTheme.colorScheme.onSurface)
                BigNumber("In position", total.filled, goodColor())
                BigNumber("Vacant", total.vacant, if (total.vacant > 0) badColor() else MaterialTheme.colorScheme.onSurface)
            }
            LinearProgressIndicator(progress = { total.percent }, modifier = Modifier.fillMaxWidth())
            Text(
                "${(total.percent * 100).toInt()}% of sanctioned posts filled",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun BigNumber(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CadreCard(
    title: String,
    totalLabel: String,
    rows: List<CadreStrength>,
    isAdmin: Boolean,
    showAdd: Boolean,
    onAddCadre: () -> Unit,
    onEditCadre: (Cadre) -> Unit
) {
    val sub = rows.totalStrength()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            TableRow("Post", "Sanctioned", "Filled", "Vacant", header = true)
            HorizontalDivider()
            if (rows.isEmpty()) {
                Text(
                    "No cadre here yet." + if (isAdmin && showAdd) " Tap \u201cAdd cadre\u201d below." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            rows.forEach { cs ->
                TableRow(
                    cs.cadre.short,
                    cs.strength.sanctioned.toString(),
                    cs.strength.filled.toString(),
                    cs.strength.vacant.toString(),
                    vacantColor = if (cs.strength.vacant > 0) badColor() else null,
                    onClick = if (isAdmin) ({ onEditCadre(cs.cadre) }) else null
                )
            }
            HorizontalDivider()
            TableRow(
                totalLabel,
                sub.sanctioned.toString(),
                sub.filled.toString(),
                sub.vacant.toString(),
                bold = true,
                vacantColor = if (sub.vacant > 0) badColor() else null
            )
            if (isAdmin && showAdd) {
                Text(
                    "Tap a cadre row to edit it.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
                OutlinedButton(
                    onClick = onAddCadre,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                ) { Text("+ Add cadre") }
            }
        }
    }
}

@Composable
private fun TableRow(
    c1: String,
    c2: String,
    c3: String,
    c4: String,
    header: Boolean = false,
    bold: Boolean = false,
    vacantColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val weight = if (header || bold) FontWeight.Bold else FontWeight.Normal
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(c1, Modifier.weight(1.2f), style = style, fontWeight = weight)
        Text(c2, Modifier.weight(1.4f), style = style, fontWeight = weight, textAlign = TextAlign.End)
        Text(c3, Modifier.weight(1f), style = style, fontWeight = weight, textAlign = TextAlign.End)
        Text(
            c4,
            Modifier.weight(1f),
            style = style,
            fontWeight = if (vacantColor != null) FontWeight.Bold else weight,
            color = vacantColor ?: Color.Unspecified,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun UnitRow(u: UnitStrength, onClick: () -> Unit) {
    val s = u.strength
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                u.unit,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${s.filled} / ${s.sanctioned}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (s.vacant > 0) badColor() else goodColor()
                )
                if (s.vacant > 0) {
                    Text(
                        "${s.vacant} vacant",
                        style = MaterialTheme.typography.labelSmall,
                        color = badColor()
                    )
                }
            }
        }
    }
}
