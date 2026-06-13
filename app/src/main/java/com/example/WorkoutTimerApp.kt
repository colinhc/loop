package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToInt

fun formatSecToMinSec(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return when {
        m > 0 && s > 0 -> "${m}m ${s}s"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

fun getSetsIntervalsLabel(workout: Workout): String {
    val converters = WorkoutTypeConverters()
    val list = converters.toSetPreferencesList(workout.setPreferencesJson)
    val subset = list.take(workout.totalSets + 1).drop(1).take(workout.totalSets)
    val intervalsJoined = subset.joinToString(", ") { "${it.durationSeconds}s" }
    return "${workout.totalSets} sets x $intervalsJoined"
}

// Elegant vibrant concentric ring color palette from theme instructions
val ringColors = listOf(
    Color(0xFFE53935), // Ruby / Crimson (Backup Red)
    Color(0xFFFFB300), // Amber / Yellow
    Color(0xFF43A047), // Emerald Green
    Color(0xFF00ACC1), // Cyber Cyan
    Color(0xFF1E88E5), // Cobalt Blue
    Color(0xFF5E35B1), // Deep Violet
    Color(0xFFD81B60), // Vivid Orchid
    Color(0xFF3949AB), // Indigo
    Color(0xFF00897B), // Rich Teal
    Color(0xFFFF7043)  // Coral Sunset
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutTimerApp(
    viewModel: TimerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)), // Immersive deep black canvas
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Immersive Header (HypeSet Pro branding styling)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDC2626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Concentric Icon",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Concentric",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E2E6),
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "CONCENTRIC INTERVALS",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF888888),
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1C))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Theme Settings",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Workout List / Management Section moved to the top
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var showSaveDialog by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "WORKOUT PLANS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 1.5.sp,
                                color = Color(0xFFA0A0A5)
                            )
                        )
                    }
                    
                    Button(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("save_workout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CREATE NEW",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (state.workouts.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = Color(0xFF1F1F1F),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.05f)
                        )
                    ) {
                        Text(
                            text = "No saved workouts yet. Configure sets and tap 'CREATE NEW' to create one!",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF88888D),
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // Vertically scrolling list of named workouts (maximum 3 visible, scrollable)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.workouts.forEach { workout ->
                            val isSelected = state.activeWorkoutId == workout.id
                            val borderColor = if (isSelected) Color(0xFFDC2626) else Color.White.copy(alpha = 0.05f)
                            val borderWeight = if (isSelected) 1.5.dp else 1.dp
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadWorkout(workout) },
                                color = if (isSelected) Color(0xFF1E1E24) else Color(0xFF1C1C1E),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = borderWeight,
                                    color = borderColor
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = workout.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else Color(0xFFE2E2E6)
                                                ),
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    color = Color(0xFFDC2626).copy(alpha = 0.15f),
                                                    contentColor = Color(0xFFFCA5A5),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Format "sets x intervals" label under the name
                                        val intervalsLabel = getSetsIntervalsLabel(workout)
                                        Text(
                                            text = intervalsLabel,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) Color(0xFFFCA5A5) else Color(0xFF88888F)
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteWorkout(workout.id) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("delete_workout_${workout.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Workout",
                                            tint = Color.White.copy(alpha = 0.3f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showSaveDialog) {
                    var workoutName by remember { mutableStateOf("") }

                    AlertDialog(
                        onDismissRequest = { showSaveDialog = false },
                        title = {
                            Text(
                                text = "CREATE NEW PLAN",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Please enter a name for this custom workout plan:",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFFE2E2E6)
                                    )
                                )
                                OutlinedTextField(
                                    value = workoutName,
                                    onValueChange = { workoutName = it },
                                    placeholder = { Text("e.g. HIIT Power Rounds") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFDC2626),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedLabelColor = Color(0xFFDC2626),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedPlaceholderColor = Color(0xFF88888D),
                                        unfocusedPlaceholderColor = Color(0xFF88888D)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("workout_name_input")
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (workoutName.trim().isNotEmpty()) {
                                        viewModel.saveWorkout(workoutName.trim())
                                        showSaveDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("SAVE")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showSaveDialog = false }
                            ) {
                                Text(
                                    text = "CANCEL",
                                    color = Color(0xFF88888F)
                                )
                            }
                        },
                        containerColor = Color(0xFF141414),
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.05f))
            )

            // Occupies 70% of screen width in a constrained square box
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val boxWidth = maxWidth
                val boxWidthPx = with(LocalDensity.current) { boxWidth.toPx() }
                
                // Clock Dial component in center
                ClockDial(
                    modifier = Modifier.fillMaxSize(),
                    totalSets = state.totalSets,
                    activeSetIndex = state.activeSetIndex,
                    elapsedSeconds = state.elapsedSeconds,
                    preferencesList = state.setPreferencesList,
                    boxWidthPx = boxWidthPx,
                    isRunning = state.isRunning,
                    onTogglePlay = { viewModel.toggleStartPause() },
                    onReset = { viewModel.resetTimer() }
                )
            }

            // Immersive Control Section Panel styled like a sheets-up card base
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                    .background(Color(0xFF141414))
                    .shadow(elevation = 24.dp)
                    .padding(horizontal = 24.dp, vertical = 42.dp),
                verticalArrangement = Arrangement.spacedBy(42.dp)
            ) {
                // First Slider Section: Sets Controller & Selector
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.totalSets > 0) {
                            Text(
                                text = "EDITING SET #${state.selectedSet}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.5.sp,
                                    color = Color(0xFFA0A0A5)
                                )
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        Text(
                            text = "TARGET SETS: ${state.totalSets}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 1.5.sp,
                                color = Color(0xFFDC2626)
                            )
                        )
                    }

                    DoubleKnobSetsSlider(
                        totalSets = state.totalSets,
                        selectedSet = state.selectedSet,
                        onTotalSetsChange = { viewModel.setTotalSets(it) },
                        onSelectedSetChange = { viewModel.setSelectedSet(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .testTag("sets_double_slider")
                    )
                }

                // Divider line matching border styling
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.05f))
                )

                if (state.totalSets > 0 && state.selectedSet > 0) {
                    val activePrefs = state.setPreferencesList[state.selectedSet]

                    // Second Slider Section: Set Duration Multi-Knob Setup
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DURATION: ${formatSecToMinSec(activePrefs.durationSeconds)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFFA0A0A5)
                                )
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "In: ${activePrefs.startBeepSeconds}s",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEAB308) // Yellow theme
                                )
                                Text(
                                    text = "Out: ${activePrefs.endAlarmSeconds}s",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF97316) // Orange theme
                                )
                            }
                        }

                        TripleKnobDurationSlider(
                            duration = activePrefs.durationSeconds,
                            startBeep = activePrefs.startBeepSeconds,
                            endAlarm = activePrefs.endAlarmSeconds,
                            onValuesChange = { newDuration, newStart, newEnd ->
                                viewModel.updateSelectedSetPreferences(
                                    duration = newDuration,
                                    startBeeps = newStart,
                                    endAlarms = newEnd
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("duration_triple_slider")
                        )
                    }

                    // Sound Configuration for Count Up and Count Down has been moved to the App Settings dialog
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Please slide the Sets Setup knob to create sets and begin your interval workout customized configuration.",
                            color = Color(0xFF66666F),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = {
                Text(
                    text = "SOUND SETTINGS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Count In Sound Group
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Count In Sound (Start of Set)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E2E6)
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoundManager.START_SOUNDS.forEach { soundName ->
                                val isSelected = state.universalStartSound == soundName
                                Surface(
                                    modifier = Modifier.clickable {
                                        viewModel.updateUniversalSounds(startSound = soundName)
                                    },
                                    color = if (isSelected) Color(0xFFEAB308) else Color(0xFF1C1C1C),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.Black else Color(0xFFEAB308),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = soundName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else Color(0xFFE2E2E6)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Count Out Sound Group
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Count Out Sound (Warning Alert)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E2E6)
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoundManager.END_SOUNDS.forEach { soundName ->
                                val isSelected = state.universalEndSound == soundName
                                Surface(
                                    modifier = Modifier.clickable {
                                        viewModel.updateUniversalSounds(endSound = soundName)
                                    },
                                    color = if (isSelected) Color(0xFFF97316) else Color(0xFF1C1C1C),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.Black else Color(0xFFF97316),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = soundName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else Color(0xFFE2E2E6)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "@2026 Colin C",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF88888F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(
                        text = "DONE",
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = Color(0xFF121212),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.testTag("settings_dialog")
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClockDial(
    modifier: Modifier = Modifier,
    totalSets: Int,
    activeSetIndex: Int,
    elapsedSeconds: Float,
    preferencesList: List<SetPreferences>,
    boxWidthPx: Float,
    isRunning: Boolean,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit
) {
    val density = LocalDensity.current
    val strokeWidthRing = with(density) { 6.dp.toPx() }
    
    // As per user requirement, let's keep the core black dial 35% of bounding box dimensions
    val baseBlackDialRadius = boxWidthPx * 0.35f
    
    val activePref = if (totalSets > 0 && activeSetIndex <= totalSets) preferencesList[activeSetIndex] else SetPreferences()
    val T_prep = activePref.startBeepSeconds.toFloat()
    val T_active = activePref.durationSeconds.toFloat()
    val rawProgress = if (elapsedSeconds < T_prep) {
        0f
    } else {
        val activeElapsed = elapsedSeconds - T_prep
        if (T_active > 0f) (activeElapsed / T_active).coerceIn(0f, 1f) else 0f
    }
    
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Chronograph drawings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // 1. Draw dial scale tick marks outside the boundary
            val scaleRadiusIn = baseBlackDialRadius + 2.dp.toPx()
            val scaleRadiusOut = baseBlackDialRadius + 8.dp.toPx()
            for (angleDegrees in 0 until 300 step 6) {
                val isThickMajor = angleDegrees % 30 == 0
                // Rotate to make 0 centered at the top
                val angleRad = Math.toRadians((angleDegrees + 120.0))
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()
                
                val pinStart = Offset(center.x + scaleRadiusIn * cosA, center.y + scaleRadiusIn * sinA)
                val pinEnd = Offset(
                    center.x + (if (isThickMajor) scaleRadiusOut + 3.dp.toPx() else scaleRadiusOut) * cosA,
                    center.y + (if (isThickMajor) scaleRadiusOut + 3.dp.toPx() else scaleRadiusOut) * sinA
                )
                
                drawLine(
                    color = if (isThickMajor) Color(0xFFDC2626) else Color(0xFF3A3A3F),
                    start = pinStart,
                    end = pinEnd,
                    strokeWidth = if (isThickMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw active concentric rings representing the sets (clean, separated, solid colors)
            if (totalSets > 0) {
                val baseRingMargin = 16.dp.toPx()
                val gapBetweenRings = 12.dp.toPx()
                val strokeWidthHalf = strokeWidthRing / 2f
                
                for (i in 1..totalSets) {
                    val ringRadius = baseBlackDialRadius + baseRingMargin + (i - 1) * gapBetweenRings
                    val ringColor = if (i == totalSets) Color(0xFFDC2626) else ringColors[(i - 1) % ringColors.size]
                    
                    // Solid dark anthracite background track for the inactive ring portion
                    drawCircle(
                        color = Color(0xFF1C1C1E),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = strokeWidthRing)
                    )
                    
                    // Clear and crisp black outer border
                    drawCircle(
                        color = Color.Black,
                        radius = ringRadius + strokeWidthHalf,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    // Clear and crisp black inner border
                    drawCircle(
                        color = Color.Black,
                        radius = ringRadius - strokeWidthHalf,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    val progressValue = when {
                        i < activeSetIndex -> 1f
                        i > activeSetIndex -> 0f
                        else -> rawProgress
                    }
                    
                    // Solid colored progress arc representing completion
                    if (progressValue > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progressValue,
                            useCenter = false,
                            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                            size = Size(ringRadius * 2, ringRadius * 2),
                            style = Stroke(width = strokeWidthRing, cap = StrokeCap.Butt)
                        )
                    }
                }
            }

            // 3. Draw physical hand arm rotating corresponding to current active set
            if (totalSets > 0) {
                val armAngleRad = Math.toRadians((360.0 * rawProgress) - 90.0)
                val baseRingMargin = 16.dp.toPx()
                val gapBetweenRings = 12.dp.toPx()
                
                // Ends at current active ring radius
                val activeRadius = baseBlackDialRadius + baseRingMargin + (activeSetIndex - 1) * gapBetweenRings
                val activeColor = if (activeSetIndex == totalSets) Color(0xFFDC2626) else ringColors[(activeSetIndex - 1) % ringColors.size]

                val armStart = Offset(
                    center.x + baseBlackDialRadius * cos(armAngleRad).toFloat(),
                    center.y + baseBlackDialRadius * sin(armAngleRad).toFloat()
                )
                val armEnd = Offset(
                    center.x + activeRadius * cos(armAngleRad).toFloat(),
                    center.y + activeRadius * sin(armAngleRad).toFloat()
                )
                
                // Arm body line pointer
                drawLine(
                    color = activeColor,
                    start = armStart,
                    end = armEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Active indicator outer tip
                drawCircle(
                    color = activeColor,
                    radius = strokeWidthRing * 1.3f,
                    center = armEnd
                )
                // Active indicator tip highlights
                drawCircle(
                    color = Color.White,
                    radius = strokeWidthRing * 0.6f,
                    center = armEnd
                )
            }
        }

        // 4. Centered gorgeous solid black disk with display indicators and Play/Pause action button
        Box(
            modifier = Modifier
                .size((baseBlackDialRadius * 2 / with(density) { 1.dp.toPx() }).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF141414), Color(0xFF000000)),
                        radius = baseBlackDialRadius
                    )
                )
                .shadow(elevation = 16.dp, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                // Header Set Tag info
                if (totalSets > 0) {
                    val activePref = if (activeSetIndex <= totalSets) preferencesList[activeSetIndex] else SetPreferences()
                    val T_prep = activePref.startBeepSeconds
                    val isPreparing = elapsedSeconds < T_prep
                    val labelText = if (isPreparing) {
                        "COUNT IN - SET $activeSetIndex"
                    } else {
                        "SET $activeSetIndex OF $totalSets"
                    }
                    val labelColor = if (isPreparing) {
                        Color(0xFFEAB308) // Amber Count In color
                    } else if (activeSetIndex == totalSets) {
                        Color(0xFFDC2626) // Last set ruby color
                    } else {
                        Color(0xFF43A047) // Active set emerald green color
                    }
                    Text(
                        text = labelText.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = labelColor,
                            letterSpacing = 2.sp
                        )
                    )
                } else {
                    Text(
                        text = "SET UP INTERVALS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF55555F),
                            letterSpacing = 2.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Digital Timer Clock Reading
                val remainingSec = if (totalSets > 0) {
                    val activePref = if (activeSetIndex <= totalSets) preferencesList[activeSetIndex] else SetPreferences()
                    val T_prep = activePref.startBeepSeconds
                    if (elapsedSeconds < T_prep) {
                        kotlin.math.ceil((T_prep - elapsedSeconds).toDouble()).toFloat().coerceAtLeast(1f)
                    } else {
                        val activeElapsed = elapsedSeconds - T_prep
                        val T_active = activePref.durationSeconds
                        kotlin.math.ceil((T_active - activeElapsed).toDouble()).toFloat().coerceAtLeast(0f)
                    }
                } else {
                    0f
                }
                val min = (remainingSec.toInt() / 60)
                val secPart = (remainingSec.toInt() % 60)
                val formatString = if (min > 0) {
                    String.format("%02d:%02d", min, secPart)
                } else {
                    String.format("00:%02d", secPart)
                }

                Text(
                    text = formatString,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E2E6)
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Start / Pause active toggle button (Doubled to 120.dp!)
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onTogglePlay,
                            onLongClick = onReset
                        )
                        .testTag("dial_play_pause_button"),
                    color = Color(0xFFDC2626), // Immersive Red
                    shape = CircleShape,
                    shadowElevation = 8.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Pause timer" else "Start timer",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "LONG PRESS TO RESET",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF55555F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}

// Custom Immersive styled Double knob Sets setup slider
@Composable
fun DoubleKnobSetsSlider(
    totalSets: Int,
    selectedSet: Int,
    onTotalSetsChange: (Int) -> Unit,
    onSelectedSetChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val trackPadding = with(density) { 20.dp.toPx() }
        val usableWidth = widthPx - 2 * trackPadding

        fun getValFromX(x: Float): Int {
            val progress = ((x - trackPadding) / usableWidth).coerceIn(0f, 1f)
            return 1 + (progress * 4).roundToInt()
        }

        fun getXFromVal(value: Int): Float {
            return trackPadding + ((value - 1) / 4f) * usableWidth
        }

        val currentTotalSets by rememberUpdatedState(totalSets)
        val currentSelectedSet by rememberUpdatedState(selectedSet)
        val currentOnTotalSetsChange by rememberUpdatedState(onTotalSetsChange)
        val currentOnSelectedSetChange by rememberUpdatedState(onSelectedSetChange)

        var activeDragKnob by remember { mutableStateOf<String?>(null) }
        var isOverlappingDrag by remember { mutableStateOf(false) }
        var dragStartX by remember { mutableStateOf(0f) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val selectedX = getXFromVal(currentSelectedSet)
                            val totalX = getXFromVal(currentTotalSets)
                            val distToSelected = abs(offset.x - selectedX)
                            val distToTotal = abs(offset.x - totalX)
                            val hitRadius = with(density) { 24.dp.toPx() }

                            if (currentSelectedSet == currentTotalSets && distToSelected <= hitRadius) {
                                isOverlappingDrag = true
                                dragStartX = offset.x
                                activeDragKnob = null
                            } else {
                                isOverlappingDrag = false
                                activeDragKnob = when {
                                    distToSelected <= hitRadius && distToTotal <= hitRadius -> {
                                        if (distToSelected <= distToTotal) "selected" else "total"
                                    }
                                    distToSelected <= hitRadius -> {
                                        "selected"
                                    }
                                    distToTotal <= hitRadius -> {
                                        "total"
                                    }
                                    else -> {
                                        null
                                    }
                                }

                                if (activeDragKnob != null) {
                                    val tappedValue = getValFromX(offset.x)
                                    if (activeDragKnob == "selected") {
                                        currentOnSelectedSetChange(tappedValue.coerceIn(1, currentTotalSets))
                                    } else {
                                        currentOnTotalSetsChange(tappedValue.coerceIn(1, 5))
                                    }
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (isOverlappingDrag) {
                                val currentX = change.position.x
                                val diffX = currentX - dragStartX
                                val threshold = with(density) { 2.dp.toPx() }
                                if (abs(diffX) > threshold) {
                                    if (diffX > 0) {
                                        activeDragKnob = "both"
                                    } else {
                                        activeDragKnob = "selected"
                                    }
                                    isOverlappingDrag = false
                                }
                            }
                            if (activeDragKnob != null) {
                                val newVal = getValFromX(change.position.x)
                                when (activeDragKnob) {
                                    "selected" -> {
                                        currentOnSelectedSetChange(newVal.coerceIn(1, currentTotalSets))
                                    }
                                    "total" -> {
                                        currentOnTotalSetsChange(newVal.coerceIn(1, 5))
                                    }
                                    "both" -> {
                                        val valVal = newVal.coerceIn(1, 5)
                                        currentOnTotalSetsChange(valVal)
                                        currentOnSelectedSetChange(valVal)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            activeDragKnob = null
                            isOverlappingDrag = false
                        },
                        onDragCancel = {
                            activeDragKnob = null
                            isOverlappingDrag = false
                        }
                    )
                }
        ) {
            val centerY = size.height / 2f
            
            // 1. Draw track line backdrop
            drawLine(
                color = Color(0xFF2A2A2A),
                start = Offset(trackPadding, centerY),
                end = Offset(trackPadding + usableWidth, centerY),
                strokeWidth = 9.dp.toPx(), // 50% larger
                cap = StrokeCap.Round
            )

            // 2. Draw active total progress tracks
            if (totalSets > 0) {
                drawLine(
                    color = Color(0xFFDC2626), // Immersive active sets red line
                    start = Offset(trackPadding, centerY),
                    end = Offset(getXFromVal(totalSets), centerY),
                    strokeWidth = 9.dp.toPx(), // 50% larger
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw tick spots
            for (tick in 1..5) {
                val tickX = getXFromVal(tick)
                drawCircle(
                    color = if (tick <= totalSets) Color.White.copy(alpha = 0.4f) else Color(0xFF3F3F3F),
                    radius = 3.dp.toPx(),
                    center = Offset(tickX, centerY)
                )
            }

            // 4. Draw Knob 1: Selected Set Selector Knob (Circular styled silver knob)
            if (selectedSet > 0) {
                val selX = getXFromVal(selectedSet)
                drawCircle(
                    color = Color(0xFF88888D),
                    radius = 11.dp.toPx(),
                    center = Offset(selX, centerY)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 4.dp.toPx(),
                    center = Offset(selX, centerY)
                )
            }

            // 5. Draw Knob 2: Total Target Sets Knob (Slim design rectangular red/white knob)
            val totalX = getXFromVal(totalSets)
            val knobWidth = 12.dp.toPx()
            val knobHeight = 34.dp.toPx()
            drawRoundRect(
                color = Color(0xFFDC2626),
                topLeft = Offset(totalX - knobWidth / 2f, centerY - knobHeight / 2f),
                size = Size(knobWidth, knobHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            // inner high contrast white accent
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(totalX - 2.dp.toPx() / 2f, centerY - 20.dp.toPx() / 2f),
                size = Size(2.dp.toPx(), 20.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
        
        // Setup numeric markings under slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 1..5) {
                Text(
                    text = i.toString(),
                    fontSize = 15.sp, // 50% larger
                    fontWeight = FontWeight.Bold,
                    color = when {
                        i == totalSets -> Color(0xFFDC2626)
                        i == selectedSet -> Color(0xFFE2E2E6)
                        i < totalSets -> Color(0xFF88888F)
                        else -> Color(0xFF3F3F45)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Custom Immersive styled Triple knob preferred duration config slider
@Composable
fun TripleKnobDurationSlider(
    duration: Int,
    startBeep: Int,
    endAlarm: Int,
    onValuesChange: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val trackPadding = with(density) { 20.dp.toPx() }
        val usableWidth = widthPx - 2 * trackPadding

        fun getXFromStartBeep(value: Int): Float {
            return trackPadding + (value / 5f) * usableWidth
        }

        fun getStartBeepFromX(x: Float): Int {
            val progress = ((x - trackPadding) / usableWidth).coerceIn(0f, 1f)
            return (progress * 5f).roundToInt().coerceIn(0, 5)
        }

        fun getXFromDuration(value: Int): Float {
            val progress = (value - 10f) / (300f - 10f)
            return trackPadding + progress * usableWidth
        }

        fun getDurationFromX(x: Float): Int {
            val progress = ((x - trackPadding) / usableWidth).coerceIn(0f, 1f)
            val rawSec = 10f + progress * (300f - 10f)
            val snapped = (rawSec / 10f).roundToInt() * 10
            return snapped.coerceIn(10, 300)
        }

        fun getXFromEndAlarm(value: Int): Float {
            val ratio = 1f - (value / 5f)
            return trackPadding + ratio * usableWidth
        }

        fun getEndAlarmFromX(x: Float): Int {
            val progress = ((x - trackPadding) / usableWidth).coerceIn(0f, 1f)
            val value = (1f - progress) * 5f
            return value.roundToInt().coerceIn(0, 5)
        }

        val currentDuration by rememberUpdatedState(duration)
        val currentStartBeep by rememberUpdatedState(startBeep)
        val currentEndAlarm by rememberUpdatedState(endAlarm)
        val currentOnValuesChange by rememberUpdatedState(onValuesChange)

        var activeDragKnob by remember { mutableStateOf<String?>(null) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val centerY = size.height / 2f
                            val upperTrackY = centerY - 22.dp.toPx()
                            val middleTrackY = centerY
                            val lowerTrackY = centerY + 22.dp.toPx()

                            val distYUpper = abs(offset.y - upperTrackY)
                            val distYMiddle = abs(offset.y - middleTrackY)
                            val distYLower = abs(offset.y - lowerTrackY)

                            activeDragKnob = when {
                                distYUpper < distYMiddle && distYUpper < distYLower -> "start"
                                distYLower < distYMiddle && distYLower < distYUpper -> "end"
                                else -> "duration"
                            }

                            when (activeDragKnob) {
                                "start" -> {
                                    val newVal = getStartBeepFromX(offset.x)
                                    if (newVal != currentStartBeep) {
                                        currentOnValuesChange(currentDuration, newVal, currentEndAlarm)
                                    }
                                }
                                "end" -> {
                                    val newVal = getEndAlarmFromX(offset.x)
                                    if (newVal != currentEndAlarm) {
                                        currentOnValuesChange(currentDuration, currentStartBeep, newVal)
                                    }
                                }
                                "duration" -> {
                                    val newVal = getDurationFromX(offset.x)
                                    if (newVal != currentDuration) {
                                        currentOnValuesChange(newVal, currentStartBeep, currentEndAlarm)
                                    }
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            when (activeDragKnob) {
                                "start" -> {
                                    val newVal = getStartBeepFromX(change.position.x)
                                    if (newVal != currentStartBeep) {
                                        currentOnValuesChange(currentDuration, newVal, currentEndAlarm)
                                    }
                                }
                                "end" -> {
                                    val newVal = getEndAlarmFromX(change.position.x)
                                    if (newVal != currentEndAlarm) {
                                        currentOnValuesChange(currentDuration, currentStartBeep, newVal)
                                    }
                                }
                                "duration" -> {
                                    val newVal = getDurationFromX(change.position.x)
                                    if (newVal != currentDuration) {
                                        currentOnValuesChange(newVal, currentStartBeep, currentEndAlarm)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            activeDragKnob = null
                        },
                        onDragCancel = {
                            activeDragKnob = null
                        }
                    )
                }
        ) {
            val centerY = size.height / 2f
            val upperY = centerY - 22.dp.toPx()
            val middleY = centerY
            val lowerY = centerY + 22.dp.toPx()

            // 1. UPPER TRACK: Count Up (Yellow, 0s - 5s)
            drawLine(
                color = Color(0xFF1F1F24),
                start = Offset(trackPadding, upperY),
                end = Offset(trackPadding + usableWidth, upperY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            val startX = getXFromStartBeep(startBeep)
            if (startBeep > 0) {
                drawLine(
                    color = Color(0xFFEAB308),
                    start = Offset(trackPadding, upperY),
                    end = Offset(startX, upperY),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // Ticks for Upper Track
            for (tick in 0..5) {
                val tickX = getXFromStartBeep(tick)
                drawCircle(
                    color = if (tick <= startBeep) Color.White.copy(alpha = 0.5f) else Color(0xFF3F3F45),
                    radius = 2.dp.toPx(),
                    center = Offset(tickX, upperY)
                )
            }
            // Knob for Upper Track (Yellow)
            drawCircle(
                color = Color(0xFFEAB308),
                radius = 11.dp.toPx(),
                center = Offset(startX, upperY)
            )
            drawCircle(
                color = Color.Black,
                radius = 4.dp.toPx(),
                center = Offset(startX, upperY)
            )

            // 2. MIDDLE TRACK: Set Duration (Slate / Active Red, 5s - 5m)
            drawLine(
                color = Color(0xFF2A2A2A),
                start = Offset(trackPadding, middleY),
                end = Offset(trackPadding + usableWidth, middleY),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round
            )
            val durX = getXFromDuration(duration)
            if (duration > 0) {
                drawLine(
                    color = Color(0xFF3F3F45),
                    start = Offset(trackPadding, middleY),
                    end = Offset(durX, middleY),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // Ticks for Duration Track
            for (sec in 0..300 step 60) {
                val secVal = if (sec == 0) 10 else sec
                val tickX = getXFromDuration(secVal)
                drawCircle(
                    color = if (secVal <= duration) Color.White.copy(alpha = 0.4f) else Color(0xFF3F3F45),
                    radius = 2.5.dp.toPx(),
                    center = Offset(tickX, middleY)
                )
            }
            // Knob for Duration Track: Slim Rounded Rectangular Red/White
            val rectWidth = 12.dp.toPx()
            val rectHeight = 32.dp.toPx()
            drawRoundRect(
                color = Color(0xFFDC2626),
                topLeft = Offset(durX - rectWidth / 2f, middleY - rectHeight / 2f),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(durX - 2.dp.toPx() / 2f, middleY - 18.dp.toPx() / 2f),
                size = Size(2.dp.toPx(), 18.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )

            // 3. LOWER TRACK: Count Down (Orange, 0s - 5s)
            drawLine(
                color = Color(0xFF1F1F24),
                start = Offset(trackPadding, lowerY),
                end = Offset(trackPadding + usableWidth, lowerY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            val endX = getXFromEndAlarm(endAlarm)
            if (endAlarm > 0) {
                drawLine(
                    color = Color(0xFFF97316),
                    start = Offset(trackPadding + usableWidth, lowerY),
                    end = Offset(endX, lowerY),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // Ticks for Lower Track
            for (tick in 0..5) {
                val tickX = getXFromEndAlarm(tick)
                drawCircle(
                    color = if (tick <= endAlarm) Color.White.copy(alpha = 0.5f) else Color(0xFF3F3F45),
                    radius = 2.dp.toPx(),
                    center = Offset(tickX, lowerY)
                )
            }
            // Knob for Lower Track (Orange)
            drawCircle(
                color = Color(0xFFF97316),
                radius = 11.dp.toPx(),
                center = Offset(endX, lowerY)
            )
            drawCircle(
                color = Color.Black,
                radius = 4.dp.toPx(),
                center = Offset(endX, lowerY)
            )
        }

        // Tonal timeline markings below middle duration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labels = listOf("10s", "1m", "2m", "3m", "4m", "5m")
            labels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF55555F),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
