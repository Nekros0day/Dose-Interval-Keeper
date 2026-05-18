package com.dosekeeper.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FoodBank
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.dosekeeper.app.data.DoseHistoryEntry
import com.dosekeeper.app.data.DoseItem
import com.dosekeeper.app.data.DoseKind
import com.dosekeeper.app.data.DosePlanGroup
import com.dosekeeper.app.data.InteractionRule
import com.dosekeeper.app.data.ScheduledDose
import com.dosekeeper.app.data.SupplementTemplate
import com.dosekeeper.app.data.TimingPreference
import com.dosekeeper.app.data.minuteOfDay
import com.dosekeeper.app.quiz.QuizEngine
import com.dosekeeper.app.quiz.QuizQuestion
import com.dosekeeper.app.quiz.QuizQuestionType
import com.dosekeeper.app.quiz.QuizRecommendation
import com.dosekeeper.app.quiz.QuizResult
import com.dosekeeper.app.quiz.RecommendationStatus
import com.dosekeeper.app.scheduling.SupplementScheduler
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DoseKeeperApp(
    viewModel: DoseKeeperViewModel,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onQuizCompleted: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(AppTab.Today) }
    var showMedicationDialog by rememberSaveable { mutableStateOf(false) }
    var showSupplementDialog by rememberSaveable { mutableStateOf(false) }
    var showHistorySettings by rememberSaveable { mutableStateOf(false) }
    var showQuietHours by rememberSaveable { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<InteractionRule?>(null) }
    var editingItem by remember { mutableStateOf<DoseItem?>(null) }
    var addingRule by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var quizAnswers by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var quizIndex by rememberSaveable { mutableStateOf(0) }
    var quizAddedTemplateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var quizCompletionAdShown by rememberSaveable { mutableStateOf(false) }
    var todayTabTapCount by rememberSaveable { mutableStateOf(0) }
    var showTodayDemoMenu by rememberSaveable { mutableStateOf(false) }
    var todayDemoMode by rememberSaveable { mutableStateOf(TodayDemoMode.Live) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val planNow = (now / PLAN_REFRESH_MILLIS) * PLAN_REFRESH_MILLIS
    val currentPlan = remember(uiState.doseState, uiState.templates, uiState.interactions, planNow) {
        SupplementScheduler.buildPlan(
            items = uiState.doseState.activeItems,
            templates = uiState.templates,
            rules = uiState.interactions,
            nowMillis = planNow,
            quietStartMinute = uiState.doseState.quietStartMinute,
            quietEndMinute = uiState.doseState.quietEndMinute,
        )
    }

    DoseBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    AdMobBanner()
                    NavigationBar(
                        containerColor = Color.White.copy(alpha = 0.92f),
                        tonalElevation = 0.dp,
                    ) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = {
                                    if (item == AppTab.Today) {
                                        todayTabTapCount += 1
                                        if (todayTabTapCount >= 10) showTodayDemoMenu = true
                                    } else {
                                        todayTabTapCount = 0
                                    }
                                    tab = item
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Crossfade(targetState = tab, label = "tab") { selected ->
                when (selected) {
                    AppTab.Today -> TodayScreen(
                        state = uiState,
                        plan = currentPlan,
                        now = now,
                        contentPadding = padding,
                        onRecordDose = viewModel::recordDose,
                        onRecordGroup = viewModel::recordGroup,
                        onRemoveItem = viewModel::removeItem,
                        onAddMedication = { showMedicationDialog = true },
                        onAddSupplement = { showSupplementDialog = true },
                        notificationsAllowed = notificationsAllowed,
                        onRequestNotifications = onRequestNotificationPermission,
                        showDemoMenu = showTodayDemoMenu,
                        demoMode = todayDemoMode,
                        onDemoModeChange = { todayDemoMode = it },
                    )

                    AppTab.Plan -> PlanScreen(
                        state = uiState,
                        plan = currentPlan,
                        contentPadding = padding,
                        onAddSupplement = { showSupplementDialog = true },
                        onOpenQuietHours = { showQuietHours = true },
                        onEditItem = { editingItem = it },
                        onRemoveItem = viewModel::removeItem,
                    )

                    AppTab.History -> HistoryScreen(
                        state = uiState,
                        contentPadding = padding,
                        onClearHistory = viewModel::clearHistory,
                        onOpenSettings = { showHistorySettings = true },
                    )

                    AppTab.Quiz -> QuizScreen(
                        state = uiState,
                        contentPadding = padding,
                        answers = quizAnswers,
                        currentIndex = quizIndex,
                        addedTemplateIds = quizAddedTemplateIds,
                        onAnswersChange = { quizAnswers = it },
                        onIndexChange = { quizIndex = it },
                        onAddedTemplateIdsChange = { quizAddedTemplateIds = it },
                        onAddSupplements = viewModel::addSupplements,
                        onQuizCompleted = onQuizCompleted,
                        quizCompletionAdShown = quizCompletionAdShown,
                        onQuizCompletionAdShownChange = { quizCompletionAdShown = it },
                    )

                    AppTab.Rules -> RulesScreen(
                        state = uiState,
                        contentPadding = padding,
                        onAddRule = { addingRule = true },
                        onEditRule = { editingRule = it },
                        onDeleteRule = viewModel::deleteInteraction,
                    )
                }
            }
        }
    }

    if (showMedicationDialog) {
        AddMedicationDialog(
            onDismiss = { showMedicationDialog = false },
            onAdd = { name, minutes, preference, target ->
                viewModel.addMedication(name, minutes, preference, target)
                showMedicationDialog = false
            },
        )
    }

    if (showSupplementDialog) {
        AddSupplementDialog(
            state = uiState,
            onDismiss = { showSupplementDialog = false },
            onAddTemplate = {
                viewModel.addSupplement(it)
            },
            onAddCustom = { name, minutes, preference, target, description ->
                viewModel.addCustomSupplement(name, minutes, preference, target, description)
            },
        )
    }

    if (showHistorySettings) {
        HistorySettingsDialog(
            currentDays = uiState.doseState.historyRetentionDays,
            onDismiss = { showHistorySettings = false },
            onSet = {
                viewModel.setHistoryRetentionDays(it)
                showHistorySettings = false
            },
        )
    }

    if (showQuietHours) {
        QuietHoursDialog(
            startMinute = uiState.doseState.quietStartMinute,
            endMinute = uiState.doseState.quietEndMinute,
            onDismiss = { showQuietHours = false },
            onSave = { start, end ->
                viewModel.setQuietHours(start, end)
                showQuietHours = false
            },
        )
    }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { name, minutes, preference, target, description ->
                viewModel.updateItemSchedule(item, name, minutes, preference, target, description)
                editingItem = null
            },
        )
    }

    if (addingRule || editingRule != null) {
        InteractionDialog(
            items = uiState.doseState.activeItems,
            existing = editingRule,
            onDismiss = {
                addingRule = false
                editingRule = null
            },
            onSave = { existingId, firstId, secondId, minutes, reason ->
                if (existingId == null) {
                    viewModel.addInteraction(firstId, secondId, minutes, reason)
                } else {
                    viewModel.updateInteraction(existingId, firstId, secondId, minutes, reason)
                }
                addingRule = false
                editingRule = null
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
private fun AdMobBanner() {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White.copy(alpha = 0.94f)),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

@Composable
private fun TodayScreen(
    state: DoseKeeperUiState,
    plan: List<DosePlanGroup>,
    now: Long,
    contentPadding: PaddingValues,
    onRecordDose: (DoseItem) -> Unit,
    onRecordGroup: (List<String>) -> Unit,
    onRemoveItem: (DoseItem) -> Unit,
    onAddMedication: () -> Unit,
    onAddSupplement: () -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    showDemoMenu: Boolean,
    demoMode: TodayDemoMode,
    onDemoModeChange: (TodayDemoMode) -> Unit,
) {
    val timelinePlan = remember(plan, state.doseState.activeItems, now, demoMode) {
        demoPlanFor(demoMode, state.doseState.activeItems, now) ?: plan
    }
    val plannedByItemId = plan
        .flatMap { group -> group.entries.map { entry -> entry.item.id to entry } }
        .toMap()
    val plannedTimeByItemId = plan
        .flatMap { group -> group.itemIds.map { id -> id to group.scheduledAtMillis } }
        .toMap()
    val items = state.doseState.activeItems.sortedWith(
        compareBy<DoseItem> { plannedTimeByItemId[it.id] ?: Long.MAX_VALUE }
            .thenBy { it.kind.name }
            .thenBy { it.name },
    )
    val missedYesterday = missedYesterdayItems(state, now)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showDemoMenu) {
            item {
                TodayDemoMenu(
                    mode = demoMode,
                    onModeChange = onDemoModeChange,
                )
            }
        }
        item {
            TodayTimelineCard(
                state = state,
                plan = timelinePlan,
                now = now,
                notificationsAllowed = notificationsAllowed,
                onRequestNotifications = onRequestNotifications,
                onRecordGroup = onRecordGroup,
                forceComplete = demoMode == TodayDemoMode.Finished,
            )
        }
        if (missedYesterday.isNotEmpty()) {
            item {
                InfoPanel(
                    title = "Yesterday was reset",
                    body = "No confirmation was recorded yesterday for ${missedYesterday.joinToString(", ")}. The planner moved the next reminders into daytime instead of sending catch-up notifications overnight.",
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onAddMedication,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Medication, contentDescription = null)
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
            DoseCard(
                item = item,
                scheduledDose = plannedByItemId[item.id],
                now = now,
                onRecordDose = { onRecordDose(item) },
                onRemove = { onRemoveItem(item) },
            )
        }
    }
}

@Composable
private fun TodayTimelineCard(
    state: DoseKeeperUiState,
    plan: List<DosePlanGroup>,
    now: Long,
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onRecordGroup: (List<String>) -> Unit,
    forceComplete: Boolean = false,
) {
    val next = plan.firstOrNull()
    val overdue = next?.scheduledAtMillis?.let { it <= now } == true
    val completed = forceComplete || isSupplementDayComplete(state, now)
    var showCelebration by remember(completed) { mutableStateOf(false) }
    var showDoneText by remember(completed) { mutableStateOf(false) }

    LaunchedEffect(completed) {
        if (completed) {
            delay(460)
            showCelebration = true
            delay(1_050)
            showDoneText = true
        } else {
            showCelebration = false
            showDoneText = false
        }
    }

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFF8FBFF), Color(0xFFEFFAF4)),
                ),
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AnimatedVisibility(
                visible = !completed,
                enter = fadeIn(tween(260)) + expandVertically(),
                exit = fadeOut(tween(420)) + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    next?.let {
                        NextPlanBanner(
                            group = it,
                            now = now,
                            overdue = overdue,
                            onTaken = { onRecordGroup(it.itemIds) },
                        )
                    } ?: EmptyPanel("No reminders planned yet. Add supplements or set a medication timing preference.")
                    DayTimeline(
                        state = state,
                        plan = plan,
                        now = now,
                    )
                }
            }
            AnimatedVisibility(
                visible = showCelebration,
                enter = fadeIn(tween(260)) + expandVertically(),
                exit = fadeOut(tween(220)) + shrinkVertically(),
            ) {
                CompletionCelebration(showDoneText = showDoneText)
            }

            if (!notificationsAllowed) {
                Button(
                    onClick = onRequestNotifications,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13233F), contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable reminders")
                }
            }
        }
    }
}

@Composable
private fun NextPlanBanner(
    group: DosePlanGroup,
    now: Long,
    overdue: Boolean,
    onTaken: () -> Unit,
) {
    val primary = group.entries.first().item
    val accent = if (overdue) Color(0xFFBE123C) else Color(primary.accentColor)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = accent.copy(alpha = if (overdue) 0.13f else 0.11f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SupplementAvatar(item = primary, size = 44.dp, background = accent.copy(alpha = 0.18f), tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (overdue) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = compactGroupTitle(group),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (overdue) {
                        "Overdue ${formatDuration(now - group.scheduledAtMillis)}"
                    } else {
                        "${formatTime(group.scheduledAtMillis)} - in ${formatDuration(group.scheduledAtMillis - now)}"
                    },
                    color = accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onTaken,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Taken", maxLines = 1)
            }
        }
    }
}

@Composable
private fun DayTimeline(
    state: DoseKeeperUiState,
    plan: List<DosePlanGroup>,
    now: Long,
) {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val startMinute = timelineStartMinute(state.doseState.quietStartMinute, state.doseState.quietEndMinute)
    val endMinute = timelineEndMinute(state.doseState.quietStartMinute, state.doseState.quietEndMinute)
    val span = (endMinute - startMinute).coerceAtLeast(1)
    val visibleGroups = plan
        .filter {
            val zoned = Instant.ofEpochMilli(it.scheduledAtMillis).atZone(zone)
            zoned.toLocalDate() == today && zoned.hour * 60 + zoned.minute in startMinute..endMinute
        }
        .take(10)
    val nowMinute = Instant.ofEpochMilli(now).atZone(zone).let { it.hour * 60 + it.minute }
    val currentFraction = ((nowMinute - startMinute).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    val hourTicks = buildList {
        var tick = roundUpMinute(startMinute, 180)
        while (tick <= endMinute) {
            add(tick)
            tick += 180
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            Spacer(Modifier.weight(1f))
            Text(
                "${formatMinuteOfDay(startMinute)}-${formatMinuteOfDay(endMinute)}",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            val labelWidth = 92.dp
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barY = 32.dp.toPx()
                drawLine(
                    color = Color(0xFFD8E2EA),
                    start = Offset(0f, barY),
                    end = Offset(size.width, barY),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                hourTicks.forEach { tick ->
                    val fraction = ((tick - startMinute).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                    val x = size.width * fraction
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(x, barY - 8.dp.toPx()),
                        end = Offset(x, barY + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                visibleGroups.forEach { group ->
                    val minute = minuteOfDay(group.scheduledAtMillis)
                    val fraction = ((minute - startMinute).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                    val x = size.width * fraction
                    drawCircle(
                        color = Color(group.entries.first().item.accentColor),
                        radius = 5.dp.toPx(),
                        center = Offset(x, barY),
                    )
                }
                val nowX = size.width * currentFraction
                drawLine(
                    color = Color(0xFFBE123C),
                    start = Offset(nowX, 8.dp.toPx()),
                    end = Offset(nowX, 58.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Text(
                text = "Now ${formatTime(now)}",
                color = Color(0xFFBE123C),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .offset(
                        x = (maxWidth * currentFraction - 34.dp).coerceIn(0.dp, maxWidth - 68.dp),
                        y = 60.dp,
                    ),
            )
            visibleGroups.forEachIndexed { index, group ->
                val minute = minuteOfDay(group.scheduledAtMillis)
                val fraction = ((minute - startMinute).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                TimelineTick(
                    group = group,
                    modifier = Modifier.offset(
                        x = (maxWidth * fraction - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth),
                        y = if (index % 2 == 0) 82.dp else 100.dp,
                    ),
                )
            }
        }
        if (visibleGroups.isEmpty()) {
            Text("Nothing else in today's awake window.", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TimelineTick(group: DosePlanGroup, modifier: Modifier = Modifier) {
    val primary = group.entries.first().item
    val accent = Color(primary.accentColor)
    Surface(
        modifier = modifier.width(92.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SupplementAvatar(item = primary, size = 20.dp, background = accent.copy(alpha = 0.15f), tint = accent)
            Spacer(Modifier.width(5.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(formatTime(group.scheduledAtMillis), color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
                Text(
                    compactGroupTitle(group),
                    color = Color(0xFF111827),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TodayDemoMenu(
    mode: TodayDemoMode,
    onModeChange: (TodayDemoMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF2F6FED), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Timeline demo", fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            }
            val options = TodayDemoMode.entries
            options.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val selected = mode == option
                        if (selected) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onModeChange(option) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                            ) {
                                Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onModeChange(option) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                            ) {
                                Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CompletionCelebration(showDoneText: Boolean) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 4_600, easing = LinearEasing),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        contentAlignment = Alignment.Center,
    ) {
        ConfettiBurst(progress = progress.value)
        AnimatedVisibility(
            visible = showDoneText,
            enter = fadeIn(tween(520)) + expandVertically(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Good job", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF147D64))
                Text("That's everything planned for today.", color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ConfettiBurst(progress: Float) {
    val enter = segment(progress, 0f, 0.2f).easeOutCubic()
    val exit = segment(progress, 0.84f, 1f).easeInCubic()
    val popperYLift = 82f * enter
    val rollAway = 104f * exit
    val particles = remember {
        listOf(
            ConfettiParticle("✨", true, 0.16f, 0.56f, 0.46f, 0.00f, 0f),
            ConfettiParticle("🎊", true, 0.24f, 0.66f, 0.52f, 0.03f, -24f),
            ConfettiParticle("✦", true, 0.32f, 0.74f, 0.48f, 0.06f, 18f),
            ConfettiParticle("✨", true, 0.40f, 0.58f, 0.58f, 0.10f, -12f),
            ConfettiParticle("🎊", true, 0.48f, 0.70f, 0.54f, 0.14f, 28f),
            ConfettiParticle("💊", true, 0.55f, 0.60f, 0.50f, 0.18f, -30f),
            ConfettiParticle("💊", true, 0.34f, 0.62f, 0.58f, 0.22f, 42f),
            ConfettiParticle("💊", true, 0.62f, 0.76f, 0.48f, 0.26f, -46f),
            ConfettiParticle("💊", true, 0.70f, 0.68f, 0.52f, 0.30f, 36f),
            ConfettiParticle("✨", false, 0.16f, 0.56f, 0.46f, 0.00f, 0f),
            ConfettiParticle("🎊", false, 0.24f, 0.66f, 0.52f, 0.04f, 22f),
            ConfettiParticle("✦", false, 0.32f, 0.74f, 0.48f, 0.08f, -18f),
            ConfettiParticle("✨", false, 0.40f, 0.58f, 0.58f, 0.12f, 16f),
            ConfettiParticle("🎊", false, 0.48f, 0.70f, 0.54f, 0.16f, -26f),
            ConfettiParticle("💊", false, 0.55f, 0.60f, 0.50f, 0.20f, 34f),
            ConfettiParticle("💊", false, 0.34f, 0.62f, 0.58f, 0.24f, -38f),
            ConfettiParticle("💊", false, 0.62f, 0.76f, 0.48f, 0.28f, 48f),
            ConfettiParticle("💊", false, 0.70f, 0.68f, 0.52f, 0.32f, -34f),
        )
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val phase = segment(progress, 0.2f + particle.delay, 1f)
            val lift = segment(phase, 0f, 0.42f).easeOutCubic()
            val fall = segment(phase, 0.34f, 1f).easeInCubic()
            val grow = segment(phase, 0f, 0.42f).easeOutCubic()
            val originX = if (particle.fromLeft) maxWidth * 0.12f else maxWidth * 0.88f
            val direction = if (particle.fromLeft) 1f else -1f
            val x = originX + maxWidth * particle.spread * direction * phase.easeOutCubic()
            val y = maxHeight - 82.dp - maxHeight * particle.height * lift + maxHeight * particle.fall * fall
            val alpha = when {
                phase <= 0f -> 0f
                phase < 0.1f -> phase / 0.1f
                phase > 0.96f -> ((1f - phase) / 0.04f).coerceIn(0f, 1f)
                else -> 1f
            }
            Text(
                text = particle.emoji,
                modifier = Modifier.offset(
                    x = x,
                    y = y,
                ).graphicsLayer {
                    rotationZ = particle.rotation * phase
                    scaleX = 0.16f + 0.84f * grow
                    scaleY = 0.16f + 0.84f * grow
                    this.alpha = alpha
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            text = "🎉",
            modifier = Modifier
                .offset(
                    x = (-72f + 58f * enter - rollAway).dp,
                    y = maxHeight - 30.dp - popperYLift.dp + (28f * exit).dp,
                )
                .graphicsLayer {
                    rotationZ = -28f + 60f * enter - 160f * exit
                    alpha = (1f - exit).coerceIn(0f, 1f)
                },
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "🎉",
            modifier = Modifier
                .offset(
                    x = maxWidth + 8.dp + (-58f * enter + rollAway).dp,
                    y = maxHeight - 30.dp - popperYLift.dp + (28f * exit).dp,
                )
                .graphicsLayer {
                    scaleX = -1f
                    rotationZ = 28f - 60f * enter + 160f * exit
                    alpha = (1f - exit).coerceIn(0f, 1f)
                },
            style = MaterialTheme.typography.displaySmall,
        )
    }
}

@Composable
private fun DoseCard(
    item: DoseItem,
    scheduledDose: ScheduledDose?,
    now: Long,
    onRecordDose: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = Color(item.accentColor)
    val last = item.lastDoseAtMillis
    val plannedAt = scheduledDose?.scheduledAtMillis
    val intervalClearsAt = item.nextAllowedAtMillis()
    val plannedRemaining = plannedAt?.minus(now)
    val dueByPlan = plannedRemaining != null && plannedRemaining <= 0
    val progress = when {
        plannedAt != null && last != null && plannedAt > last -> ((now - last).toFloat() / (plannedAt - last).toFloat()).coerceIn(0f, 1f)
        plannedAt != null -> if (dueByPlan) 1f else 0.12f
        last != null && intervalClearsAt != null && intervalClearsAt > last -> ((now - last).toFloat() / (intervalClearsAt - last).toFloat()).coerceIn(0f, 1f)
        else -> 1f
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
                SupplementAvatar(item = item, size = 44.dp, background = accent.copy(alpha = 0.16f), tint = accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${formatInterval(item.intervalMinutes)} interval - ${timingLabel(item.timingPreference)}",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ${item.name}", tint = Color(0xFF94A3B8))
                }
            }

            Text(
                text = when {
                    plannedAt != null && dueByPlan -> "Due now"
                    plannedAt != null -> "Due in ${formatDuration(plannedAt - now)}"
                    last == null -> "Ready to start"
                    intervalClearsAt != null && intervalClearsAt > now -> "Available in ${formatDuration(intervalClearsAt - now)}"
                    else -> "Ready when needed"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (dueByPlan) Color(0xFF147D64) else Color(0xFF111827),
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
                    text = buildString {
                        if (plannedAt != null) {
                            append("Planned: ${formatDateTime(plannedAt)} - ${scheduledDose.reason}")
                        } else {
                            append(last?.let { "Last taken: ${formatDateTime(it)}" } ?: "Confirming this starts the next optimized plan.")
                        }
                        intervalClearsAt?.takeIf { it > now && plannedAt != null }?.let {
                            append(". Minimum interval clears ${formatDateTime(it)}")
                        }
                    },
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
            scheduledDose?.notes?.take(2)?.forEach {
                Text(it, color = Color(0xFF8A5A00), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlanScreen(
    state: DoseKeeperUiState,
    plan: List<DosePlanGroup>,
    contentPadding: PaddingValues,
    onAddSupplement: () -> Unit,
    onOpenQuietHours: () -> Unit,
    onEditItem: (DoseItem) -> Unit,
    onRemoveItem: (DoseItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                title = "Smart Plan",
                subtitle = "The optimizer groups compatible doses and spaces conflicts after every edit or confirmation.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onAddSupplement,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Supplement")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenQuietHours,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Quiet hours")
                }
            }
        }
        item {
            Text(
                text = "No reminders during ${formatMinuteOfDay(state.doseState.quietStartMinute)}-${formatMinuteOfDay(state.doseState.quietEndMinute)}.",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (plan.isEmpty()) {
            item { EmptyPanel("No planned reminders yet. Add supplements or confirm an as-needed medication.") }
        } else {
            items(plan, key = { "${it.scheduledAtMillis}-${it.itemIds.joinToString()}" }) { group ->
                PlanGroupCard(
                    group = group,
                    onEditItem = onEditItem,
                    onRemoveItem = onRemoveItem,
                )
            }
        }
    }
}

@Composable
private fun PlanGroupCard(
    group: DosePlanGroup,
    onEditItem: (DoseItem) -> Unit,
    onRemoveItem: (DoseItem) -> Unit,
) {
    val primary = group.entries.first().item
    val accent = Color(primary.accentColor)
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SupplementAvatar(item = primary, size = 46.dp, background = accent.copy(alpha = 0.15f), tint = accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatDateTime(group.scheduledAtMillis), color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                }
                if (group.entries.size == 1) {
                    IconButton(onClick = { onEditItem(primary) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${primary.name}", tint = Color(0xFF64748B))
                    }
                    IconButton(onClick = { onRemoveItem(primary) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove ${primary.name}", tint = Color(0xFF94A3B8))
                    }
                }
            }
            if (group.entries.size == 1) {
                val entry = group.entries.first()
                PlanInfoLine(
                    label = entry.reason,
                    detail = entry.notes.firstOrNull() ?: "Optimizer target: ${timingLabel(entry.item.timingPreference)}",
                )
            } else {
                group.entries.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "${entry.item.name}: ${entry.reason}",
                                color = Color(0xFF334155),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            entry.notes.firstOrNull()?.let {
                                Text(it, color = Color(0xFF8A5A00), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { onEditItem(entry.item) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit ${entry.item.name}", tint = Color(0xFF64748B))
                        }
                        IconButton(onClick = { onRemoveItem(entry.item) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove ${entry.item.name}", tint = Color(0xFF94A3B8))
                        }
                    }
                }
            }
            group.notes.forEach {
                Text(it, color = Color(0xFF8A5A00), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlanInfoLine(label: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FAFC),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = Color(0xFF334155), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HistoryScreen(
    state: DoseKeeperUiState,
    contentPadding: PaddingValues,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header(
                title = "History",
                subtitle = "Kept locally. Current retention: ${retentionLabel(state.doseState.historyRetentionDays)}.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(modifier = Modifier.weight(1f), onClick = onOpenSettings, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retention")
                }
                Button(modifier = Modifier.weight(1f), onClick = onClearHistory, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
        }
        if (state.doseState.history.isEmpty()) {
            item { EmptyPanel("No confirmations recorded yet.") }
        } else {
            items(state.doseState.history, key = { it.id }) { entry ->
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
            Text(if (entry.itemKind == DoseKind.Medication) "Med" else "Supp", color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun QuizScreen(
    state: DoseKeeperUiState,
    contentPadding: PaddingValues,
    answers: Map<String, Set<String>>,
    currentIndex: Int,
    addedTemplateIds: Set<String>,
    onAnswersChange: (Map<String, Set<String>>) -> Unit,
    onIndexChange: (Int) -> Unit,
    onAddedTemplateIdsChange: (Set<String>) -> Unit,
    onAddSupplements: (List<String>) -> Unit,
    onQuizCompleted: () -> Unit,
    quizCompletionAdShown: Boolean,
    onQuizCompletionAdShownChange: (Boolean) -> Unit,
) {
    val questions = QuizEngine.questionsFor(answers)
    val tags = QuizEngine.tagsFor(answers)
    val stopped = "stop_quiz" in tags || ("under_18" in tags && "q1" in answers)
    val finished = stopped || questions.isEmpty() || currentIndex >= questions.size

    LaunchedEffect(finished) {
        if (finished && !quizCompletionAdShown) {
            onQuizCompletionAdShownChange(true)
            onQuizCompleted()
        } else if (!finished) {
            onQuizCompletionAdShownChange(false)
        }
    }

    if (finished) {
        QuizResultsScreen(
            state = state,
            result = QuizEngine.evaluate(answers, state.templates),
            contentPadding = contentPadding,
            addedTemplateIds = addedTemplateIds,
            onAddedTemplateIdsChange = onAddedTemplateIdsChange,
            onAddSupplements = onAddSupplements,
            onRetake = {
                onQuizCompletionAdShownChange(false)
                onAnswersChange(emptyMap())
                onIndexChange(0)
                onAddedTemplateIdsChange(emptySet())
            },
        )
        return
    }

    val safeIndex = currentIndex.coerceIn(0, questions.lastIndex)
    val question = questions[safeIndex]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                title = "Supplement Quiz",
                subtitle = "A safety-first questionnaire that turns your answers into ranked, timing-aware supplement suggestions.",
            )
        }
        item {
            QuizQuestionCard(
                question = question,
                step = safeIndex + 1,
                total = questions.size,
                selectedValues = answers[question.id].orEmpty(),
                onSelect = { value ->
                    onAnswersChange(updateQuizAnswers(answers, question, value))
                },
                onBack = {
                    onIndexChange((safeIndex - 1).coerceAtLeast(0))
                },
                onNext = {
                    val nextTags = QuizEngine.tagsFor(answers)
                    if ("stop_quiz" in nextTags || ("under_18" in nextTags && "q1" in answers)) {
                        onIndexChange(QuizEngine.questionsFor(answers).size)
                    } else {
                        onIndexChange((safeIndex + 1).coerceAtMost(QuizEngine.questionsFor(answers).size))
                    }
                },
                canGoBack = safeIndex > 0,
            )
        }
        item {
            InfoPanel(
                title = "Educational guidance",
                body = "The quiz can help organize supplement timing and safety flags. It does not diagnose deficiencies or replace clinician, pharmacist, dietitian, veterinary, or product-label guidance.",
            )
        }
    }
}

@Composable
private fun QuizQuestionCard(
    question: QuizQuestion,
    step: Int,
    total: Int,
    selectedValues: Set<String>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    canGoBack: Boolean,
) {
    val progress = (step.toFloat() / total.toFloat()).coerceIn(0.05f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Step $step of $total", modifier = Modifier.weight(1f), color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                Text(question.section.uppercase(), color = Color(0xFF2F6FED), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color(0xFF2F6FED),
                trackColor = Color(0xFFE2E8F0),
            )
            Text(question.prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            if (question.type == QuizQuestionType.MultiSelect) {
                Text(
                    text = if (question.maxSelections < 10) "Choose up to ${question.maxSelections}." else "Select all that apply.",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            question.options.forEach { option ->
                QuizOptionRow(
                    label = option.label,
                    selected = option.value in selectedValues,
                    onClick = { onSelect(option.value) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = canGoBack,
                    onClick = onBack,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Back")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = selectedValues.isNotEmpty(),
                    onClick = onNext,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(if (step == total) "Results" else "Next")
                }
            }
        }
    }
}

@Composable
private fun QuizOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF2F6FED) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(20.dp),
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFFEAF3FF) else Color.White,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color(0xFF2F6FED) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(label, modifier = Modifier.weight(1f), color = Color(0xFF111827), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuizResultsScreen(
    state: DoseKeeperUiState,
    result: QuizResult,
    contentPadding: PaddingValues,
    addedTemplateIds: Set<String>,
    onAddedTemplateIdsChange: (Set<String>) -> Unit,
    onAddSupplements: (List<String>) -> Unit,
    onRetake: () -> Unit,
) {
    val templateById = state.templates.associateBy { it.id }
    val activeTemplateIds = state.doseState.activeItems.mapNotNull { it.supplementTemplateId }.toSet()
    val addableIds = result.allRecommendations
        .filter { it.addable && it.supplementId in templateById }
        .map { it.supplementId }
        .distinct()
    val newAddableIds = addableIds.filterNot { it in activeTemplateIds || it in addedTemplateIds }
    val addedRecommendations = addedTemplateIds.mapNotNull { id ->
        result.allRecommendations.firstOrNull { it.supplementId == id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                title = "Quiz Results",
                subtitle = "Best-fit items, test-first items, review warnings, and a simple timing plan.",
            )
        }
        item {
            QuizSummaryCard(result.summary, result.stoppedMessage)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = newAddableIds.isNotEmpty(),
                    onClick = {
                        onAddSupplements(newAddableIds)
                        onAddedTemplateIdsChange(addedTemplateIds + newAddableIds)
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            newAddableIds.isNotEmpty() -> "Add ${newAddableIds.size}"
                            addableIds.isNotEmpty() -> "Already added"
                            else -> "No auto-adds"
                        },
                        maxLines = 1,
                    )
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRetake,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Retake")
                }
            }
        }
        item {
            AddedSupplementsPanel(
                recommendations = addedRecommendations,
                templateById = templateById,
            )
        }
        item {
            RecommendationSection(
                title = "Likely useful",
                subtitle = "Strong fit with lower safety friction.",
                recommendations = result.likelyUseful,
                activeTemplateIds = activeTemplateIds,
                addedTemplateIds = addedTemplateIds,
                templateById = templateById,
                onAdd = { recommendation ->
                    onAddSupplements(listOf(recommendation.supplementId))
                    onAddedTemplateIdsChange(addedTemplateIds + recommendation.supplementId)
                },
            )
        }
        item {
            RecommendationSection(
                title = "Consider / optional",
                subtitle = "Reasonable or lower-priority options you can still add.",
                recommendations = result.consider,
                activeTemplateIds = activeTemplateIds,
                addedTemplateIds = addedTemplateIds,
                templateById = templateById,
                onAdd = { recommendation ->
                    onAddSupplements(listOf(recommendation.supplementId))
                    onAddedTemplateIdsChange(addedTemplateIds + recommendation.supplementId)
                },
            )
        }
        item {
            RecommendationSection(
                title = "Test first",
                subtitle = "Symptoms or context overlap with possible deficiencies; labs matter.",
                recommendations = result.testFirst,
                activeTemplateIds = activeTemplateIds,
                addedTemplateIds = addedTemplateIds,
                templateById = templateById,
                onAdd = {},
            )
        }
        item {
            RecommendationSection(
                title = "Review first",
                subtitle = "A medication, condition, or life-stage flag needs clinician/pharmacist review.",
                recommendations = result.reviewFirst,
                activeTemplateIds = activeTemplateIds,
                addedTemplateIds = addedTemplateIds,
                templateById = templateById,
                onAdd = {},
            )
        }
        item {
            RecommendationSection(
                title = "Avoid",
                subtitle = "Not appropriate to start from this quiz alone.",
                recommendations = result.avoid,
                activeTemplateIds = activeTemplateIds,
                addedTemplateIds = addedTemplateIds,
                templateById = templateById,
                onAdd = {},
            )
        }
        item {
            TimingPlanPanel(result.timingPlan)
        }
        if (result.labs.isNotEmpty()) {
            item {
                InfoPanel("Suggested checks", result.labs.joinToString(", "))
            }
        }
        if (result.safetyNotes.isNotEmpty()) {
            item {
                InfoPanel("Safety notes", result.safetyNotes.joinToString(" "))
            }
        }
    }
}

@Composable
private fun QuizSummaryCard(summary: String, stoppedMessage: String?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF13233F), Color(0xFF2F6FED), Color(0xFF1DBA91)),
                ),
            ),
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Based on your answers", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
            Text(stoppedMessage ?: summary, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "Likely, consider, and optional items can be added. Test-first and review-first items stay as guidance.",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AddedSupplementsPanel(
    recommendations: List<QuizRecommendation>,
    templateById: Map<String, SupplementTemplate>,
) {
    AnimatedVisibility(visible = recommendations.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFEFFAF4),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Added to your plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF147D64))
                recommendations.forEach { recommendation ->
                    val template = templateById[recommendation.supplementId]
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DBA91)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(recommendation.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F513D))
                            Text(template?.description ?: recommendation.why.firstOrNull().orEmpty(), color = Color(0xFF256B54), style = MaterialTheme.typography.bodySmall)
                            recommendation.why.take(2).forEach {
                                Text("Why: $it", color = Color(0xFF256B54), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationSection(
    title: String,
    subtitle: String,
    recommendations: List<QuizRecommendation>,
    activeTemplateIds: Set<String>,
    addedTemplateIds: Set<String>,
    templateById: Map<String, SupplementTemplate>,
    onAdd: (QuizRecommendation) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Text(subtitle, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        if (recommendations.isEmpty()) {
            EmptyPanel("No items in this group.")
        } else {
            recommendations.forEach { recommendation ->
                RecommendationCard(
                    recommendation = recommendation,
                    canAdd = recommendation.addable &&
                        recommendation.supplementId in templateById &&
                        recommendation.supplementId !in activeTemplateIds &&
                        recommendation.supplementId !in addedTemplateIds,
                    added = recommendation.supplementId in activeTemplateIds || recommendation.supplementId in addedTemplateIds,
                    onAdd = { onAdd(recommendation) },
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: QuizRecommendation,
    canAdd: Boolean,
    added: Boolean,
    onAdd: () -> Unit,
) {
    val statusColor = recommendation.status.statusColor()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.9f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.FoodBank, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(recommendation.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                    Text("Score ${recommendation.score}", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(recommendation.status.label) },
                )
            }
            recommendation.why.take(3).forEach {
                Text(it, color = Color(0xFF334155), style = MaterialTheme.typography.bodyMedium)
            }
            Text("Timing: ${recommendation.timing}", color = Color(0xFF475569), style = MaterialTheme.typography.bodySmall)
            recommendation.labs.takeIf { it.isNotEmpty() }?.let {
                Text("Labs: ${it.joinToString(", ")}", color = Color(0xFF8A5A00), style = MaterialTheme.typography.bodySmall)
            }
            recommendation.warnings.take(3).forEach {
                Text(it, color = Color(0xFF9F1239), style = MaterialTheme.typography.bodySmall)
            }
            if (recommendation.addable) {
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = canAdd,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(if (added) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (added) "Added" else "Add this")
                }
            }
        }
    }
}

@Composable
private fun TimingPlanPanel(timingPlan: Map<String, List<String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Timing plan")
        if (timingPlan.isEmpty()) {
            EmptyPanel("No timing plan generated.")
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.9f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    timingPlan.forEach { (bucket, items) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEAF3FF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Color(0xFF2F6FED), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bucket, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                Text(items.joinToString(" + "), color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesScreen(
    state: DoseKeeperUiState,
    contentPadding: PaddingValues,
    onAddRule: () -> Unit,
    onEditRule: (InteractionRule) -> Unit,
    onDeleteRule: (String) -> Unit,
) {
    val custom = state.doseState.customInteractions
    val defaults = state.interactions.filterNot { it.editable }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header(
                title = "Spacing Rules",
                subtitle = "Add or edit conflicts between your tracked supplements and medications.",
            )
        }
        item {
            Button(
                onClick = onAddRule,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add interaction")
            }
        }
        if (custom.isEmpty()) {
            item { EmptyPanel("No custom interactions yet.") }
        } else {
            items(custom, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    state = state,
                    editable = true,
                    onEdit = { onEditRule(rule) },
                    onDelete = { onDeleteRule(rule.id) },
                )
            }
        }
        item { SectionTitle("Default supplement rules") }
        items(defaults, key = { it.id }) { rule ->
            RuleRow(
                rule = rule,
                state = state,
                editable = false,
                onEdit = {},
                onDelete = {},
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: InteractionRule,
    state: DoseKeeperUiState,
    editable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${ruleLabel(rule.firstKey, state)} + ${ruleLabel(rule.secondKey, state)}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text("${ruleSpacingLabel(rule)} - ${rule.reason}", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            if (editable) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit rule")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete rule", tint = Color(0xFF94A3B8))
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
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
private fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, TimingPreference, Int?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var intervalMinutes by rememberSaveable { mutableStateOf(6 * 60) }
    var preference by rememberSaveable { mutableStateOf(TimingPreference.WithBreakfast) }
    var targetMinute by rememberSaveable { mutableStateOf(defaultTargetForPreference(preference)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onAdd(name, intervalMinutes, preference, targetMinute) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add medication") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 700.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                DoneTextField(value = name, onValueChange = { name = it }, label = "Name")
                }
                item {
                DurationWheelPicker(
                    label = "Minimum interval",
                    minutes = intervalMinutes,
                    onMinutesChange = { intervalMinutes = it },
                    minHours = 1,
                    maxHours = 72,
                )
                }
                item {
                TimingPreferenceSelector(
                    value = preference,
                    onChange = {
                        preference = it
                        targetMinute = defaultTargetForPreference(it)
                    },
                )
                }
                item {
                TimeWheelPicker(
                    label = "Preferred target",
                    minute = targetMinute,
                    onMinuteChange = { targetMinute = it },
                )
                }
                item {
                Text(
                    "The optimizer can move this target for spacing rules, late confirmations, and quiet hours.",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall,
                )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

@Composable
private fun AddSupplementDialog(
    state: DoseKeeperUiState,
    onDismiss: () -> Unit,
    onAddTemplate: (String) -> Unit,
    onAddCustom: (String, Int, TimingPreference, Int?, String) -> Unit,
) {
    val activeTemplateIds = state.doseState.items.mapNotNull { it.supplementTemplateId }.toSet()
    var customName by rememberSaveable { mutableStateOf("") }
    var customIntervalMinutes by rememberSaveable { mutableStateOf(24 * 60) }
    var customDescription by rememberSaveable { mutableStateOf("") }
    var preference by rememberSaveable { mutableStateOf(TimingPreference.WithBreakfast) }
    var targetMinute by rememberSaveable { mutableStateOf(defaultTargetForPreference(preference)) }
    var showCustomForm by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = state.templates.map { it.category }.distinct().sorted()
    val visibleTemplates = state.templates
        .filter { template ->
            val categoryMatches = selectedCategory == null || template.category == selectedCategory
            val query = searchQuery.trim()
            val queryMatches = query.isBlank() ||
                template.name.contains(query, ignoreCase = true) ||
                template.category.contains(query, ignoreCase = true) ||
                template.description.contains(query, ignoreCase = true) ||
                template.timingNotes.contains(query, ignoreCase = true) ||
                template.interactionNotes.contains(query, ignoreCase = true)
            categoryMatches && queryMatches
        }
        .sortedWith(compareBy<SupplementTemplate> { it.category }.thenBy { it.name })

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Add supplement") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 660.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showCustomForm) {
                    item {
                        TextButton(onClick = { showCustomForm = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Default supplement list")
                        }
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFFF8FAFC),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Add new supplement", fontWeight = FontWeight.SemiBold)
                                DoneTextField(value = customName, onValueChange = { customName = it }, label = "Name")
                                DurationWheelPicker(
                                    label = "Minimum interval",
                                    minutes = customIntervalMinutes,
                                    onMinutesChange = { customIntervalMinutes = it },
                                    minHours = 1,
                                    maxHours = 72,
                                )
                                TimingPreferenceSelector(
                                    value = preference,
                                    onChange = {
                                        preference = it
                                        targetMinute = defaultTargetForPreference(it)
                                    },
                                )
                                TimeWheelPicker(
                                    label = "Preferred target",
                                    minute = targetMinute,
                                    onMinuteChange = { targetMinute = it },
                                )
                                DoneTextField(value = customDescription, onValueChange = { customDescription = it }, label = "Short note")
                                Button(
                                    onClick = {
                                        onAddCustom(customName, customIntervalMinutes, preference, targetMinute, customDescription)
                                        customName = ""
                                        customDescription = ""
                                    },
                                    enabled = customName.isNotBlank(),
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add supplement")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showCustomForm = true },
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add new supplement")
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search supplements") },
                            shape = RoundedCornerShape(18.dp),
                        )
                    }
                    item {
                        CategorySelector(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onSelect = { selectedCategory = it },
                        )
                    }
                    item { SectionTitle("Default supplement library") }
                    if (visibleTemplates.isEmpty()) {
                        item { EmptyPanel("No supplements match that search.") }
                    }
                    items(visibleTemplates, key = { it.id }) { template ->
                        SupplementTemplateRow(
                            template = template,
                            active = template.id in activeTemplateIds,
                            onAdd = { onAddTemplate(template.id) },
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(0.98f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

@Composable
private fun CategorySelector(
    categories: List<String>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val labels = listOf<String?>(null) + categories
        labels.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    val selected = selectedCategory == category
                    val label = category ?: "All"
                    if (selected) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(category) },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(category) },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
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
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = if (active) Color(0xFFF1F5F9) else Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(template.accentColor).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(templateIcon(template), contentDescription = null, tint = Color(template.accentColor), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(template.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${template.category} - ${template.timingNotes}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Rules: ${template.interactionNotes}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF8A5A00),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(enabled = !active, onClick = onAdd) {
                Icon(if (active) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun TimingPreferenceSelector(value: TimingPreference, onChange: (TimingPreference) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Planner timing", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        TimingOptionGroup(
            options = listOf(
                TimingPreference.WithBreakfast,
                TimingPreference.WithLunch,
                TimingPreference.WithDinner,
                TimingPreference.Bedtime,
            ),
            value = value,
            onChange = onChange,
        )
    }
}

@Composable
private fun TimingOptionGroup(
    options: List<TimingPreference>,
    value: TimingPreference,
    onChange: (TimingPreference) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val selected = option == value
                    val color = timingColor(option)
                    if (selected) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { onChange(option) },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                        ) {
                            Icon(timingIcon(option), contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(timingLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onChange(option) },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Icon(timingIcon(option), contentDescription = null, modifier = Modifier.size(17.dp), tint = color)
                            Spacer(Modifier.width(6.dp))
                            Text(timingLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DoneTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun DurationWheelPicker(
    label: String,
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    minHours: Int,
    maxHours: Int,
) {
    val currentHours = (minutes / 60).coerceIn(minHours, maxHours)
    NumberWheelPicker(
        label = label,
        value = currentHours,
        range = minHours..maxHours,
        suffix = "h",
        onValueChange = { onMinutesChange(it * 60) },
    )
    if (minHours == 0) {
        Text(
            if (currentHours == 0) "0h makes this an advisory rule." else "Planner keeps these at least ${currentHours}h apart.",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TimeWheelPicker(
    label: String,
    minute: Int,
    onMinuteChange: (Int) -> Unit,
) {
    val safeMinute = minute.coerceIn(0, 23 * 60 + 59)
    var wheelHour by rememberSaveable { mutableStateOf(safeMinute / 60) }
    var wheelMinute by rememberSaveable { mutableStateOf(((safeMinute % 60) / 5) * 5) }

    LaunchedEffect(safeMinute) {
        val nextHour = safeMinute / 60
        val nextMinute = ((safeMinute % 60) / 5) * 5
        if (nextHour != wheelHour) wheelHour = nextHour
        if (nextMinute != wheelMinute) wheelMinute = nextMinute
    }

    LaunchedEffect(wheelHour, wheelMinute) {
        val nextMinuteOfDay = minuteOfDay(wheelHour, wheelMinute)
        if (nextMinuteOfDay != safeMinute) onMinuteChange(nextMinuteOfDay)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            Spacer(Modifier.weight(1f))
            Text(formatMinuteOfDay(minuteOfDay(wheelHour, wheelMinute)), fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberWheelPicker(
                label = "Hour",
                value = wheelHour,
                range = 0..23,
                suffix = "h",
                modifier = Modifier.weight(1f),
                onValueChange = { wheelHour = it },
            )
            NumberWheelPicker(
                label = "Minute",
                value = wheelMinute,
                range = 0..55,
                step = 5,
                suffix = "m",
                modifier = Modifier.weight(1f),
                onValueChange = { wheelMinute = it },
            )
        }
    }
}

@Composable
private fun NumberWheelPicker(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
) {
    val values = remember(range.first, range.last, step) {
        generateSequence(range.first) { it + step }
            .takeWhile { it <= range.last }
            .toList()
            .ifEmpty { listOf(range.first) }
    }
    val selectedIndex = values.indexOf(value.coerceIn(range.first, range.last)).takeIf { it >= 0 } ?: 0
    val baseIndex = remember(values.size) { WHEEL_CENTER_INDEX - (WHEEL_CENTER_INDEX % values.size) }
    val targetIndex = (baseIndex + selectedIndex).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    var programmaticScroll by remember { mutableStateOf(false) }
    val selectedValue by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val centeredItem = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }
            values[loopingIndex(centeredItem?.index ?: targetIndex, values.size)]
        }
    }

    LaunchedEffect(value, targetIndex) {
        if (!listState.isScrollInProgress && selectedValue != value) {
            programmaticScroll = true
            listState.animateScrollToItem(targetIndex)
            programmaticScroll = false
        }
    }

    LaunchedEffect(selectedValue, listState.isScrollInProgress, programmaticScroll) {
        if (!programmaticScroll && !listState.isScrollInProgress && selectedValue != value) {
            onValueChange(selectedValue)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentPadding = PaddingValues(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(WHEEL_ITEM_COUNT) { index ->
                    val itemValue = values[loopingIndex(index, values.size)]
                    Text(
                        text = "$itemValue$suffix",
                        modifier = Modifier.height(32.dp),
                        color = if (itemValue == selectedValue) Color(0xFF111827) else Color(0xFF94A3B8),
                        style = if (itemValue == selectedValue) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (itemValue == selectedValue) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .border(1.dp, Color(0xFFD7E0EA), RoundedCornerShape(14.dp)),
            )
        }
    }
}

@Composable
private fun HistorySettingsDialog(
    currentDays: Int?,
    onDismiss: () -> Unit,
    onSet: (Int?) -> Unit,
) {
    var customDays by rememberSaveable { mutableStateOf(currentDays ?: 30) }
    val options = listOf<Pair<String, Int?>>(
        "No clearing" to null,
        "30 days" to 30,
        "90 days" to 90,
        "180 days" to 180,
        "365 days" to 365,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSet(customDays.coerceAtLeast(1)) }) { Text("Use custom") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("History retention") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { (label, days) ->
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSet(days) },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(if (currentDays == days) "$label selected" else label)
                    }
                }
                NumberWheelPicker(
                    label = "Custom days",
                    value = customDays,
                    range = 1..730,
                    suffix = "d",
                    onValueChange = { customDays = it },
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun QuietHoursDialog(
    startMinute: Int,
    endMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var start by rememberSaveable(startMinute) { mutableStateOf(startMinute) }
    var end by rememberSaveable(endMinute) { mutableStateOf(endMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(start, end) },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Quiet hours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The smart planner avoids sending supplement reminders inside this window. Missed doses are moved to the next daytime slot.",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TimeWheelPicker(
                    label = "Quiet starts",
                    minute = start,
                    onMinuteChange = { start = it },
                )
                TimeWheelPicker(
                    label = "Quiet ends",
                    minute = end,
                    onMinuteChange = { end = it },
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun EditItemDialog(
    item: DoseItem,
    onDismiss: () -> Unit,
    onSave: (String, Int, TimingPreference, Int?, String) -> Unit,
) {
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var intervalMinutes by rememberSaveable(item.id) { mutableStateOf(item.intervalMinutes) }
    var preference by rememberSaveable(item.id) { mutableStateOf(item.timingPreference) }
    var targetMinute by rememberSaveable(item.id) { mutableStateOf(item.targetMinuteOfDay ?: defaultTargetForPreference(item.timingPreference)) }
    var description by rememberSaveable(item.id) { mutableStateOf(item.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, intervalMinutes, preference, targetMinute, description) },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "These are optimizer preferences. The final reminder can move to satisfy spacing rules, late confirmations, and quiet hours.",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall,
                )
                DoneTextField(value = name, onValueChange = { name = it }, label = "Name")
                DurationWheelPicker(
                    label = "Minimum interval",
                    minutes = intervalMinutes,
                    onMinutesChange = { intervalMinutes = it },
                    minHours = 1,
                    maxHours = 72,
                )
                TimingPreferenceSelector(
                    value = preference,
                    onChange = {
                        preference = it
                        targetMinute = defaultTargetForPreference(it)
                    },
                )
                TimeWheelPicker(
                    label = "Preferred target",
                    minute = targetMinute,
                    onMinuteChange = { targetMinute = it },
                )
                DoneTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun InteractionDialog(
    items: List<DoseItem>,
    existing: InteractionRule?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, Int, String) -> Unit,
) {
    var firstId by rememberSaveable(existing?.id, items.size) { mutableStateOf(existing?.firstKey ?: items.firstOrNull()?.id.orEmpty()) }
    var secondId by rememberSaveable(existing?.id, items.size) { mutableStateOf(existing?.secondKey ?: items.firstOrNull { it.id != firstId }?.id.orEmpty()) }
    var separationMinutes by rememberSaveable(existing?.id) { mutableStateOf(existing?.separationMinutes ?: 120) }
    var reason by rememberSaveable(existing?.id) { mutableStateOf(existing?.reason ?: "") }
    var pickingTarget by remember { mutableStateOf<InteractionPickTarget?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = firstId.isNotBlank() && secondId.isNotBlank() && firstId != secondId,
                onClick = { onSave(existing?.id, firstId, secondId, separationMinutes, reason) },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (existing == null) "Add interaction" else "Edit interaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (items.size < 2) {
                    Text("Add at least two medications or supplements before creating a spacing rule.", color = Color(0xFF64748B))
                } else {
                    Text("Choose two tracked items and the minimum spacing between them.", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                    ItemSelectButton(
                        label = "First item",
                        item = items.firstOrNull { it.id == firstId } ?: items.first(),
                        onClick = { pickingTarget = InteractionPickTarget.First },
                    )
                    ItemSelectButton(
                        label = "Second item",
                        item = items.firstOrNull { it.id == secondId } ?: items.first { it.id != firstId },
                        onClick = { pickingTarget = InteractionPickTarget.Second },
                    )
                    DurationWheelPicker(
                        label = "Minimum spacing",
                        minutes = separationMinutes,
                        onMinutesChange = { separationMinutes = it },
                        minHours = 0,
                        maxHours = 24,
                    )
                    DoneTextField(value = reason, onValueChange = { reason = it }, label = "Reason")
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
    )

    pickingTarget?.let { target ->
        val excludedId = if (target == InteractionPickTarget.First) secondId else firstId
        ItemPickerDialog(
            title = if (target == InteractionPickTarget.First) "Choose first item" else "Choose second item",
            items = items.filter { it.id != excludedId },
            selectedId = if (target == InteractionPickTarget.First) firstId else secondId,
            onDismiss = { pickingTarget = null },
            onSelect = { selected ->
                if (target == InteractionPickTarget.First) {
                    firstId = selected.id
                } else {
                    secondId = selected.id
                }
                pickingTarget = null
            },
        )
    }
}

@Composable
private fun ItemSelectButton(label: String, item: DoseItem, onClick: () -> Unit) {
    val color = Color(item.accentColor)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.14f),
        onClick = onClick,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                Text(item.name, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = color)
        }
    }
}

@Composable
private fun ItemPickerDialog(
    title: String,
    items: List<DoseItem>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (DoseItem) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleItems = items
        .filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.kind.name.contains(query, ignoreCase = true)
        }
        .sortedWith(compareBy<DoseItem> { it.kind.name }.thenBy { it.name })

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search tracked items") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (visibleItems.isEmpty()) {
                        item { EmptyPanel("No tracked items match that search.") }
                    }
                    items(visibleItems, key = { it.id }) { item ->
                        PickerItemRow(
                            item = item,
                            selected = item.id == selectedId,
                            onClick = { onSelect(item) },
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun PickerItemRow(item: DoseItem, selected: Boolean, onClick: () -> Unit) {
    val color = Color(item.accentColor)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) color else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(18.dp),
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) color.copy(alpha = 0.12f) else Color.White,
        onClick = onClick,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                Text(if (item.kind == DoseKind.Medication) "Medication" else "Supplement", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private enum class InteractionPickTarget {
    First,
    Second,
}

private enum class TodayDemoMode(val label: String) {
    Live("Live"),
    Due("Due med"),
    Overdue("Overdue"),
    Finished("Done"),
    Empty("Empty"),
}

private data class ConfettiParticle(
    val emoji: String,
    val fromLeft: Boolean,
    val spread: Float,
    val height: Float,
    val fall: Float,
    val delay: Float,
    val rotation: Float,
)

private enum class AppTab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Rounded.Home),
    Plan("Plan", Icons.Rounded.DateRange),
    History("History", Icons.Rounded.History),
    Quiz("Quiz", Icons.Rounded.Check),
    Rules("Rules", Icons.Rounded.Link),
}

private fun updateQuizAnswers(
    answers: Map<String, Set<String>>,
    question: QuizQuestion,
    value: String,
): Map<String, Set<String>> {
    val current = answers[question.id].orEmpty()
    val nextValues = when (question.type) {
        QuizQuestionType.SingleSelect -> setOf(value)
        QuizQuestionType.MultiSelect -> {
            val exclusive = value == "none" || value == "unsure"
            when {
                exclusive && value !in current -> setOf(value)
                exclusive -> emptySet()
                value in current -> current - value
                current.size >= question.maxSelections -> current
                else -> (current - "none" - "unsure") + value
            }
        }
    }
    val changed = if (nextValues.isEmpty()) answers - question.id else answers + (question.id to nextValues)
    val reachableQuestionIds = QuizEngine.questionsFor(changed).map { it.id }.toSet()
    return changed.filterKeys { it in reachableQuestionIds }
}

private fun RecommendationStatus.statusColor(): Color = when (this) {
    RecommendationStatus.LikelyUseful -> Color(0xFF147D64)
    RecommendationStatus.Consider -> Color(0xFF2F6FED)
    RecommendationStatus.Optional -> Color(0xFF64748B)
    RecommendationStatus.TestFirst -> Color(0xFF8A5A00)
    RecommendationStatus.ReviewFirst -> Color(0xFFB45309)
    RecommendationStatus.Avoid -> Color(0xFFBE123C)
}

@Composable
private fun SupplementAvatar(
    item: DoseItem,
    size: Dp,
    background: Color,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(supplementIcon(item), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.54f))
    }
}

private fun supplementIcon(item: DoseItem): ImageVector {
    if (item.kind == DoseKind.Medication) return Icons.Rounded.Medication
    return when {
        item.supplementTemplateId in setOf("vitamin_d", "vitamin_a", "vitamin_k") -> Icons.Rounded.WbSunny
        item.supplementTemplateId in setOf("magnesium", "calcium", "zinc", "iron", "selenium", "iodine") -> Icons.Rounded.Science
        item.supplementTemplateId in setOf("creatine", "collagen") -> Icons.Rounded.FitnessCenter
        item.supplementTemplateId in setOf("melatonin") -> Icons.Rounded.Bedtime
        item.supplementTemplateId in setOf("ashwagandha", "rhodiola", "turmeric", "lions_mane") -> Icons.Rounded.Spa
        item.supplementTemplateId in setOf("omega_3", "krill_oil", "algal_oil", "coq10") -> Icons.Rounded.Restaurant
        item.supplementTemplateId in setOf("berberine", "psyllium", "inositol") -> Icons.Rounded.Bolt
        item.supplementTemplateId in setOf("b12", "folate", "vitamin_c", "multivitamin", "choline", "biotin") -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.FoodBank
    }
}

private fun templateIcon(template: SupplementTemplate): ImageVector =
    supplementIcon(
        DoseItem(
            id = template.id,
            name = template.name,
            kind = DoseKind.Supplement,
            intervalMinutes = template.defaultIntervalMinutes,
            accentColor = template.accentColor,
            supplementTemplateId = template.id,
            timingPreference = template.timingPreference,
        ),
    )

private fun screenPadding(contentPadding: PaddingValues): PaddingValues = PaddingValues(
    start = 18.dp,
    top = contentPadding.calculateTopPadding() + 18.dp,
    end = 18.dp,
    bottom = contentPadding.calculateBottomPadding() + 18.dp,
)

private fun hoursToMinutes(value: String): Int =
    ((value.toFloatOrNull() ?: 1f) * 60f).roundToInt().coerceAtLeast(30)

private fun parseTime(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
    val minute = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
    return minuteOfDay(hour, minute)
}

private fun defaultTargetForPreference(value: TimingPreference): Int = when (value) {
    TimingPreference.Anytime -> minuteOfDay(9)
    TimingPreference.WithBreakfast -> minuteOfDay(8)
    TimingPreference.WithLunch -> minuteOfDay(12, 30)
    TimingPreference.WithDinner -> minuteOfDay(18, 30)
    TimingPreference.WithFood -> minuteOfDay(12, 30)
    TimingPreference.EmptyStomach -> minuteOfDay(10, 30)
    TimingPreference.Bedtime -> minuteOfDay(21, 30)
}

private fun formatMinuteOfDay(minute: Int): String =
    LocalTime.of(minute.coerceIn(0, 23 * 60 + 59) / 60, minute.coerceIn(0, 23 * 60 + 59) % 60)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun minuteOfDay(millis: Long): Int {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return zoned.hour * 60 + zoned.minute
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun timelineStartMinute(quietStartMinute: Int, quietEndMinute: Int): Int =
    if (quietStartMinute > quietEndMinute) quietEndMinute else 0

private fun timelineEndMinute(quietStartMinute: Int, quietEndMinute: Int): Int =
    if (quietStartMinute > quietEndMinute) quietStartMinute else 23 * 60 + 59

private fun roundUpMinute(value: Int, step: Int): Int =
    ((value + step - 1) / step) * step

private fun compactGroupTitle(group: DosePlanGroup): String {
    val names = group.entries.map { compactDoseName(it.item.name) }
    return when {
        names.isEmpty() -> "Reminder"
        names.size == 1 -> names.first()
        names.size == 2 -> names.joinToString(" + ")
        else -> "${names.take(2).joinToString(" + ")} +${names.size - 2}"
    }
}

private fun compactDoseName(name: String): String =
    name
        .replace("Vitamin ", "Vit ")
        .replace("Omega-3 ", "O3 ")
        .replace("monohydrate", "", ignoreCase = true)
        .replace("peptides", "", ignoreCase = true)
        .trim()

private fun loopingIndex(index: Int, size: Int): Int =
    ((index % size) + size) % size

private fun segment(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun Float.easeOutCubic(): Float {
    val inverse = 1f - this
    return 1f - inverse * inverse * inverse
}

private fun Float.easeInCubic(): Float = this * this * this

private fun missedYesterdayItems(state: DoseKeeperUiState, now: Long): List<String> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val yesterday = today.minusDays(1)
    val confirmedYesterday = state.doseState.history
        .filter { Instant.ofEpochMilli(it.takenAtMillis).atZone(zone).toLocalDate() == yesterday }
        .map { it.itemId }
        .toSet()

    return state.doseState.activeItems
        .filter { it.kind == DoseKind.Supplement && it.intervalMinutes <= 36 * 60 }
        .filter { it.id !in confirmedYesterday }
        .filter {
            val lastDate = it.lastDoseAtMillis?.let { millis -> Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() }
            lastDate != null && lastDate < yesterday
        }
        .map { it.name }
        .take(4)
}

private fun isSupplementDayComplete(state: DoseKeeperUiState, now: Long): Boolean {
    val dailySupplements = state.doseState.activeItems
        .filter { it.kind == DoseKind.Supplement && it.intervalMinutes <= 36 * 60 }
    if (dailySupplements.isEmpty()) return false

    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val confirmedToday = state.doseState.history
        .filter { Instant.ofEpochMilli(it.takenAtMillis).atZone(zone).toLocalDate() == today }
        .map { it.itemId }
        .toSet()

    return dailySupplements.all { it.id in confirmedToday }
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

private fun formatInterval(minutes: Int): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours > 0 && remaining > 0 -> "${hours}h ${remaining}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))

private fun timingLabel(value: TimingPreference): String = when (value) {
    TimingPreference.Anytime -> "Anytime"
    TimingPreference.WithBreakfast -> "Breakfast"
    TimingPreference.WithLunch -> "Lunch"
    TimingPreference.WithDinner -> "Dinner"
    TimingPreference.WithFood -> "With food"
    TimingPreference.EmptyStomach -> "Empty stomach"
    TimingPreference.Bedtime -> "Bedtime"
}

private fun timingIcon(value: TimingPreference): ImageVector = when (value) {
    TimingPreference.WithBreakfast -> Icons.Rounded.WbSunny
    TimingPreference.WithLunch -> Icons.Rounded.Restaurant
    TimingPreference.WithDinner -> Icons.Rounded.FoodBank
    TimingPreference.Bedtime -> Icons.Rounded.Bedtime
    TimingPreference.Anytime,
    TimingPreference.WithFood,
    TimingPreference.EmptyStomach -> Icons.Rounded.Schedule
}

private fun timingColor(value: TimingPreference): Color = when (value) {
    TimingPreference.WithBreakfast -> Color(0xFFFFB86B)
    TimingPreference.WithLunch -> Color(0xFF2F6FED)
    TimingPreference.WithDinner -> Color(0xFF1DBA91)
    TimingPreference.Bedtime -> Color(0xFF5E60CE)
    TimingPreference.Anytime,
    TimingPreference.WithFood,
    TimingPreference.EmptyStomach -> Color(0xFF64748B)
}

private fun retentionLabel(days: Int?): String = when (days) {
    null -> "no automatic clearing"
    1 -> "1 day"
    else -> "$days days"
}

private fun ruleLabel(key: String, state: DoseKeeperUiState): String =
    SupplementScheduler.labelForRuleKey(key, state.doseState.activeItems, state.templates)

private fun ruleSpacingLabel(rule: InteractionRule): String =
    if (rule.separationMinutes <= 0) "Advisory" else "${formatInterval(rule.separationMinutes)} apart"

private fun demoPlanFor(mode: TodayDemoMode, items: List<DoseItem>, now: Long): List<DosePlanGroup>? = when (mode) {
    TodayDemoMode.Live -> null
    TodayDemoMode.Empty,
    TodayDemoMode.Finished -> emptyList()
    TodayDemoMode.Due -> {
        val item = items.firstOrNull { it.kind == DoseKind.Medication } ?: demoItem(
            id = "demo-med",
            name = "Migraine relief",
            kind = DoseKind.Medication,
            accent = 0xFF2F6FED,
        )
        listOf(demoGroup(item, now, "demo due now"))
    }
    TodayDemoMode.Overdue -> {
        val item = items.firstOrNull { it.kind == DoseKind.Supplement } ?: demoItem(
            id = "demo-iron",
            name = "Iron",
            kind = DoseKind.Supplement,
            accent = 0xFFEF5DA8,
        )
        listOf(demoGroup(item, now - 45 * 60_000L, "demo overdue slot"))
    }
}

private fun demoItem(id: String, name: String, kind: DoseKind, accent: Long): DoseItem = DoseItem(
    id = id,
    name = name,
    kind = kind,
    intervalMinutes = 6 * 60,
    accentColor = accent,
    timingPreference = TimingPreference.WithBreakfast,
)

private fun demoGroup(item: DoseItem, scheduledAtMillis: Long, reason: String): DosePlanGroup = DosePlanGroup(
    scheduledAtMillis = scheduledAtMillis,
    entries = listOf(
        ScheduledDose(
            item = item,
            scheduledAtMillis = scheduledAtMillis,
            reason = reason,
            notes = listOf("Demo mode only."),
        ),
    ),
    notes = listOf("Demo mode only."),
)

private const val PLAN_REFRESH_MILLIS = 10 * 60 * 1_000L
private const val WHEEL_ITEM_COUNT = 100_000
private const val WHEEL_CENTER_INDEX = WHEEL_ITEM_COUNT / 2
private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
