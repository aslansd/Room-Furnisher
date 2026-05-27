package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProjectDetailsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StagingViewModel
import com.example.ui.viewmodel.StagingViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room Local Database and Repository Pattern inline securely
        val database = AppDatabase.getDatabase(this)
        val repository = ProjectRepository(database.projectDao())

        setContent {
            MyApplicationTheme {
                val viewModel: StagingViewModel by viewModels {
                    StagingViewModelFactory(repository)
                }

                val activeProject by viewModel.activeProject.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BoxWithNavigation(
                        activeProject = activeProject,
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BoxWithNavigation(
    activeProject: com.example.data.database.RoomProject?,
    viewModel: StagingViewModel,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = activeProject,
        label = "screen_navigation"
    ) { project ->
        if (project != null) {
            ProjectDetailsScreen(
                viewModel = viewModel,
                project = project,
                onBack = { viewModel.selectProject(null) },
                modifier = modifier
            )
        } else {
            DashboardScreen(
                viewModel = viewModel,
                onProjectSelected = { selected -> viewModel.selectProject(selected) },
                modifier = modifier
            )
        }
    }
}
