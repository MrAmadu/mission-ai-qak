package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.ui.screens.CreateStudioScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerStoryboardScreen

const val ROUTE_CREATE = "create"
const val ROUTE_LIBRARY = "library"
const val ROUTE_PLAYER = "player/{reelId}"

@Composable
fun ReelCraftApp(
    viewModel: ReelCraftViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val rerenderState by viewModel.rerenderState.collectAsStateWithLifecycle()
    val reels by viewModel.reels.collectAsStateWithLifecycle()

    LaunchedEffect(generationState) {
        if (generationState is GenerationUiState.Completed) {
            val reelId = (generationState as GenerationUiState.Completed).reelId
            viewModel.dismissGenerationState()
            navController.navigate("player/$reelId") {
                launchSingleTop = true
            }
        }
    }

    val showChromeNav = currentRoute == ROUTE_CREATE || currentRoute == ROUTE_LIBRARY

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showChromeNav && !useNavigationRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_CREATE,
                            onClick = {
                                navController.navigate(ROUTE_CREATE) {
                                    popUpTo(ROUTE_CREATE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_CREATE) {
                                        Icons.Filled.AutoAwesome
                                    } else {
                                        Icons.Outlined.AutoAwesome
                                    },
                                    contentDescription = stringResource(R.string.nav_create)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_create)) },
                            modifier = Modifier.testTag("nav_create_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_LIBRARY,
                            onClick = {
                                navController.navigate(ROUTE_LIBRARY) {
                                    popUpTo(ROUTE_CREATE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_LIBRARY) {
                                        Icons.Filled.VideoLibrary
                                    } else {
                                        Icons.Outlined.VideoLibrary
                                    },
                                    contentDescription = stringResource(R.string.nav_library)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_library)) },
                            modifier = Modifier.testTag("nav_library_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChromeNav && useNavigationRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_CREATE,
                            onClick = {
                                navController.navigate(ROUTE_CREATE) {
                                    popUpTo(ROUTE_CREATE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_CREATE) {
                                        Icons.Filled.AutoAwesome
                                    } else {
                                        Icons.Outlined.AutoAwesome
                                    },
                                    contentDescription = stringResource(R.string.nav_create)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_create)) }
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_LIBRARY,
                            onClick = {
                                navController.navigate(ROUTE_LIBRARY) {
                                    popUpTo(ROUTE_CREATE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_LIBRARY) {
                                        Icons.Filled.VideoLibrary
                                    } else {
                                        Icons.Outlined.VideoLibrary
                                    },
                                    contentDescription = stringResource(R.string.nav_library)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_library)) }
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CREATE,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(ROUTE_CREATE) {
                        CreateStudioScreen(
                            studioState = studioState,
                            generationState = generationState,
                            onPromptChange = viewModel::updatePrompt,
                            onApplyInspiration = viewModel::applyInspirationPrompt,
                            onSelectStyle = viewModel::selectStyle,
                            onSelectAspectRatio = viewModel::selectAspectRatio,
                            onSelectDuration = viewModel::selectDuration,
                            onSelectEngineMode = viewModel::selectEngineMode,
                            onToggleVoiceover = viewModel::setIncludeVoiceover,
                            onEnhancePrompt = viewModel::enhancePromptWithAi,
                            onGenerateVideo = viewModel::generateVideo,
                            onCancelGeneration = viewModel::cancelGeneration,
                            onDismissError = viewModel::dismissGenerationState
                        )
                    }

                    composable(ROUTE_LIBRARY) {
                        LibraryScreen(
                            reels = reels,
                            onOpenReel = { id ->
                                navController.navigate("player/$id") {
                                    launchSingleTop = true
                                }
                            },
                            onDeleteReel = viewModel::deleteReel,
                            onCreateNew = {
                                navController.navigate(ROUTE_CREATE) {
                                    popUpTo(ROUTE_CREATE) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(
                        route = ROUTE_PLAYER,
                        arguments = listOf(navArgument("reelId") { type = NavType.StringType })
                    ) { backStack ->
                        val reelId = backStack.arguments?.getString("reelId").orEmpty()
                        val reel by viewModel.observeReel(reelId).collectAsStateWithLifecycle(initialValue = null)
                        PlayerStoryboardScreen(
                            reel = reel,
                            rerenderState = rerenderState,
                            onBack = { navController.popBackStack() },
                            onRerender = viewModel::rerenderStoryboard,
                            onRerenderHandled = viewModel::resetRerenderState
                        )
                    }
                }
            }
        }
    }
}
