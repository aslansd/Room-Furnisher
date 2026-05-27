package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.FurnitureItem
import com.example.data.database.RoomProject
import com.example.data.database.StagingCritique
import com.example.data.repository.ProjectRepository
import com.example.data.network.GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StagingViewModel(private val repository: ProjectRepository) : ViewModel() {

    val allProjects: StateFlow<List<RoomProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProject = MutableStateFlow<RoomProject?>(null)
    val activeProject: StateFlow<RoomProject?> = _activeProject.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    val activeFurnitureItems: StateFlow<List<FurnitureItem>> = _activeProject
        .flatMapLatest { project ->
            if (project != null) {
                repository.getItemsForProject(project.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCritique: StateFlow<StagingCritique?> = _activeProject
        .flatMapLatest { project ->
            if (project != null) {
                repository.getCritiqueForProject(project.id)
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectProject(project: RoomProject?) {
        _activeProject.value = project
    }

    fun selectProjectById(id: Int) {
        viewModelScope.launch {
            val project = repository.getProjectById(id)
            _activeProject.value = project
        }
    }

    fun createNewProject(name: String, roomType: String, backgroundType: String, customUri: Uri?, context: Context) {
        viewModelScope.launch {
            val finalBg: String
            val hasUserPhoto: Boolean

            if (backgroundType == "custom" && customUri != null) {
                val localPath = copyImageToInternalStorage(customUri, context)
                finalBg = localPath ?: "sample_living_room"
                hasUserPhoto = localPath != null
            } else {
                finalBg = backgroundType
                hasUserPhoto = false
            }

            val project = RoomProject(
                name = name.ifBlank { "Unfurnished $roomType" },
                roomType = roomType,
                backgroundImage = finalBg,
                hasUserPhoto = hasUserPhoto
            )

            val newId = repository.insertProject(project)
            val createdProject = repository.getProjectById(newId)
            _activeProject.value = createdProject
        }
    }

    fun deleteProject(project: RoomProject) {
        viewModelScope.launch {
            if (project.hasUserPhoto) {
                try {
                    val file = File(project.backgroundImage)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            repository.deleteProject(project)
            if (_activeProject.value?.id == project.id) {
                _activeProject.value = null
            }
        }
    }

    fun addFurnitureItem(projectId: Int, name: String, storeUrl: String, category: String, price: String, colorHex: String) {
        viewModelScope.launch {
            val item = FurnitureItem(
                projectId = projectId,
                name = name,
                storeUrl = storeUrl,
                category = category,
                price = price,
                colorHexTag = colorHex,
                placedX = 0.4f + (Math.random() * 0.2f).toFloat(),
                placedY = 0.4f + (Math.random() * 0.2f).toFloat(),
                placedWidth = 0.22f,
                placedHeight = 0.22f
            )
            repository.insertFurnitureItem(item)
        }
    }

    fun updateItemPosition(item: FurnitureItem, x: Float, y: Float) {
        viewModelScope.launch {
            val updated = item.copy(placedX = x.coerceIn(0f, 1f), placedY = y.coerceIn(0f, 1f))
            repository.updateFurnitureItem(updated)
        }
    }

    fun updateItemScaleRotation(item: FurnitureItem, width: Float, height: Float, rotation: Float) {
        viewModelScope.launch {
            val updated = item.copy(
                placedWidth = width.coerceIn(0.1f, 0.8f),
                placedHeight = height.coerceIn(0.1f, 0.8f),
                rotation = rotation
            )
            repository.updateFurnitureItem(updated)
        }
    }

    fun deleteFurnitureItem(id: Int) {
        viewModelScope.launch {
            repository.deleteFurnitureItemById(id)
        }
    }

    fun addSampleFurnitureItems(projectId: Int) {
        viewModelScope.launch {
            val samples = listOf(
                FurnitureItem(
                    projectId = projectId,
                    name = "IKEA Strandmon Wing Chair",
                    storeUrl = "https://www.ikea.com/us/en/p/strandmon-wing-chair-nordvalla-dark-grey-90359829/",
                    category = "Chair",
                    price = "$249",
                    colorHexTag = "#FFA07A", // Warm sandy orange
                    placedX = 0.25f,
                    placedY = 0.65f,
                    placedWidth = 0.20f,
                    placedHeight = 0.25f
                ),
                FurnitureItem(
                    projectId = projectId,
                    name = "West Elm Oak Study Desk",
                    storeUrl = "https://www.westelm.com/products/mid-century-desk-acorn-h209/",
                    category = "Desk",
                    price = "$499",
                    colorHexTag = "#D2B48C", // Tan wood outline
                    placedX = 0.65f,
                    placedY = 0.55f,
                    placedWidth = 0.32f,
                    placedHeight = 0.24f
                ),
                FurnitureItem(
                    projectId = projectId,
                    name = "Matte Black Standing Lamp",
                    storeUrl = "https://www.amazon.com/dp/B08V8DYXZN/",
                    category = "Lamp",
                    price = "$45",
                    colorHexTag = "#FFE4B5", // Soft golden light
                    placedX = 0.12f,
                    placedY = 0.45f,
                    placedWidth = 0.15f,
                    placedHeight = 0.35f
                )
            )
            for (item in samples) {
                repository.insertFurnitureItem(item)
            }
        }
    }

    fun analyzeStagingLayout(context: Context) {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                val items = repository.getItemsForProjectSync(project.id)
                val bitmap = loadRoomBitmap(project, context)

                val result = GeminiApiClient.analyzeStaging(
                    roomBitmap = bitmap,
                    roomType = project.roomType,
                    furnitureItems = items
                )

                val critique = StagingCritique(
                    projectId = project.id,
                    analysisText = result.analysisText,
                    buyingVerdict = result.buyingVerdict,
                    styleCompatibility = result.styleCompatibility,
                    aiSuggestionsJson = result.aiPlacementNotes
                )

                repository.insertCritique(critique)
            } catch (e: Exception) {
                _analysisError.value = e.localizedMessage
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private suspend fun loadRoomBitmap(project: RoomProject, context: Context): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (project.hasUserPhoto) {
                val file = File(project.backgroundImage)
                if (file.exists()) {
                    return@withContext BitmapFactory.decodeFile(file.absolutePath)
                }
            } else {
                // Load from drawable resources
                val resId = when (project.backgroundImage) {
                    "sample_living_room" -> com.example.R.drawable.sample_living_room
                    "sample_bedroom" -> com.example.R.drawable.sample_bedroom
                    "sample_home_office" -> com.example.R.drawable.sample_home_office
                    else -> com.example.R.drawable.sample_living_room
                }
                return@withContext BitmapFactory.decodeResource(context.resources, resId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private suspend fun copyImageToInternalStorage(uri: Uri, context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "room_photo_$timeStamp.jpg"
            val directory = File(context.filesDir, "room_photos")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, filename)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return@withContext file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}

class StagingViewModelFactory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StagingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StagingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
