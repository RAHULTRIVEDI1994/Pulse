package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.IosConnectionPopupSheet
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.GesturesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.screens.UtilitiesScreen

object PodPulseRoutes {
    const val STATUS = "status"
    const val GESTURES = "gestures"
    const val EQUALIZER = "equalizer"
    const val UTILITIES = "utilities"
    const val SETTINGS = "settings"
}

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val primaryTabs = listOf(
    NavTab(
        route = PodPulseRoutes.STATUS,
        labelRes = R.string.nav_status,
        selectedIcon = Icons.Filled.Headphones,
        unselectedIcon = Icons.Outlined.Headphones
    ),
    NavTab(
        route = PodPulseRoutes.GESTURES,
        labelRes = R.string.nav_gestures,
        selectedIcon = Icons.Filled.TouchApp,
        unselectedIcon = Icons.Outlined.TouchApp
    ),
    NavTab(
        route = PodPulseRoutes.EQUALIZER,
        labelRes = R.string.nav_equalizer,
        selectedIcon = Icons.Filled.GraphicEq,
        unselectedIcon = Icons.Outlined.GraphicEq
    ),
    NavTab(
        route = PodPulseRoutes.UTILITIES,
        labelRes = R.string.nav_utilities,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodPulseApp(
    viewModel: PodPulseViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: PodPulseRoutes.STATUS

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionsAndStartScanning()
    }

    val requestAllNeededPermissions = {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (currentRoute == PodPulseRoutes.SETTINGS) {
                                stringResource(R.string.nav_settings)
                            } else {
                                stringResource(R.string.app_name)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (currentRoute == PodPulseRoutes.SETTINGS) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.testTag("top_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.openIosPopupPreview() },
                            modifier = Modifier.testTag("top_popup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Open iOS connection popup"
                            )
                        }
                        if (currentRoute != PodPulseRoutes.SETTINGS) {
                            IconButton(
                                onClick = { navController.navigate(PodPulseRoutes.SETTINGS) },
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open settings"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail && currentRoute != PodPulseRoutes.SETTINGS) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && currentRoute != PodPulseRoutes.SETTINGS) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = PodPulseRoutes.STATUS,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(PodPulseRoutes.STATUS) {
                        StatusScreen(
                            state = state,
                            onRequestPermissions = requestAllNeededPermissions,
                            onRestartScan = { viewModel.restartBleScan() },
                            onToggleMonitorService = { viewModel.setMonitorServiceEnabled(it) },
                            onOpenPopup = { viewModel.openIosPopupPreview() },
                            onSpeakBattery = { viewModel.speakBatteryNow() },
                            onToggleAutoEarPause = { viewModel.updateAutoEarPause(it) },
                            onToggleBassBoost = { viewModel.setBassBoostEnabled(it) },
                            onVolumeChange = { viewModel.setMusicVolume(it) },
                            onOpenGesturesTab = { navigateToTab(PodPulseRoutes.GESTURES) },
                            onOpenEqualizerTab = { navigateToTab(PodPulseRoutes.EQUALIZER) }
                        )
                    }
                    composable(PodPulseRoutes.GESTURES) {
                        GesturesScreen(
                            state = state,
                            onSetGesturesEnabled = { viewModel.setGesturesEnabled(it) },
                            onSetInterceptMediaButtons = { viewModel.setInterceptMediaButtons(it) },
                            onSetRequireAirPodsConnected = { viewModel.setRequireAirPodsConnected(it) },
                            onSetMultiPressWindowMs = { viewModel.setMultiPressWindowMs(it) },
                            onSetVolumeSteps = { viewModel.setVolumeStepCount(it) },
                            onUpdateMapping = { g, a -> viewModel.updateGestureMapping(g, a) },
                            onResetMappings = { viewModel.resetGestureMappings() },
                            onTestGesture = { viewModel.testGesture(it) },
                            onClearEventLog = { viewModel.clearGestureEventLog() }
                        )
                    }
                    composable(PodPulseRoutes.EQUALIZER) {
                        EqualizerScreen(
                            state = state,
                            onSetEqEnabled = { viewModel.setEqEnabled(it) },
                            onApplyPreset = { viewModel.applyEqPreset(it) },
                            onUpdateBand = { idx, db -> viewModel.updateEqBand(idx, db) },
                            onSetBassBoostEnabled = { viewModel.setBassBoostEnabled(it) },
                            onSetBassBoostStrength = { viewModel.setBassBoostStrength(it) },
                            onSetLoudnessEnabled = { viewModel.setLoudnessEnabled(it) }
                        )
                    }
                    composable(PodPulseRoutes.UTILITIES) {
                        UtilitiesScreen(
                            state = state,
                            onToggleFindMy = { viewModel.toggleFindMy(it) },
                            onStopFindMy = { viewModel.stopFindMy() },
                            onSpeakBattery = { viewModel.speakBatteryNow() }
                        )
                    }
                    composable(PodPulseRoutes.SETTINGS) {
                        SettingsScreen(
                            state = state,
                            onRequestPermissions = requestAllNeededPermissions,
                            onTogglePopupOnLidOpen = { viewModel.updatePopupOnLidOpen(it) },
                            onToggleAutoEarPause = { viewModel.updateAutoEarPause(it) },
                            onToggleAutoEarResume = { viewModel.updateAutoEarResume(it) },
                            onToggleAnnounceBattery = { viewModel.updateAnnounceBatteryOnConnect(it) },
                            onSelectLowBatteryThreshold = { viewModel.updateLowBatteryThreshold(it) },
                            onResetGestures = { viewModel.resetGestureMappings() }
                        )
                    }
                }
            }
        }
    }

    if (state.showIosPopupSheet) {
        IosConnectionPopupSheet(
            telemetry = state.telemetry,
            isAudioRouted = state.isAirPodsAudioRouted,
            onDismiss = { viewModel.dismissIosPopup() },
            onSpeakBattery = { viewModel.speakBatteryNow() },
            onOpenEqualizer = { navigateToTab(PodPulseRoutes.EQUALIZER) }
        )
    }
}
