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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val Background = Color(0xFF0D0E11)
private val SurfaceDark = Color(0xFF17191E)
private val Raised = Color(0xFF20232A)
private val TextPrimary = Color(0xFFF4F3F7)
private val TextMuted = Color(0xFF858B98)
private val Accent = Color(0xFFB7A6FF)
private val RingTrack = Color(0xFF292C33)
private val Palette = listOf(
    // Original seven
    Color(0xFF83D6B1), Color(0xFF83B8F5), Color(0xFFF0BD78),
    Color(0xFFC1A0F5), Color(0xFFF08D9A), Color(0xFF78C8D2), Color(0xFFAAB2BE),
    // Seven new colors
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
    val isToday = selectedDate == LocalDate.now()

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
                        Text(if (isToday) "YOUR DAY" else "DAY PLANNER", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
                        Row(
                            Modifier.clip(RoundedCornerShape(9.dp)).clickable { showDatePicker = true }.padding(vertical = 3.dp, horizontal = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Choose date", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text("24 HOURS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
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
                                    selectedDate = selectedDate.plusDays(1)
                                    editorOpen = false
                                } else if (dragDistance < -threshold) {
                                    selectedDate = selectedDate.minusDays(1)
                                    editorOpen = false
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    DayDial(activities = activities, showCurrentTime = isToday, onTap = ::editAt)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("YOUR DAY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.7.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("24", color = TextPrimary, fontSize = 48.sp, lineHeight = 50.sp, fontWeight = FontWeight.Light)
                        Text("HOURS", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(9.dp))
                        Text(if (planned == 0) "Tap the ring to begin" else "${duration(planned)} planned", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                    Text(if (isToday) "TODAY'S PLAN" else "DAY'S PLAN", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("${activities.size} ${if (activities.size == 1) "activity" else "activities"}", color = TextMuted, fontSize = 11.sp)
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
                Text("Swipe the clock to change days.", color = TextMuted, fontSize = 11.sp)
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(SurfaceDark).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(block.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${timeLabel(block.start)} – ${timeLabel(block.end)}${if (block.end >= 1440) " · NEXT DAY" else ""}${if (block.subactivities.isNotEmpty()) " · ${block.subactivities.size} steps" else ""}",
                color = TextMuted, fontSize = 11.sp
            )
        }
        Text(duration(block.end - block.start), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DayDial(activities: List<ActivityBlock>, showCurrentTime: Boolean, onTap: (Int) -> Unit) {
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
        drawArc(RingTrack, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

        activities.forEach { item ->
            val color = parseColor(item.color)
            val first = min(item.end - item.start, 1440 - item.start)
            activityArc(color, item.start, first, topLeft, arcSize, stroke)
            val remainder = item.end - item.start - first
            if (remainder > 0) activityArc(color, 0, remainder, topLeft, arcSize, stroke)
        }

        for (hour in 0 until 24) {
            val angle = Math.toRadians((-90f + hour * 15f).toDouble())
            val inner = radius + stroke * 0.58f
            val outer = inner + if (hour % 6 == 0) 9.dp.toPx() else 4.dp.toPx()
            drawLine(
                if (hour % 6 == 0) Color(0xFF777D89) else Color(0xFF444852),
                Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = if (hour % 6 == 0) 1.7.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round,
            )
        }

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(155, 160, 172)
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.toPx()
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val labelRadius = radius + stroke * 0.5f + 22.dp.toPx()
        listOf(0, 6, 12, 18).forEach { hour ->
            val angle = Math.toRadians((-90f + hour * 15f).toDouble())
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%02d", hour),
                center.x + cos(angle).toFloat() * labelRadius,
                center.y + sin(angle).toFloat() * labelRadius + 3.5.dp.toPx(),
                paint,
            )
        }

        if (showCurrentTime) {
            val now = LocalTime.now()
            val nowAngle = Math.toRadians((-90f + (now.hour * 60 + now.minute) / 1440f * 360f).toDouble())
            val dotRadius = radius + stroke / 2f + 2.dp.toPx()
            val dot = Offset(center.x + cos(nowAngle).toFloat() * dotRadius, center.y + sin(nowAngle).toFloat() * dotRadius)
            drawCircle(Background, 5.dp.toPx(), dot)
            drawCircle(TextPrimary, 3.dp.toPx(), dot)
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
    var subactivityInput by remember(existing?.id, initialStart) { mutableStateOf("") }
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
                    TimeButton("END", timeLabel(end) + if (end >= 1440) " · NEXT DAY" else "", Modifier.weight(1f)) { pickTime(false) }
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
                Text("SUBACTIVITIES", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("Smaller steps that belong to this activity.", color = TextMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subactivityInput,
                        onValueChange = { subactivityInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Subactivity") },
                        placeholder = { Text("e.g. Warm up") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent,
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = RingTrack,
                            focusedLabelColor = Accent,
                            unfocusedLabelColor = TextMuted,
                            focusedPlaceholderColor = TextMuted,
                            unfocusedPlaceholderColor = TextMuted,
                        ),
                    )
                    TextButton(onClick = {
                        val clean = subactivityInput.trim()
                        if (clean.isNotEmpty() && clean !in subactivities) {
                            subactivities = subactivities + clean
                            subactivityInput = ""
                        }
                    }) { Text("Add", color = Accent) }
                }
                if (subactivities.isEmpty()) {
                    Text("No subactivities added yet.", color = TextMuted, fontSize = 11.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        subactivities.forEach { subactivity ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceDark).padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(selectedColor))
                                Spacer(Modifier.width(9.dp))
                                Text(subactivity, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { subactivities = subactivities - subactivity }) {
                                    Text("Remove", color = TextMuted, fontSize = 11.sp)
                                }
                            }
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
                    else -> onSave(ActivityBlock(existing?.id ?: System.currentTimeMillis(), clean, start, end, colorHex(selectedColor), subactivities))
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
private fun parseColor(value: String): Color = try { Color(AndroidColor.parseColor(value)) } catch (_: Exception) { Palette.first() }

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

