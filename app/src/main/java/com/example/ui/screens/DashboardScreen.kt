package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.database.RoomProject
import com.example.ui.viewmodel.StagingViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: StagingViewModel,
    onProjectSelected: (RoomProject) -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.allProjects.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Room Furnisher",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Virtual Interior Staging AI",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_project_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Project")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "New Project", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (projects.isEmpty()) {
                EmptyStateView(onCreateClick = { showCreateDialog = true })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectGridCard(
                            project = project,
                            onClick = { onProjectSelected(project) },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
            }

            if (showCreateDialog) {
                CreateProjectDialog(
                    onDismiss = { showCreateDialog = false },
                    onConfirm = { name, roomType, bgType, uri ->
                        viewModel.createNewProject(name, roomType, bgType, uri, context)
                        showCreateDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your Next Space Awaits",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Upload a photo of your empty room, paste furniture links from any store, and let Gemini AI arrange and critique your virtually designed space in 3D-position overlays!",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCreateClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag("empty_state_create_button")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Begin Staging", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProjectGridCard(
    project: RoomProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("project_card_${project.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            if (project.hasUserPhoto) {
                val file = File(project.backgroundImage)
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = project.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Missing photo", color = Color.White, fontSize = 12.sp)
                    }
                }
            } else {
                val resId = when (project.backgroundImage) {
                    "sample_living_room" -> R.drawable.sample_living_room
                    "sample_bedroom" -> R.drawable.sample_bedroom
                    "sample_home_office" -> R.drawable.sample_home_office
                    else -> R.drawable.sample_living_room
                }
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = project.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )

            // Category tag
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = project.roomType,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }

            // Close delete handler
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Bottom titles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = project.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Text(
                    text = if (project.hasUserPhoto) "My Room Photo" else "Sample Template",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedRoomType by remember { mutableStateOf("Living Room") }
    var selectedBackground by remember { mutableStateOf("sample_living_room") }
    var customImageUri by remember { mutableStateOf<Uri?>(null) }

    val roomTypes = listOf("Living Room", "Bedroom", "Office", "Custom Space")

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            customImageUri = uri
            selectedBackground = "custom"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "New Staging Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. My Sunny Office") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Room Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    roomTypes.take(2).forEach { type ->
                        FilterChip(
                            selected = selectedRoomType == type,
                            onClick = { selectedRoomType = type },
                            label = { Text(type) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    roomTypes.drop(2).forEach { type ->
                        FilterChip(
                            selected = selectedRoomType == type,
                            onClick = { selectedRoomType = type },
                            label = { Text(type) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Room Image Source",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedBackground = "sample_living_room" }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedBackground == "sample_living_room",
                            onClick = { selectedBackground = "sample_living_room" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scandi Living Room Model (Sample)", fontSize = 14.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedBackground = "sample_bedroom" }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedBackground == "sample_bedroom",
                            onClick = { selectedBackground = "sample_bedroom" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cozy Bedroom Model (Sample)", fontSize = 14.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedBackground = "sample_home_office" }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedBackground == "sample_home_office",
                            onClick = { selectedBackground = "sample_home_office" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Study Office Model (Sample)", fontSize = 14.sp)
                    }

                    Surface(
                        color = if (selectedBackground == "custom") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = if (selectedBackground == "custom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (customImageUri != null) "Custom Image Selected" else "Upload Room Photo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (selectedBackground == "custom") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (customImageUri != null) "Tap to change image" else "Choose folder photo from storage",
                                    fontSize = 11.sp,
                                    color = if (selectedBackground == "custom") MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedRoomType, selectedBackground, customImageUri) },
                enabled = selectedBackground != "custom" || customImageUri != null
            ) {
                Text("Let's Stage!")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
