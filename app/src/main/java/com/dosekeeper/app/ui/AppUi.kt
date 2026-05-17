package com.dosekeeper.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dosekeeper.app.data.DoseHistoryEntry
import com.dosekeeper.app.data.DoseItem
import com.dosekeeper.app.data.DoseKind
import com.dosekeeper.app.data.SupplementGroup
import com.dosekeeper.app.data.SupplementTemplate
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun DoseKeeperApp(
    viewModel: DoseKeeperViewModel,
    onRequestNotificationPermission: () -> Unit,
    onAuthenticateNotes: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(AppTab.Dashboard) }
    var showMedicationDialog by rememberSaveable { mutableStateOf(false) }
    var showSupplementDialog by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    DoseBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = Color.White.copy(alpha = 0.92f),
                    tonalElevation = 0.dp,
                ) {
                    AppTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Crossfade(targetState = tab, label = "tab") { selected ->
                when (selected) {
                    AppTab.Dashboard -> DashboardScreen(
                        state = uiState,
                        now = now,
                        contentPadding = padding,
                        onRecordDose = viewModel::recordDose,
                        onAddMedication = { showMedicationDialog = true },
                        onAddSupplement = { showSupplementDialog = true },
                        onRequestNotifications = onRequestNotificationPermission,
                    )

                    AppTab.Plan -> PlanScreen(
                        state = uiState,
                        contentPadding = padding,
                        onAddSupplement = { showSupplementDialog = true },
                        onConfirmGroup = viewModel::recordGroup,
                    )

                    AppTab.History -> HistoryScreen(
                        entries = uiState.doseState.history,
                        contentPadding = padding,
                    )

                    AppTab.Notes -> NotesScreen(
                        state = uiState,
                        contentPadding = padding,
                        onUnlock = onAuthenticateNotes,
                        onSave = viewModel::saveNote,
                    )
                }
            }
        }
    }

    if (showMedicationDialog) {
        AddMedicationDialog(
            onDismiss = { showMedicationDialog = false },
            onAdd = { name, minutes ->
                viewModel.addMedication(name, minutes)
                showMedicationDialog = false
            },
        )
    }

    if (showSupplementDialog) {
        AddSupplementDialog(
            state = uiState,
            onDismiss = { showSupplementDialog = false },
            onAdd = {
                viewModel.addSupplement(it)
                showSupplementDialog = false
            },
        )
    }
}

@Composable
private fun DoseBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFEFF7F1),
                        Color(0xFFF7F0FA),
                    ),
                ),
            ),
    ) {
        content()
    }
}

@Composable
private fun DashboardScreen(
    state: DoseKeeperUiState,
    now: Long,
    contentPadding: PaddingValues,
    onRecordDose: (DoseItem) -> Unit,
    onAddMedication: () -> Unit,
    onAddSupplement: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val items = state.doseState.activeItems.sortedWith(compareBy<DoseItem> { it.kind.name }.thenBy { it.name })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            end = 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                title = "Dose Interval Keeper",
                subtitle = "A private timing aid for as-needed doses, supplements, pets, and care routines.",
            )
        }
        item {
            HeroStatus(items = items, now = now, onRequestNotifications = onRequestNotifications)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onAddMedication,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Medication")
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onAddSupplement,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Supplement")
                }
            }
        }
        items(items, key = { it.id }) { item ->
            DoseCard(item = item, now = now, onRecordDose = { onRecordDose(item) })
        }
    }
}

@Composable
private fun HeroStatus(
    items: List<DoseItem>,
    now: Long,
    onRequestNotifications: () -> Unit,
) {
    val next = items.mapNotNull { item -> item.nextSafeAtMillis()?.let { item to it } }
        .filter { it.second > now }
        .minByOrNull { it.second }

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF13233F), Color(0xFF2F6FED), Color(0xFF1DBA91)),
                ),
            ),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = next?.first?.name ?: "All tracked intervals are clear",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = next?.let { "Next safe window in ${formatDuration(it.second - now)}" }
                    ?: "Record the last dose or supplement and the app will keep the countdown visible.",
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRequestNotifications,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF13233F)),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enable reminders")
            }
        }
    }
}

@Composable
private fun DoseCard(
    item: DoseItem,
    now: Long,
    onRecordDose: () -> Unit,
) {
    val accent = Color(item.accentColor)
    val last = item.lastDoseAtMillis
    val next = item.nextSafeAtMillis()
    val remaining = next?.minus(now)
    val ready = remaining == null || remaining <= 0
    val progress = when {
        last == null || next == null -> 1f
        next <= last -> 1f
        else -> ((now - last).toFloat() / (next - last).toFloat()).coerceIn(0f, 1f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.kind == DoseKind.Medication) Icons.Rounded.Schedule else Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accent,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${item.intervalMinutes / 60}h minimum interval",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (item.kind == DoseKind.Medication) "PRN" else "Supplement") },
                )
            }

            Text(
                text = when {
                    last == null -> "No dose recorded yet"
                    ready -> "Interval complete"
                    else -> "Safe interval countdown: ${formatDuration(remaining ?: 0L)}"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (ready) Color(0xFF147D64) else Color(0xFF111827),
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.12f),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = last?.let { "Last: ${formatDateTime(it)}" } ?: "Tap when taken to start the timer.",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onRecordDose,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Taken now")
                }
            }
        }
    }
}

@Composable
private fun PlanScreen(
    state: DoseKeeperUiState,
    contentPadding: PaddingValues,
    onAddSupplement: () -> Unit,
    onConfirmGroup: (List<String>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            end = 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                title = "Supplement Plan",
                subtitle = "Compatible supplements are grouped; known conflicts are spaced apart.",
            )
        }
        item {
            FilledTonalButton(onClick = onAddSupplement, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add from supplement list")
            }
        }
        if (state.plan.isEmpty()) {
            item { EmptyPanel("Add supplements to generate a daily timing plan.") }
        } else {
            items(state.plan, key = { it.minuteOfDay }) { group ->
                PlanGroupCard(group = group, onConfirm = { onConfirmGroup(group.itemIds) })
            }
        }
        item {
            InfoPanel(
                title = "Timing aid",
                body = "This app tracks intervals and supplement spacing. It does not decide dose amounts or replace medication, supplement, veterinary, or clinical instructions.",
            )
        }
    }
}

@Composable
private fun PlanGroupCard(group: SupplementGroup, onConfirm: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatMinute(group.minuteOfDay),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onConfirm, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm all")
                }
            }
            Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            group.entries.forEach {
                Text("- ${it.item.name}: ${it.reason}", color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
            }
            group.notes.forEach {
                Text(it, color = Color(0xFF8A5A00), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryScreen(entries: List<DoseHistoryEntry>, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            end = 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(title = "History", subtitle = "Recent confirmations are stored locally on this device.") }
        if (entries.isEmpty()) {
            item { EmptyPanel("No doses recorded yet.") }
        } else {
            items(entries, key = { it.id }) { entry ->
                HistoryRow(entry)
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: DoseHistoryEntry) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.86f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAF0F7)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = Color(0xFF2F6FED))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.itemName, fontWeight = FontWeight.SemiBold)
                Text(formatDateTime(entry.takenAtMillis), color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            Text(if (entry.itemKind == DoseKind.Medication) "PRN" else "Supplement", color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun NotesScreen(
    state: DoseKeeperUiState,
    contentPadding: PaddingValues,
    onUnlock: (String) -> Unit,
    onSave: (String, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            end = 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                title = "Secure Notes",
                subtitle = "Medication notes are encrypted with Android Keystore and gated by biometric or device unlock.",
            )
        }
        state.noteError?.let { item { InfoPanel("Notes", it) } }
        items(state.doseState.activeItems, key = { it.id }) { item ->
            NoteCard(
                item = item,
                unlockedNote = state.unlockedNotes[item.id],
                onUnlock = { onUnlock(item.id) },
                onSave = { onSave(item.id, it) },
            )
        }
    }
}

@Composable
private fun NoteCard(
    item: DoseItem,
    unlockedNote: String?,
    onUnlock: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(unlockedNote, item.id) { mutableStateOf(unlockedNote.orEmpty()) }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (unlockedNote == null) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF64748B))
                }
            }
            if (unlockedNote == null) {
                Text("Unlock to view or edit local notes.", color = Color(0xFF64748B))
                Button(onClick = onUnlock, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock notes")
                }
            } else {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text("Notes") },
                    shape = RoundedCornerShape(18.dp),
                )
                Button(onClick = { onSave(draft) }, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save encrypted note")
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.82f),
    ) {
        Text(text, modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
    }
}

@Composable
private fun InfoPanel(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFFFFF7E6),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B4A00))
            Text(body, color = Color(0xFF6B4A00), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AddMedicationDialog(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("6") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onAdd(name, (hours.toFloatOrNull() ?: 6f).times(60).roundToInt()) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add medication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Minimum interval, hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun AddSupplementDialog(
    state: DoseKeeperUiState,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val activeTemplateIds = state.doseState.items.mapNotNull { it.supplementTemplateId }.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Add supplement") },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.templates, key = { it.id }) { template ->
                    SupplementTemplateRow(
                        template = template,
                        active = template.id in activeTemplateIds,
                        onAdd = { onAdd(template.id) },
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun SupplementTemplateRow(
    template: SupplementTemplate,
    active: Boolean,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = if (active) Color(0xFFF1F5F9) else Color.White,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(template.accentColor).copy(alpha = 0.16f)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(template.name, fontWeight = FontWeight.SemiBold)
                Text(template.notes, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(enabled = !active, onClick = onAdd) {
                Icon(if (active) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    Dashboard("Today", Icons.Rounded.Home),
    Plan("Plan", Icons.Rounded.DateRange),
    History("History", Icons.Rounded.History),
    Notes("Notes", Icons.Rounded.Lock),
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatMinute(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
