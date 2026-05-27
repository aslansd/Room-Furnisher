package com.example.data.repository

import com.example.data.database.ProjectDao
import com.example.data.database.RoomProject
import com.example.data.database.FurnitureItem
import com.example.data.database.StagingCritique
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<RoomProject>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Int): RoomProject? {
        return projectDao.getProjectById(id)
    }

    suspend fun insertProject(project: RoomProject): Int {
        return projectDao.insertProject(project).toInt()
    }

    suspend fun deleteProject(project: RoomProject) {
        projectDao.deleteProject(project)
    }

    fun getItemsForProject(projectId: Int): Flow<List<FurnitureItem>> {
        return projectDao.getItemsForProject(projectId)
    }

    suspend fun getItemsForProjectSync(projectId: Int): List<FurnitureItem> {
        return projectDao.getItemsForProjectSync(projectId)
    }

    suspend fun insertFurnitureItem(item: FurnitureItem): Long {
        return projectDao.insertFurnitureItem(item)
    }

    suspend fun updateFurnitureItem(item: FurnitureItem) {
        projectDao.updateFurnitureItem(item)
    }

    suspend fun deleteFurnitureItemById(id: Int) {
        projectDao.deleteFurnitureItemById(id)
    }

    fun getCritiqueForProject(projectId: Int): Flow<StagingCritique?> {
        return projectDao.getCritiqueForProject(projectId)
    }

    suspend fun insertCritique(critique: StagingCritique) {
        projectDao.insertCritique(critique)
    }
}
