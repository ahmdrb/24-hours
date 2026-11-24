package com.twentyfourhours.app

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val Background = Color(0xFF0D0E11)
private val SurfaceDark = Color(0xFF17191E)
private val Raised = Color(0xFF20232A)
private val TextPrimary = Color(0xFFF4F3F7)
private val TextMuted = Color(0xFF858B98)
private val Accent = Color(0xFFB7A6FF)
private val RingTrack = Color(0xFF202124)

// Rich jewel tones used consistently by the activity picker, cards, and clock ring.
private val Palette = listOf(
    Color(0xFF167B83), // Deep teal
    Color(0xFF356FA8), // Ocean blue
    Color(0xFF9B702F), // Antique amber
    Color(0xFF694A9B), // Deep violet
    Color(0xFFA64E68), // Muted rose
    Color(0xFF43858A), // Muted cyan
    Color(0xFF66717E), // Slate
    Color(0xFFA17A2D), // Ochre
    Color(0xFFAC6245), // Terracotta
    Color(0xFF824A68), // Plum
    Color(0xFF34785B), // Forest green
    Color(0xFF25677D), // Deep cyan
    Color(0xFF4B579A), // Indigo
    Color(0xFF80654B), // Warm brown
)

// Existing saved activities keep their original color values. Map those known palette
// colors to the corresponding new jewel tone so existing schedules inherit the redesign.
private val LegacyPalette = listOf(
    Color(0xFF83D6B1), Color(0xFF83B8F5), Color(0xFFF0BD78),
    Color(0xFFC1A0F5), Color(0xFFF08D9A), Color(0xFF78C8D2), Color(0xFFAAB2BE),
    Color(0xFFFFD166), Color(0xFFE89A74), Color(0xFFC06C84),
    Color(0xFF6F9E8A), Color(0xFF558E9B), Color(0xFF7584BC), Color(0xFFA58D72),
)

private data class ActivityBlock(
    val id: Long,
    val title: String,
    val start: Int,
    val end: Int,
    val color: String,
    val subactivities: List<String> = emptyList(),
)

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(13, 14, 17)
        window.navigationBarColor = AndroidColor.rgb(13, 14, 17)
        window.decorView.systemUiVisibility = 0
        setContent { PlannerApp() }
    }
}

@Composable
private fun PlannerApp() {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val activities = remember(selectedDate) {
        mutableStateListOf<ActivityBlock>().apply { addAll(loadActivities(context, selectedDate)) }
    }

    var editorOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ActivityBlock?>(null) }
    var suggestedStart by remember { mutableIntStateOf(6 * 60) }
    var suggestedEnd by remember { mutableIntStateOf(7 * 60) }
    var currentMinute by remember {
        mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute })
    }

    fun editAt(minute: Int) {
        val found = activities.firstOrNull { contains(it, minute) }
        selected = found
        if (found != null) {
            suggestedStart = found.start
            suggestedEnd = found.end
        } else {
            suggestedStart = (minute / 60) * 60
            suggestedEnd = min(suggestedStart + 60, 1440)
        }
        editorOpen = true
    }

    fun persistUpdatedActivities(updated: List<ActivityBlock>) {
        activities.clear()
        activities.addAll(updated)
        saveActivities(context, selectedDate, updated)
    }

    val planned = activities.sumOf { it.end - it.start }.coerceIn(0, 1440)
    val free = 1440 - planned
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val tomorrow = today.plusDays(1)
    val dayLabel = when {
        selectedDate == today -> "TODAY"
        selectedDate == yesterday -> "YESTERDAY"
        selectedDate == tomorrow -> "TOMORROW"
        selectedDate.isBefore(yesterday) -> "PAST DAY"
        else -> "FUTURE DAY"
    }
    val planHeading = when {
        selectedDate == today -> "TODAY'S PLAN"
        selectedDate == yesterday -> "YESTERDAY'S PLAN"
        selectedDate == tomorrow -> "TOMORROW'S PLAN"
        selectedDate.isBefore(yesterday) -> "PAST DAY'S PLAN"
        else -> "FUTURE DAY'S PLAN"
    }
    val isToday = selectedDate == today

    LaunchedEffect(isToday) {
        if (isToday) {
            while (true) {
                val now = LocalTime.now()
                currentMinute = now.hour * 60 + now.minute
                delay(1_000L)
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, onPrimary = Color(0xFF17131F), background = Background,
        surface = SurfaceDark, onSurface = TextPrimary, secondary = TextMuted,
    )) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Column(
                Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(dayLabel, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
                        Row(
                            Modifier.clip(RoundedCornerShape(9.dp)).clickable { showDatePicker = true }.padding(vertical = 3.dp, horizontal = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Choose date", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Box(
                    Modifier.fillMaxWidth().pointerInput(selectedDate) {
                        var dragDistance = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                dragDistance += amount
                                change.consume()
                            },
                            onDragEnd = {
                                val threshold = 56.dp.toPx()
                                if (dragDistance > threshold) {
                                    selectedDate = selectedDate.plusDays(1).coerceIn(yesterday, tomorrow)
                                    editorOpen = false
                                } else if (dragDistance < -threshold) {
                                    selectedDate = selectedDate.minusDays(1).coerceIn(yesterday, tomorrow)
                                    editorOpen = false
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    DayDial(
                        activities = activities,
                        showCurrentTime = isToday,
                        currentMinute = currentMinute,
                        onTap = ::editAt,
                    )
                    Column(
                        Modifier.fillMaxWidth(0.56f).aspectRatio(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("☾", color = Color(0xFF9699FF), fontSize = 17.sp, lineHeight = 19.sp)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                selectedDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                                    .uppercase(Locale.getDefault()),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp,
                            )
                            Text("24", color = TextPrimary, fontSize = 48.sp, lineHeight = 49.sp, fontWeight = FontWeight.Light)
                            Text("HOURS", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Medium)
                        }
                        Text("☀", color = Color(0xFFFFD166), fontSize = 16.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceDark).padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Metric("PLANNED", duration(planned), Accent)
                    Box(Modifier.width(1.dp).height(30.dp).background(RingTrack))
                    Metric("FREE TIME", duration(free), TextPrimary)
                    Box(Modifier.width(1.dp).height(30.dp).background(RingTrack))
                    Metric("ACTIVITIES", activities.size.toString(), TextPrimary)
                }

                Spacer(Modifier.height(25.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(planHeading, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.height(10.dp))

                if (activities.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceDark).padding(vertical = 22.dp, horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("A day with room for anything.", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text("Tap any point on the ring to add an activity.", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activities.sortedBy { it.start }.forEach { block ->
                            ActivityRow(block) { editAt(block.start) }
                        }
                    }
                }

                Spacer(Modifier.height(17.dp))
                Button(
                    onClick = { editAt(nextFreeMinute(activities)) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text("＋  Add activity", color = Color(0xFF17131F), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        editorOpen = false
                    }
                    showDatePicker = false
                }) { Text("Done", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextMuted) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (editorOpen) {
        ActivityEditor(
            existing = selected,
            initialStart = suggestedStart,
            initialEnd = suggestedEnd,
            allActivities = activities.toList(),
            onDismiss = { editorOpen = false },
            onSave = { updated ->
                val next = activities.toMutableList()
                val index = next.indexOfFirst { it.id == updated.id }
                if (index >= 0) next[index] = updated else next.add(updated)
                persistUpdatedActivities(next)
                editorOpen = false
            },
            onDelete = { id ->
                persistUpdatedActivities(activities.filterNot { it.id == id })
                editorOpen = false
            },
        )
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActivityRow(block: ActivityBlock, onClick: () -> Unit) {
    val color = parseColor(block.color)
    val foreground = readableTextColor(color)
    val badgeBackground = if (foreground == Color.White) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                block.title,
                color = foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${timeLabel(block.start)} – ${timeLabel(block.end)}",
                color = foreground.copy(alpha = 0.76f),
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.clip(RoundedCornerShape(9.dp))
                .background(badgeBackground)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                duration(block.end - block.start),
                color = foreground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DayDial(
    activities: List<ActivityBlock>,
    showCurrentTime: Boolean,
    currentMinute: Int,
    onTap: (Int) -> Unit,
) {
    Canvas(
        Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f).pointerInput(activities) {
            detectTapGestures { tap ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val dx = tap.x - cx
                val dy = tap.y - cy
                val distance = sqrt(dx * dx + dy * dy)
                val radius = minOf(size.width, size.height).toFloat() * 0.36f
                if (distance in (radius * 0.66f)..(radius * 1.35f)) {
                    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    val clockAngle = (degrees + 90f + 360f) % 360f
                    onTap(((clockAngle / 360f) * 1440f).roundToInt().coerceIn(0, 1439))
                }
            }
        },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.36f
        val stroke = 24.dp.toPx()
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // Charcoal unplanned time and sharp, flat-ended activity segments.
        drawArc(
            RingTrack, -90f, 360f, false, topLeft, arcSize,
            style = Stroke(stroke, cap = StrokeCap.Butt),
        )

        activities.forEach { item ->
            val color = parseColor(item.color)
            val first = min(item.end - item.start, 1440 - item.start)
            activityArc(color, item.start, first, topLeft, arcSize, stroke)
            val remainder = item.end - item.start - first
            if (remainder > 0) activityArc(color, 0, remainder, topLeft, arcSize, stroke)
        }

        // Tick marks now sit wholly inside the ring band instead of protruding
        // into the surrounding background. Five-minute marks are short; quarters
        // and hours reach progressively farther toward the ring's outer edge.
        val tickInner = radius - stroke * 0.5f + 1.dp.toPx()
        for (minute in 0 until 1440 step 5) {
            val isHour = minute % 60 == 0
            val isQuarter = minute % 15 == 0
            val angle = Math.toRadians((-90f + minute / 1440f * 360f).toDouble())
            val tickLength = when {
                isHour -> stroke - 2.dp.toPx()
                isQuarter -> 12.dp.toPx()
                else -> 5.dp.toPx()
            }
            val outer = tickInner + tickLength
            val segment = activities.firstOrNull { contains(it, minute) }
            val baseColor = segment?.let { parseColor(it.color) } ?: Color(0xFF85868D)
            val tickColor = baseColor.copy(
                alpha = when {
                    isHour -> 0.98f
                    isQuarter -> 0.76f
                    else -> if (segment != null) 0.52f else 0.40f
                }
            )
            drawLine(
                tickColor,
                Offset(center.x + cos(angle).toFloat() * tickInner, center.y + sin(angle).toFloat() * tickInner),
                Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = when {
                    isHour -> 1.55.dp.toPx()
                    isQuarter -> 1.05.dp.toPx()
                    else -> 0.75.dp.toPx()
                },
                cap = StrokeCap.Butt,
            )
        }

        // Three-hour labels remain clean and legible around the centerline.
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(238, 238, 242)
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.toPx()
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        }
        listOf(0, 3, 6, 9, 12, 15, 18, 21).forEach { hour ->
            val angle = Math.toRadians((-90f + hour * 15f).toDouble())
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%02d", hour),
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius + 3.5.dp.toPx(),
                labelPaint,
            )
        }

        // Both the indicator line and pill use the same minute state, refreshed
        // from LocalTime every second by PlannerApp while today's plan is visible.
        if (showCurrentTime) {
            val hour = (currentMinute / 60).coerceIn(0, 23)
            val minute = currentMinute % 60
            val angleDegrees = -90f + currentMinute / 1440f * 360f
            val angle = Math.toRadians(angleDegrees.toDouble())
            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()

            val lineInner = radius - stroke * 0.5f - 1.dp.toPx()
            val lineOuter = radius + stroke * 0.5f + 13.dp.toPx()
            drawLine(
                Color.White,
                Offset(center.x + cosAngle * lineInner, center.y + sinAngle * lineInner),
                Offset(center.x + cosAngle * lineOuter, center.y + sinAngle * lineOuter),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Butt,
            )

            val timeText = String.format(Locale.US, "%02d:%02d", hour, minute)
            val pillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            val pillHeight = 18.dp.toPx()
            val pillWidth = pillPaint.measureText(timeText) + 12.dp.toPx()
            val desiredRadius = radius + stroke * 0.5f + 25.dp.toPx()
            val pillCenterX = (center.x + cosAngle * desiredRadius)
                .coerceIn(pillWidth / 2f + 2.dp.toPx(), size.width - pillWidth / 2f - 2.dp.toPx())
            val pillCenterY = (center.y + sinAngle * desiredRadius)
                .coerceIn(pillHeight / 2f + 2.dp.toPx(), size.height - pillHeight / 2f - 2.dp.toPx())
            val pillRect = android.graphics.RectF(
                pillCenterX - pillWidth / 2f,
                pillCenterY - pillHeight / 2f,
                pillCenterX + pillWidth / 2f,
                pillCenterY + pillHeight / 2f,
            )
            drawContext.canvas.nativeCanvas.drawRoundRect(
                pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint,
            )
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(25, 26, 30)
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            val baseline = pillCenterY - (textPaint.ascent() + textPaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(timeText, pillCenterX, baseline, textPaint)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.activityArc(
    color: Color, start: Int, duration: Int, topLeft: Offset, size: Size, stroke: Float,
) {
    if (duration <= 0) return
    val sweep = duration / 1440f * 360f
    drawArc(color, -90f + start / 1440f * 360f, sweep, false, topLeft, size, style = Stroke(stroke, cap = StrokeCap.Butt))
}

@Composable
private fun ActivityEditor(
    existing: ActivityBlock?, initialStart: Int, initialEnd: Int, allActivities: List<ActivityBlock>,
    onDismiss: () -> Unit, onSave: (ActivityBlock) -> Unit, onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(existing?.id, initialStart) { mutableStateOf(existing?.title.orEmpty()) }
    var start by remember(existing?.id, initialStart) { mutableIntStateOf(initialStart) }
    var end by remember(existing?.id, initialEnd) { mutableIntStateOf(initialEnd) }
    var selectedColor by remember(existing?.id) { mutableStateOf(parseColor(existing?.color ?: colorHex(Palette.first()))) }
    var subactivities by remember(existing?.id, initialStart) { mutableStateOf(existing?.subactivities ?: emptyList()) }
    var editingSubactivityIndex by remember(existing?.id, initialStart) { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickTime(isStart: Boolean) {
        val current = if (isStart) start else end % 1440
        TimePickerDialog(context, { _, hour, minute ->
            val picked = hour * 60 + minute
            if (isStart) {
                val oldEnd = end % 1440
                start = picked
                end = if (oldEnd <= picked) oldEnd + 1440 else oldEnd
            } else end = if (picked <= start) picked + 1440 else picked
            error = null
        }, current / 60, current % 60, false).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Raised,
        title = { Text(if (existing == null) "New activity" else "Edit activity", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Activity name") },
                    placeholder = { Text("e.g. Exercise") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextMuted,
                        cursorColor = Accent,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = RingTrack,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = TextMuted,
                        focusedPlaceholderColor = TextMuted,
                        unfocusedPlaceholderColor = TextMuted,
                    ),
                )
                Text("TIME", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimeButton("START", timeLabel(start), Modifier.weight(1f)) { pickTime(true) }
                    Text("—", color = TextMuted)
                    TimeButton("END", timeLabel(end), Modifier.weight(1f)) { pickTime(false) }
                }

                Text("COLOR", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Palette.chunked(7).forEach { rowColors ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            rowColors.forEach { color ->
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(color)
                                        .then(if (selectedColor == color) Modifier.border(2.dp, TextPrimary, CircleShape) else Modifier)
                                        .clickable { selectedColor = color },
                                )
                            }
                        }
                    }
                }

                Divider(color = RingTrack)
                Text(
                    "SUBACTIVITIES",
                    modifier = Modifier.clickable {
                        subactivities = subactivities + ""
                        editingSubactivityIndex = subactivities.lastIndex
                    },
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
                if (subactivities.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        subactivities.forEachIndexed { index, subactivity ->
                            SubactivityPill(
                                text = subactivity,
                                color = selectedColor,
                                isEditing = editingSubactivityIndex == index,
                                onTextChange = { value ->
                                    subactivities = subactivities.mapIndexed { itemIndex, old ->
                                        if (itemIndex == index) value else old
                                    }
                                },
                                onSwipeLeft = {
                                    subactivities = subactivities.filterIndexed { itemIndex, _ -> itemIndex != index }
                                    editingSubactivityIndex = -1
                                },
                                onSwipeRight = { editingSubactivityIndex = index },
                            )
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF9AA7), fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = title.trim()
                when {
                    clean.isEmpty() -> error = "Give this activity a name."
                    end <= start -> error = "The end time must be after the start time."
                    end - start > 1440 -> error = "An activity can last up to 24 hours."
                    allActivities.any { it.id != existing?.id && overlaps(start, end, it) } -> error = "That time overlaps another activity."
                    else -> onSave(ActivityBlock(existing?.id ?: System.currentTimeMillis(), clean, start, end, colorHex(selectedColor), subactivities.map { it.trim() }.filter { it.isNotEmpty() }))
                }
            }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("Save", color = Color(0xFF17131F), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) TextButton(onClick = { onDelete(existing.id) }) { Text("Delete", color = Color(0xFFFF9AA7)) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        },
    )
}

@Composable
private fun SubactivityPill(
    text: String,
    color: Color,
    isEditing: Boolean,
    onTextChange: (String) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val foreground = readableTextColor(color)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(color)
            .pointerInput(isEditing) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val threshold = 38.dp.toPx()
                        when {
                            dragDistance <= -threshold -> onSwipeLeft()
                            dragDistance >= threshold -> onSwipeRight()
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEditing) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(
                    color = foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(foreground),
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text("Subactivity name", color = foreground.copy(alpha = 0.62f), fontSize = 12.sp)
                        }
                        innerTextField()
                    }
                },
            )
        } else {
            Text(
                text.ifBlank { " " },
                modifier = Modifier.weight(1f),
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun readableTextColor(background: Color): Color {
    fun linearize(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f
        else ((channel + 0.055f) / 1.055f).pow(2.4f)

    val luminance = 0.2126f * linearize(background.red) +
        0.7152f * linearize(background.green) +
        0.0722f * linearize(background.blue)

    // Pick the higher-contrast foreground at the WCAG black/white crossover.
    return if (luminance > 0.179f) Color(0xFF1B1C21) else Color.White
}

@Composable
private fun TimeButton(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(SurfaceDark).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun nextFreeMinute(items: List<ActivityBlock>): Int {
    for (offset in 0 until 24) {
        val minute = ((6 + offset) % 24) * 60
        if (items.none { contains(it, minute) }) return minute
    }
    return 6 * 60
}

private fun contains(block: ActivityBlock, minute: Int): Boolean = ((minute - block.start + 1440) % 1440) < (block.end - block.start)

private fun overlaps(start: Int, end: Int, other: ActivityBlock): Boolean = listOf(-1440, 0, 1440).any { offset ->
    start < other.end + offset && end > other.start + offset
}

private fun timeLabel(total: Int): String = LocalTime.of((total % 1440) / 60, total % 60)
    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private fun duration(total: Int): String {
    val h = total / 60
    val m = total % 60
    return when { h == 0 -> "${m}m"; m == 0 -> "${h}h"; else -> "${h}h ${m}m" }
}

private fun colorHex(color: Color): String = String.format(Locale.US, "#%06X", 0xFFFFFF and color.toArgb())
private fun parseColor(value: String): Color = try {
    val parsed = Color(AndroidColor.parseColor(value))
    val legacyIndex = LegacyPalette.indexOfFirst { it.toArgb() == parsed.toArgb() }
    if (legacyIndex in Palette.indices) Palette[legacyIndex] else parsed
} catch (_: Exception) {
    Palette.first()
}

private fun loadActivities(context: Context, date: LocalDate): List<ActivityBlock> {
    val preferences = context.getSharedPreferences("twenty_four_hours", Context.MODE_PRIVATE)
    val dateKey = "activities_${date}"
    var raw = preferences.getString(dateKey, null)

    // Preserve the schedule made in the first app version by assigning it to today's date.
    if (raw == null && date == LocalDate.now()) {
        raw = preferences.getString("activities", null)
        if (raw != null) {
            preferences.edit().putString(dateKey, raw).remove("activities").apply()
        }
    }

    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val subArray = item.optJSONArray("subactivities")
                val subs = buildList {
                    if (subArray != null) {
                        for (j in 0 until subArray.length()) {
                            val sub = subArray.optString(j).trim()
                            if (sub.isNotEmpty()) add(sub)
                        }
                    }
                }
                add(
                    ActivityBlock(
                        item.optLong("id", System.currentTimeMillis() + i),
                        item.optString("title", "Activity"),
                        item.optInt("start", 0),
                        item.optInt("end", 60),
                        item.optString("color", colorHex(Palette.first())),
                        subs,
                    )
                )
            }
        }.filter { it.title.isNotBlank() && it.start in 0..1439 && it.end > it.start && it.end - it.start <= 1440 }
            .sortedBy { it.start }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveActivities(context: Context, date: LocalDate, activities: List<ActivityBlock>) {
    val json = JSONArray()
    activities.sortedBy { it.start }.forEach { item ->
        val subJson = JSONArray()
        item.subactivities.forEach { subJson.put(it) }
        json.put(
            JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("start", item.start)
                .put("end", item.end)
                .put("color", item.color)
                .put("subactivities", subJson)
        )
    }
    context.getSharedPreferences("twenty_four_hours", Context.MODE_PRIVATE)
        .edit()
        .putString("activities_${date}", json.toString())
        .apply()
}

