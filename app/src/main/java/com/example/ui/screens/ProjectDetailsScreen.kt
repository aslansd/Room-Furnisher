package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.database.FurnitureItem
import com.example.data.database.RoomProject
import com.example.data.database.StagingCritique
import com.example.ui.viewmodel.StagingViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsScreen(
    viewModel: StagingViewModel,
    project: RoomProject,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by viewModel.activeFurnitureItems.collectAsState()
    val critique by viewModel.activeCritique.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val error by viewModel.analysisError.collectAsState()

    var selectedItemId by remember { mutableStateOf<Int?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showCritiquePanel by remember { mutableStateOf(false) }

    // Track the previous state of isAnalyzing to safely auto-show panel only when active analysis concludes
    var wasAnalyzing by remember { mutableStateOf(false) }

    LaunchedEffect(isAnalyzing) {
        if (wasAnalyzing && !isAnalyzing && critique != null) {
            showCritiquePanel = true
        }
        wasAnalyzing = isAnalyzing
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = project.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = project.roomType, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.addSampleFurnitureItems(project.id) },
                        modifier = Modifier.testTag("add_samples_btn")
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Add Samples", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (critique != null) {
                        IconButton(onClick = { showCritiquePanel = !showCritiquePanel }) {
                            Icon(imageVector = Icons.Default.Comment, contentDescription = "View critique", tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Interactive Staging Surface Canvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .background(Color.Black)
            ) {
                val containerWidth = maxWidth
                val containerHeight = maxHeight
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()

                // 1. Render Background View
                if (project.hasUserPhoto) {
                    val file = File(project.backgroundImage)
                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Room Canvas",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Room image missing locally", color = Color.White)
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
                        contentDescription = "Room Background Model",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Instructions Overlay Hint (Fades out when item is added and dragged)
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Empty Room Stage",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '✨ Add Samples' top right or '+' bottom right to place virtual furniture tags on top of your room image!",
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.widthIn(max = 280.dp)
                            )
                        }
                    }
                }

                // 2. Render Placed Furniture Overlays
                items.forEach { item ->
                    key(item.id) {
                        var tempX by remember { mutableStateOf(item.placedX) }
                        var tempY by remember { mutableStateOf(item.placedY) }

                        // Sync with outer database updates (such as sample loading or undo actions)
                        LaunchedEffect(item.placedX, item.placedY) {
                            tempX = item.placedX
                            tempY = item.placedY
                        }

                        val isSelected = selectedItemId == item.id

                        val xOffsetDouble = tempX * containerWidth.value
                        val yOffsetDouble = tempY * containerHeight.value

                        // Capture parameters in remembered states to avoid gesture listener cancellations or restarts
                        val currentItem by rememberUpdatedState(item)
                        val currentTempX by rememberUpdatedState(tempX)
                        val currentTempY by rememberUpdatedState(tempY)
                        val currentWidthPx by rememberUpdatedState(widthPx)
                        val currentHeightPx by rememberUpdatedState(heightPx)

                        val onDragEndState by rememberUpdatedState {
                            viewModel.updateItemPosition(currentItem, currentTempX, currentTempY)
                        }

                        val onDragState by rememberUpdatedState { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                            change.consume()
                            val divisorX = if (currentWidthPx > 0f) currentWidthPx else 1f
                            val divisorY = if (currentHeightPx > 0f) currentHeightPx else 1f
                            tempX = (tempX + dragAmount.x / divisorX).coerceIn(0f, 0.85f)
                            tempY = (tempY + dragAmount.y / divisorY).coerceIn(0f, 0.85f)
                        }

                        Box(
                            modifier = Modifier
                                .absoluteOffset(x = xOffsetDouble.dp, y = yOffsetDouble.dp)
                                .size(
                                    width = (item.placedWidth * containerWidth.value).dp,
                                    height = (item.placedHeight * containerHeight.value).dp
                                )
                                .rotate(item.rotation)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedItemId = item.id }
                                .pointerInput(item.id) {
                                    detectDragGestures(
                                        onDragEnd = { onDragEndState() },
                                        onDrag = { change, dragAmount -> onDragState(change, dragAmount) }
                                    )
                                }
                                .testTag("canvas_item_${item.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val categoryEmoji = when (item.category.lowercase()) {
                                    "chair" -> "🪑"
                                    "sofa", "couch" -> "🛋️"
                                    "desk", "table" -> "🖥️"
                                    "lamp", "light" -> "💡"
                                    "rug" -> "🧱"
                                    else -> "📦"
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = categoryEmoji, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = item.price.ifEmpty { "$--" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = item.name,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }

                            // Interactive Adjustment Handles overlay when selected
                            if (isSelected) {
                                // Delete floating tag on top-right
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color.Red, shape = CircleShape)
                                        .clickable {
                                            viewModel.deleteFurnitureItem(item.id)
                                            selectedItemId = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                // Selection feedback & Controls bar floating beneath
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp)
                                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Bigger",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                viewModel.updateItemScaleRotation(
                                                    item,
                                                    item.placedWidth * 1.1f,
                                                    item.placedHeight * 1.1f,
                                                    item.rotation
                                                )
                                            }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = "Rotate",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                viewModel.updateItemScaleRotation(
                                                    item,
                                                    item.placedWidth,
                                                    item.placedHeight,
                                                    item.rotation + 45f
                                                )
                                            }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ZoomOut,
                                        contentDescription = "Smaller",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                viewModel.updateItemScaleRotation(
                                                    item,
                                                    item.placedWidth * 0.9f,
                                                    item.placedHeight * 0.9f,
                                                    item.rotation
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Staging Details Control Bar & Shopping Panel (Bottom half)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Title for Bottom list and Analysis FAB
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Home Stuffs Links list",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${items.size} product items tracked",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Trigger Gemini analysis button
                        Button(
                            onClick = { viewModel.analyzeStagingLayout(context) },
                            enabled = !isAnalyzing && items.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("analyze_staging_button")
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI Stylist analyzing...", fontSize = 13.sp)
                            } else {
                                Icon(imageVector = Icons.Default.BubbleChart, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ask Stylist AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Grid or Row of items
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { showAddItemDialog = true }
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add store product URL")
                            }
                        }
                    } else {
                        // Vertical scrolling list of item links with Launch capabilities
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(items) { furniture ->
                                FurnitureLinkRowCard(
                                    item = furniture,
                                    isSelected = selectedItemId == furniture.id,
                                    onSelect = { selectedItemId = furniture.id },
                                    onDelete = { viewModel.deleteFurnitureItem(furniture.id) },
                                    onOpenUrl = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(furniture.storeUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback browser trigger or toast
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Add item FAB floating
                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag("add_item_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Furniture Link")
                }
            }
        }

        // Add Store link Dialog
        if (showAddItemDialog) {
            AddFurnitureDialog(
                onDismiss = { showAddItemDialog = false },
                onAdd = { name, link, category, price, colorHex ->
                    viewModel.addFurnitureItem(project.id, name, link, category, price, colorHex)
                    showAddItemDialog = false
                }
            )
        }

        // Slide up bottom sheet for AI Critique & Styling guidelines
        if (showCritiquePanel) {
            val activeCritique = critique
            if (activeCritique != null) {
                ModalBottomSheet(
                    onDismissRequest = { showCritiquePanel = false },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    CritiquePanelContent(
                        critique = activeCritique,
                        onDismiss = { showCritiquePanel = false }
                    )
                }
            }
        }
    }
}

@Composable
fun FurnitureLinkRowCard(
    item: FurnitureItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val categoryIcon = when (item.category.lowercase()) {
                    "chair" -> "🪑"
                    "sofa" -> "🛋️"
                    "desk" -> "🖥️"
                    "lamp" -> "💡"
                    else -> "📦"
                }
                Text(text = categoryIcon, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.category,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.price.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.price,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = item.storeUrl,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action: Buy / View website
            FilledIconButton(
                onClick = onOpenUrl,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Buy Now",
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove item",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AddFurnitureDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, link: String, category: String, price: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var storeUrl by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Chair") }
    var price by remember { mutableStateOf("") }

    val categories = listOf("Chair", "Sofa", "Desk", "Lamp", "Other Decor")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add Furniture Link", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name / details") },
                    placeholder = { Text("e.g. IKEA Strandmon armchair") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = storeUrl,
                    onValueChange = { storeUrl = it },
                    label = { Text("Online Store Link URL") },
                    placeholder = { Text("https://www.ikea.com/...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price (optional)") },
                        placeholder = { Text("e.g. $199") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Category", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name.ifBlank { "Unlabelled product" }, storeUrl, selectedCategory, price, "#3F51B5") },
                enabled = storeUrl.isNotBlank()
            ) {
                Text("Place on Stage")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CritiquePanelContent(
    critique: StagingCritique,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Stylist Staging Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Constructive Layout Coordination Analysis",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Score dial ring & rating
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Transparent, shape = CircleShape)
                    .border(
                        width = 4.dp,
                        color = when (critique.styleCompatibility) {
                            in 80..100 -> Color(0xFF4CAF50)
                            in 60..79 -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${critique.styleCompatibility}%",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                val verdictColor = when (critique.buyingVerdict.uppercase()) {
                    "RECOMMENDED" -> Color(0xFF4CAF50)
                    "STYLISH_BUT_TIGHT" -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }

                val verdictText = when (critique.buyingVerdict.uppercase()) {
                    "RECOMMENDED" -> "🌟 HIGHLY RECOMMENDED MATCH"
                    "STYLISH_BUT_TIGHT" -> "📐 STYLISH, BUT SPACE TIGHT"
                    else -> "⚠️ STYLE OR DIMENSIONS CLASH"
                }

                Text(
                    text = verdictText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = verdictColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Calculated style compatibility matching wood tone, lighting and layout flow of custom items.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "DESIGN STYLE REPORT",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Style Analysis Content
        Text(
            text = critique.analysisText.trimIndent(),
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (critique.aiSuggestionsJson.isNotEmpty()) {
            Text(
                text = "PLACEMENT CRITIQUES",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = critique.aiSuggestionsJson,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
