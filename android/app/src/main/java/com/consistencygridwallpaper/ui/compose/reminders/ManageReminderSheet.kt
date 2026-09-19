package com.consistencygridwallpaper.ui.compose.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.storage.room.ReminderEntity
import com.consistencygridwallpaper.ui.compose.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ManageReminderSheet(
    reminderToEdit: ReminderEntity? = null,
    viewModel: RemindersViewModel,
    onDismiss: () -> Unit
) {
    InlineManageReminderForm(
        reminderToEdit = reminderToEdit,
        viewModel = viewModel,
        onDismiss = onDismiss
    )
}

@Composable
fun InlineManageReminderForm(
    reminderToEdit: ReminderEntity? = null,
    viewModel: RemindersViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayCal = remember { Calendar.getInstance() }
    val today = remember { dateFmt.format(todayCal.time) }

    var title by remember { mutableStateOf(reminderToEdit?.title ?: "") }
    var description by remember { mutableStateOf(reminderToEdit?.description ?: "") }
    var startDate by remember { mutableStateOf(reminderToEdit?.startDate ?: today) }
    var endDate by remember { mutableStateOf(reminderToEdit?.endDate ?: today) }
    var startTime by remember { mutableStateOf(reminderToEdit?.startTime ?: "") }
    var endTime by remember { mutableStateOf(reminderToEdit?.endTime ?: "") }
    var isFullDay by remember { mutableStateOf(reminderToEdit?.isFullDay ?: true) }
    var priority by remember { mutableStateOf(reminderToEdit?.priority ?: 1) } // 0=Low, 1=Med, 2=High
    var markerColor by remember { mutableStateOf(reminderToEdit?.markerColor ?: "#FF7A00") }

    val isEditing = reminderToEdit != null

    // Helper functions for pickers
    fun showDatePicker(currentDateStr: String, onDateSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()
        try {
            val parsed = dateFmt.parse(currentDateStr)
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {}
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                onDateSelected(dateFmt.format(selectedCal.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker(currentTimeStr: String, onTimeSelected: (String) -> Unit) {
        var hour = 9
        var minute = 0
        if (currentTimeStr.contains(":")) {
            val parts = currentTimeStr.split(":")
            hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
            minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        }
        TimePickerDialog(
            context,
            { _, h, m ->
                val formatted = String.format(Locale.US, "%02d:%02d", h, m)
                onTimeSelected(formatted)
            },
            hour,
            minute,
            true
        ).show()
    }

    val availableColors = remember {
        listOf(
            "#FF7A00" to "Orange Accent",
            "#34D399" to "Emerald",
            "#FBBF24" to "Amber",
            "#22D3EE" to "Cyan",
            "#A78BFA" to "Purple",
            "#F472B6" to "Pink",
            "#EF4444" to "Red"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = OrangeAccent.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(22.dp))
            .background(BgCard)
            .border(1.5.dp, OrangeAccent.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OrangeMuted)
                            .border(1.dp, OrangeAccent.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = OrangeAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isEditing) "Edit Reminder" else "Create New Reminder",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Fill in details below and save",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isEditing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoseDim)
                                .clickable {
                                    viewModel.deleteReminder(reminderToEdit!!.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete, "Delete",
                                    tint = RoseAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Delete", fontSize = 12.sp, color = RoseAccent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BgBase)
                    ) {
                        Icon(Icons.Filled.Close, "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = GlassStroke)

            // Title
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("REMINDER TITLE", fontSize = 10.sp, color = OrangeAccent, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("What needs to be done?", color = TextMuted) },
                    colors = defaultTextFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Description
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DESCRIPTION (OPTIONAL)", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Add extra context or steps...", color = TextMuted) },
                    colors = defaultTextFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            // Priority Level
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PRIORITY LEVEL", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val pItems = listOf(0 to "🟢 Low", 1 to "🟡 Medium", 2 to "🔴 High")
                    pItems.forEach { (pVal, pLabel) ->
                        val isSelected = priority == pVal
                        val activeColor = when(pVal) {
                            2 -> RoseAccent
                            1 -> Color(0xFFF59E0B)
                            else -> GreenAccent
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) activeColor.copy(alpha = 0.15f) else BgBase)
                                .border(
                                    1.dp,
                                    if (isSelected) activeColor else GlassStroke,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { priority = pVal },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeColor else TextPrimary
                            )
                        }
                    }
                }
            }

            // Quick Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("QUICK DATE PRESETS", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = remember {
                        val tomCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                        val nxtWeekCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                        listOf(
                            "Today" to dateFmt.format(todayCal.time),
                            "Tomorrow" to dateFmt.format(tomCal.time),
                            "Next Week" to dateFmt.format(nxtWeekCal.time)
                        )
                    }
                    presets.forEach { (label, dateVal) ->
                        val isSelected = startDate == dateVal
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) OrangeMuted else BgBase)
                                .border(1.dp, if (isSelected) OrangeAccent else GlassStroke, RoundedCornerShape(20.dp))
                                .clickable {
                                    startDate = dateVal
                                    endDate = dateVal
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (isSelected) OrangeAccent else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Date Selection
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgBase)
                        .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                        .clickable { showDatePicker(startDate) { startDate = it; if (endDate < it) endDate = it } }
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Start Date", fontSize = 10.sp, color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.DateRange, null, tint = OrangeAccent, modifier = Modifier.size(16.dp))
                            Text(startDate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgBase)
                        .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                        .clickable { showDatePicker(endDate) { endDate = it } }
                        .padding(12.dp)
                ) {
                    Column {
                        Text("End Date", fontSize = 10.sp, color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.DateRange, null, tint = OrangeLight, modifier = Modifier.size(16.dp))
                            Text(endDate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }
            }

            // All Day Switch & Time Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgBase)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("All Day Event", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("No specific time needed", fontSize = 11.sp, color = TextMuted)
                }
                Switch(
                    checked = isFullDay,
                    onCheckedChange = { isFullDay = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OrangeAccent,
                        uncheckedTrackColor = GlassStroke
                    )
                )
            }

            AnimatedVisibility(visible = !isFullDay) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgBase)
                            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                            .clickable { showTimePicker(startTime.ifEmpty { "09:00" }) { startTime = it } }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Start Time", fontSize = 10.sp, color = TextMuted)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Schedule, null, tint = OrangeAccent, modifier = Modifier.size(16.dp))
                                Text(startTime.ifEmpty { "09:00" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgBase)
                            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                            .clickable { showTimePicker(endTime.ifEmpty { "10:00" }) { endTime = it } }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("End Time", fontSize = 10.sp, color = TextMuted)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Schedule, null, tint = OrangeLight, modifier = Modifier.size(16.dp))
                                Text(endTime.ifEmpty { "10:00" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Color Swatches
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MARKER COLOR", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableColors.forEach { (hex, name) ->
                        val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(OrangeAccent)
                        val isSelected = markerColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { markerColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, name, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons (Cancel / Save)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke)
                ) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (title.isNotBlank())
                                Brush.horizontalGradient(listOf(OrangeAccent, Color(0xFFFF4500)))
                            else
                                Brush.horizontalGradient(listOf(GlassStroke, GlassStroke))
                        )
                        .clickable(enabled = title.isNotBlank()) {
                            if (reminderToEdit != null) {
                                viewModel.updateReminder(
                                    id = reminderToEdit.id,
                                    serverId = reminderToEdit.serverId,
                                    title = title,
                                    description = description.ifBlank { null },
                                    startDate = startDate,
                                    endDate = endDate.ifBlank { startDate },
                                    startTime = startTime.ifBlank { null },
                                    endTime = endTime.ifBlank { null },
                                    isFullDay = isFullDay,
                                    priority = priority,
                                    markerColor = markerColor
                                )
                            } else {
                                viewModel.addReminder(
                                    title = title,
                                    description = description.ifBlank { null },
                                    startDate = startDate,
                                    endDate = endDate.ifBlank { startDate },
                                    startTime = startTime.ifBlank { null },
                                    endTime = endTime.ifBlank { null },
                                    isFullDay = isFullDay,
                                    priority = priority,
                                    markerColor = markerColor
                                )
                            }
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEditing) "Save Changes ✓" else "Save Reminder ✓",
                        color = if (title.isNotBlank()) Color.White else TextMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun defaultTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OrangeAccent,
    unfocusedBorderColor = GlassStroke,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = OrangeAccent,
    focusedContainerColor = BgBase,
    unfocusedContainerColor = BgBase
)
