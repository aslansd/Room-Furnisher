package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "room_projects")
data class RoomProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val roomType: String,
    val backgroundImage: String, // "sample_living_room", "sample_bedroom", "sample_home_office" or local Uri string
    val hasUserPhoto: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "furniture_items",
    foreignKeys = [
        ForeignKey(
            entity = RoomProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class FurnitureItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val name: String,
    val storeUrl: String,
    val category: String,
    val price: String = "",
    val notes: String = "",
    // Percentage layout positioning coordinates (0f to 1f)
    val placedX: Float = 0.5f,
    val placedY: Float = 0.5f,
    val placedWidth: Float = 0.25f,
    val placedHeight: Float = 0.25f,
    val rotation: Float = 0f,
    val colorHexTag: String = "#3F51B5"
)

@Entity(
    tableName = "staging_critiques",
    foreignKeys = [
        ForeignKey(
            entity = RoomProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StagingCritique(
    @PrimaryKey val projectId: Int,
    val analysisText: String,
    val buyingVerdict: String, // "RECOMMENDED", "STYLISH_BUT_TIGHT", "NOT_RECOMMENDED"
    val styleCompatibility: Int, // 0-100 rating
    val aiSuggestionsJson: String = ""
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM room_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<RoomProject>>

    @Query("SELECT * FROM room_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Int): RoomProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: RoomProject): Long

    @Delete
    suspend fun deleteProject(project: RoomProject)

    @Query("SELECT * FROM furniture_items WHERE projectId = :projectId")
    fun getItemsForProject(projectId: Int): Flow<List<FurnitureItem>>

    @Query("SELECT * FROM furniture_items WHERE projectId = :projectId")
    suspend fun getItemsForProjectSync(projectId: Int): List<FurnitureItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFurnitureItem(item: FurnitureItem): Long

    @Update
    suspend fun updateFurnitureItem(item: FurnitureItem)

    @Query("DELETE FROM furniture_items WHERE id = :id")
    suspend fun deleteFurnitureItemById(id: Int)

    @Query("SELECT * FROM staging_critiques WHERE projectId = :projectId LIMIT 1")
    fun getCritiqueForProject(projectId: Int): Flow<StagingCritique?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCritique(critique: StagingCritique)
}
