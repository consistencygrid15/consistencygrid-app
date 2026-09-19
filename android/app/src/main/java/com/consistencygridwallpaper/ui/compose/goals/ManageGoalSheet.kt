package com.consistencygridwallpaper.ui.compose.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consistencygridwallpaper.storage.room.GoalEntity
import com.consistencygridwallpaper.ui.compose.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageGoalSheet(
    goalToEdit: GoalEntity? = null,
    viewModel: GoalsViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = BgElevated,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle       = {
            BottomSheetDefaults.DragHandle(color = VioletAccent.copy(alpha = 0.4f))
        }
    ) {
        Box(modifier = Modifier.navigationBarsPadding()) {
            InlineManageGoalForm(
                goalToEdit = goalToEdit,
                viewModel = viewModel,
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineManageGoalForm(
    goalToEdit: GoalEntity? = null,
    viewModel: GoalsViewModel,
    onDismiss: () -> Unit
) {
    var title    by remember { mutableStateOf(goalToEdit?.title ?: "") }
    var category by remember { mutableStateOf(goalToEdit?.category ?: "General") }

    val initialSubGoals = remember {
        val parsed = goalToEdit?.let { viewModel.parseSubGoals(it.subGoalsJson) } ?: emptyList()
        if (parsed.isEmpty() && goalToEdit == null) {
            listOf(SubGoal(id = "new-${UUID.randomUUID()}", title = "", isCompleted = false))
        } else {
            parsed
        }
    }
    var subGoals by remember { mutableStateOf(initialSubGoals) }

    val categoryColor = when (category.lowercase()) {
        "health"         -> Color(0xFFEF4444)
        "wealth"         -> GreenAccent
        "mind"           -> VioletAccent
        "work"           -> SkyAccent
        "life milestone" -> GoldAccent
        else             -> VioletAccent
    }

    val isEditing = goalToEdit != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = VioletAccent.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(22.dp))
            .background(BgCard)
            .border(1.5.dp, VioletAccent.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment    = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VioletAccent.copy(alpha = 0.15f))
                            .border(1.dp, VioletAccent.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isEditing) "✏️" else "🎯", fontSize = 16.sp)
                    }
                    Column {
                        Text(
                            if (isEditing) "Edit Goal" else "New Goal",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = TextPrimary
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
                                .background(RoseAccent.copy(alpha = 0.12f))
                                .clickable {
                                    viewModel.deleteGoal(goalToEdit!!.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment    = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete, "Delete",
                                    tint     = RoseAccent,
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

            // Title Input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GOAL TITLE", fontSize = 10.sp, color = VioletAccent, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                OutlinedTextField(
                    value            = title,
                    onValueChange    = { title = it },
                    modifier         = Modifier.fillMaxWidth(),
                    placeholder      = { Text("What do you want to achieve?", color = TextMuted) },
                    colors           = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = VioletAccent,
                        unfocusedBorderColor    = GlassStroke,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = VioletAccent,
                        focusedContainerColor   = BgBase,
                        unfocusedContainerColor = BgBase
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            // Category Dropdown
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CATEGORY", fontSize = 10.sp, color = categoryColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                var expanded by remember { mutableStateOf(false) }
                val categories = listOf("Health", "Wealth", "Mind", "Work", "Life Milestone", "General")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value          = category,
                        onValueChange  = { category = it },
                        readOnly       = true,
                        trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors         = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedBorderColor      = categoryColor,
                            unfocusedBorderColor    = GlassStroke,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            focusedContainerColor   = BgBase,
                            unfocusedContainerColor = BgBase
                        ),
                        shape    = RoundedCornerShape(14.dp),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded          = expanded,
                        onDismissRequest  = { expanded = false },
                        modifier          = Modifier.background(BgCard)
                    ) {
                        categories.forEach { option ->
                            val optColor = when (option.lowercase()) {
                                "health"         -> Color(0xFFEF4444)
                                "wealth"         -> GreenAccent
                                "mind"           -> VioletAccent
                                "work"           -> SkyAccent
                                "life milestone" -> GoldAccent
                                else             -> TextSecondary
                            }
                            DropdownMenuItem(
                                text    = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(optColor))
                                        Text(option, color = TextPrimary)
                                    }
                                },
                                onClick = { category = option; expanded = false }
                            )
                        }
                    }
                }
            }

            // Sub-goals steps
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment    = Alignment.CenterVertically
                ) {
                    Text("STEPS (SUB-GOALS)", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = VioletAccent, letterSpacing = 1.2.sp)
                    Text("${subGoals.count { it.isCompleted }}/${subGoals.size}", fontSize = 11.sp, color = TextMuted)
                }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subGoals.forEachIndexed { index, sg ->
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked        = sg.isCompleted,
                                onCheckedChange = { checked ->
                                    val list = subGoals.toMutableList()
                                    list[index] = sg.copy(isCompleted = checked)
                                    subGoals = list
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor   = VioletAccent,
                                    uncheckedColor = TextSecondary,
                                    checkmarkColor = Color.White
                                )
                            )
                            OutlinedTextField(
                                value         = sg.title,
                                onValueChange = { txt ->
                                    val list = subGoals.toMutableList()
                                    list[index] = sg.copy(title = txt)
                                    subGoals = list
                                },
                                placeholder   = { Text("Step ${index + 1}…", color = TextMuted) },
                                modifier      = Modifier.weight(1f),
                                singleLine    = true,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor      = VioletAccent,
                                    unfocusedBorderColor    = GlassStroke,
                                    focusedTextColor        = TextPrimary,
                                    unfocusedTextColor      = TextPrimary,
                                    focusedContainerColor   = BgBase,
                                    unfocusedContainerColor = BgBase
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            IconButton(onClick = {
                                val list = subGoals.toMutableList()
                                list.removeAt(index)
                                subGoals = list
                            }) {
                                Icon(Icons.Filled.Close, "Remove", tint = TextMuted)
                            }
                        }
                    }
                }

                // Add step button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            subGoals = subGoals + SubGoal(id = "new-${UUID.randomUUID()}", title = "", isCompleted = false)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Add, "Add", tint = VioletAccent, modifier = Modifier.size(18.dp))
                    Text("Add Step", color = VioletAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                Brush.horizontalGradient(listOf(VioletAccent, Color(0xFF6D28D9)))
                            else
                                Brush.horizontalGradient(listOf(GlassStroke, GlassStroke))
                        )
                        .clickable(enabled = title.isNotBlank()) {
                            val validSubGoals = subGoals.filter { it.title.isNotBlank() }
                            if (goalToEdit != null) {
                                viewModel.updateGoal(goalToEdit.id, goalToEdit.serverId, title, category, validSubGoals, goalToEdit.isPinned)
                            } else {
                                viewModel.addGoal(title, category, validSubGoals)
                            }
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (title.isNotBlank()) (if (isEditing) "Save Changes ✓" else "Save Goal ✓") else "Enter a title",
                        color      = if (title.isNotBlank()) Color.White else TextMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 14.sp
                    )
                }
            }
        }
    }
}

