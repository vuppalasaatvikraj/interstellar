package com.bitchat.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bitchat.android.geohash.LocationChannelManager
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.MeshService
import com.bitchat.android.nostr.PoWPreferenceManager
import com.bitchat.android.onboarding.BatteryOptimizationManager
import com.bitchat.android.onboarding.BatteryOptimizationPreferenceManager
import com.bitchat.android.onboarding.BatteryOptimizationStatus
import com.bitchat.android.onboarding.BluetoothStatus
import com.bitchat.android.onboarding.BluetoothStatusManager
import com.bitchat.android.onboarding.InitializationErrorScreen
import com.bitchat.android.onboarding.LocationStatus
import com.bitchat.android.onboarding.LocationStatusManager
import com.bitchat.android.onboarding.OnboardingCoordinator
import com.bitchat.android.onboarding.OnboardingState
import com.bitchat.android.onboarding.PermissionManager
import com.bitchat.android.services.VerificationService
import com.bitchat.android.ui.ChatViewModel
import com.bitchat.android.ui.OrientationAwareActivity
import com.bitchat.android.ui.messenger.ConversationScreen
import com.bitchat.android.ui.messenger.HomeScreen
import com.bitchat.android.ui.messenger.SaturnOnboardingScreen
import com.bitchat.android.ui.messenger.SaturnOnboardingStep
import com.bitchat.android.ui.messenger.SaturnPlanetSplashScreen
import com.bitchat.android.ui.theme.BitchatTheme
import com.bitchat.android.wifiaware.WifiAwareController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : OrientationAwareActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var onboardingCoordinator: OnboardingCoordinator
    private lateinit var bluetoothStatusManager: BluetoothStatusManager
    private lateinit var locationStatusManager: LocationStatusManager
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager

    private lateinit var meshService: BluetoothMeshService
    private lateinit var unifiedMeshService: MeshService
    private val mainViewModel: MainViewModel by viewModels()
    private var pendingMeshForegroundServiceStart = false
    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, meshService, unifiedMeshService) as T
            }
        }
    }

    private val forceFinishReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action == com.bitchat.android.util.AppConstants.UI.ACTION_FORCE_FINISH) {
                Log.i("MainActivity", "Received force finish broadcast, closing UI")
                finishAffinity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.setRecentsScreenshotEnabled(false)
        }
        val filter = android.content.IntentFilter(com.bitchat.android.util.AppConstants.UI.ACTION_FORCE_FINISH)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                forceFinishReceiver,
                filter,
                com.bitchat.android.util.AppConstants.UI.PERMISSION_FORCE_FINISH,
                null,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                forceFinishReceiver,
                filter,
                com.bitchat.android.util.AppConstants.UI.PERMISSION_FORCE_FINISH,
                null
            )
        }

        if (intent.getBooleanExtra("ACTION_QUIT_APP", false)) {
            Log.d("MainActivity", "Quit request received in onCreate, finishing activity")
            finish()
            return
        }

        com.bitchat.android.service.AppShutdownCoordinator.cancelPendingShutdown()
        enableEdgeToEdge()

        permissionManager = PermissionManager(this)
        startMeshForegroundServiceBestEffort()
        meshService = com.bitchat.android.service.MeshServiceHolder.getOrCreate(applicationContext)
        unifiedMeshService = com.bitchat.android.service.MeshServiceHolder.getUnifiedOrCreate(applicationContext)

        bluetoothStatusManager = BluetoothStatusManager(
            activity = this,
            context = this,
            onBluetoothEnabled = ::handleBluetoothEnabled,
            onBluetoothDisabled = ::handleBluetoothDisabled
        )
        locationStatusManager = LocationStatusManager(
            activity = this,
            context = this,
            onLocationEnabled = ::handleLocationEnabled,
            onLocationDisabled = ::handleLocationDisabled
        )
        batteryOptimizationManager = BatteryOptimizationManager(
            activity = this,
            context = this,
            onBatteryOptimizationDisabled = ::handleBatteryOptimizationDisabled,
            onBatteryOptimizationFailed = ::handleBatteryOptimizationFailed
        )
        onboardingCoordinator = OnboardingCoordinator(
            activity = this,
            permissionManager = permissionManager,
            onOnboardingComplete = ::handleOnboardingComplete,
            onBackgroundLocationRequired = {
                mainViewModel.updateOnboardingState(OnboardingState.BACKGROUND_LOCATION_EXPLANATION)
            },
            onOnboardingFailed = ::handleOnboardingFailed
        )

        setContent {
            BitchatTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    OnboardingFlowScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.onboardingState.collect { state ->
                    handleOnboardingStateChange(state)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WifiAwareController.running.collect { running ->
                    if (running && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        unifiedMeshService.delegate = chatViewModel
                    }
                }
            }
        }

        if (mainViewModel.onboardingState.value == OnboardingState.CHECKING) {
            checkOnboardingStatus()
        }
    }

    @Composable
    private fun OnboardingFlowScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val currentNickname by com.bitchat.android.services.AppStateStore.nickname.collectAsState()
        var draftNickname by remember { mutableStateOf(currentNickname) }
        var identityConfirmed by remember { mutableStateOf(currentNickname.isNotBlank()) }

        val onboardingState by mainViewModel.onboardingState.collectAsState()
        val errorMessage by mainViewModel.errorMessage.collectAsState()
        val isBluetoothLoading by mainViewModel.isBluetoothLoading.collectAsState()
        val isLocationLoading by mainViewModel.isLocationLoading.collectAsState()
        val isBatteryOptimizationLoading by mainViewModel.isBatteryOptimizationLoading.collectAsState()

        var showSplash by remember { mutableStateOf(true) }
        var activeThread by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }

        if (showSplash) {
            SaturnPlanetSplashScreen(onFinished = { showSplash = false })
            return
        }

        DisposableEffect(context, bluetoothStatusManager) {
            val receiver = bluetoothStatusManager.monitorBluetoothState(
                context = context,
                bluetoothStatusManager = bluetoothStatusManager,
                onBluetoothStateChanged = { status ->
                    if (status == BluetoothStatus.ENABLED && onboardingState == OnboardingState.BLUETOOTH_CHECK) {
                        checkBluetoothAndProceed()
                    }
                }
            )

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: IllegalStateException) {
                    Log.w("BluetoothStatusUI", "Receiver was not registered")
                }
            }
        }

        when (onboardingState) {
            OnboardingState.CHECKING,
            OnboardingState.INITIALIZING -> {
                if (!identityConfirmed) {
                    SaturnOnboardingScreen(
                        currentStep = SaturnOnboardingStep.IDENTITY,
                        nickname = draftNickname,
                        onNicknameChange = { draftNickname = it },
                        onPrimaryAction = {
                            val clean = draftNickname.trim()
                            if (clean.isNotEmpty()) {
                                chatViewModel.setNickname(clean)
                                identityConfirmed = true
                                checkOnboardingStatus()
                            }
                        }
                    )
                }
            }

            OnboardingState.COMPLETE -> {
                val currentThread = activeThread
                if (currentThread != null) {
                    ConversationScreen(
                        threadId = currentThread.first,
                        threadTitle = currentThread.second,
                        isGroup = currentThread.third,
                        chatViewModel = chatViewModel,
                        onBack = { activeThread = null }
                    )
                } else {
                    HomeScreen(
                        chatViewModel = chatViewModel,
                        founderNames = "Saatvik Raj & Khayum Shaik",
                        onOpenChat = { id, title, isGroup ->
                            activeThread = Triple(id, title, isGroup)
                        }
                    )
                }
            }

            OnboardingState.PERMISSION_EXPLANATION,
            OnboardingState.PERMISSION_REQUESTING,
            OnboardingState.BLUETOOTH_CHECK,
            OnboardingState.BACKGROUND_LOCATION_EXPLANATION -> {
                if (!identityConfirmed) {
                    SaturnOnboardingScreen(
                        currentStep = SaturnOnboardingStep.IDENTITY,
                        nickname = draftNickname,
                        onNicknameChange = { draftNickname = it },
                        onPrimaryAction = {
                            val clean = draftNickname.trim()
                            if (clean.isNotEmpty()) {
                                chatViewModel.setNickname(clean)
                                identityConfirmed = true
                            }
                        }
                    )
                } else {
                    SaturnOnboardingScreen(
                        currentStep = SaturnOnboardingStep.NEARBY_PERMISSIONS,
                        nickname = draftNickname,
                        onNicknameChange = {},
                        isLoading = isBluetoothLoading || onboardingState == OnboardingState.PERMISSION_REQUESTING,
                        onPrimaryAction = {
                            when (onboardingState) {
                                OnboardingState.BLUETOOTH_CHECK -> {
                                    mainViewModel.updateBluetoothLoading(true)
                                    bluetoothStatusManager.requestEnableBluetooth()
                                }
                                OnboardingState.BACKGROUND_LOCATION_EXPLANATION -> {
                                    onboardingCoordinator.requestBackgroundLocation()
                                }
                                else -> {
                                    mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_REQUESTING)
                                    onboardingCoordinator.requestPermissions()
                                }
                            }
                        },
                        onSkipStep = {
                            when (onboardingState) {
                                OnboardingState.BLUETOOTH_CHECK -> {
                                    mainViewModel.skipBluetoothCheck()
                                    checkLocationAndProceed()
                                }
                                OnboardingState.BACKGROUND_LOCATION_EXPLANATION -> {
                                    onboardingCoordinator.skipBackgroundLocation()
                                }
                                else -> {
                                    checkLocationAndProceed()
                                }
                            }
                        }
                    )
                }
            }

            OnboardingState.LOCATION_CHECK -> {
                SaturnOnboardingScreen(
                    currentStep = SaturnOnboardingStep.LOCATION_SWITCH,
                    nickname = draftNickname,
                    onNicknameChange = {},
                    isLoading = isLocationLoading,
                    onPrimaryAction = {
                        mainViewModel.updateLocationLoading(true)
                        locationStatusManager.requestEnableLocation()
                    },
                    onSkipStep = { checkBatteryOptimizationAndProceed() }
                )
            }

            OnboardingState.BATTERY_OPTIMIZATION_CHECK -> {
                SaturnOnboardingScreen(
                    currentStep = SaturnOnboardingStep.BATTERY_AWAKE,
                    nickname = draftNickname,
                    onNicknameChange = {},
                    isLoading = isBatteryOptimizationLoading,
                    onPrimaryAction = {
                        mainViewModel.updateBatteryOptimizationLoading(true)
                        batteryOptimizationManager.requestDisableBatteryOptimization()
                    },
                    onSkipStep = { proceedWithPermissionCheck() }
                )
            }

            OnboardingState.ERROR -> {
                InitializationErrorScreen(
                    modifier = modifier,
                    errorMessage = errorMessage,
                    onRetry = {
                        mainViewModel.updateOnboardingState(OnboardingState.CHECKING)
                        checkOnboardingStatus()
                    },
                    onOpenSettings = {
                        onboardingCoordinator.openAppSettings()
                    }
                )
            }
        }
    }

    private fun handleOnboardingStateChange(state: OnboardingState) {
        when (state) {
            OnboardingState.COMPLETE -> {
                Log.i("MainActivity", "Onboarding completed - app ready")
            }
            OnboardingState.ERROR -> {
                Log.e("MainActivity", "Onboarding error state reached")
            }
            else -> {}
        }
    }

    private fun checkOnboardingStatus() {
        lifecycleScope.launch {
            delay(500)
            checkBluetoothAndProceed()
        }
    }

    private fun checkBluetoothAndProceed() {
        if (mainViewModel.isBluetoothCheckSkipped.value) {
            checkLocationAndProceed()
            return
        }

        if (permissionManager.isFirstTimeLaunch()) {
            proceedWithPermissionCheck()
            return
        }

        bluetoothStatusManager.logBluetoothStatus()
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())

        val bleRequired = try {
            com.bitchat.android.ui.debug.DebugPreferenceManager.getBleEnabled(true)
        } catch (_: Exception) {
            true
        }
        if (!bleRequired) {
            checkLocationAndProceed()
            return
        }
        when (mainViewModel.bluetoothStatus.value) {
            BluetoothStatus.ENABLED -> {
                checkLocationAndProceed()
            }
            BluetoothStatus.DISABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            BluetoothStatus.NOT_SUPPORTED -> {
                Log.e("MainActivity", "Bluetooth not supported")
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
        }
    }

    private fun proceedWithPermissionCheck() {
        lifecycleScope.launch {
            delay(200)

            if (permissionManager.isFirstTimeLaunch()) {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            } else if (permissionManager.getUnrequestedOptionalPermissions().isNotEmpty()) {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            } else if (permissionManager.areRequiredPermissionsGranted()) {
                if (permissionManager.needsBackgroundLocationPermission() &&
                    !permissionManager.isBackgroundLocationGranted() &&
                    !com.bitchat.android.onboarding.BackgroundLocationPreferenceManager.isSkipped(this@MainActivity)
                ) {
                    mainViewModel.updateOnboardingState(OnboardingState.BACKGROUND_LOCATION_EXPLANATION)
                } else {
                    mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                    initializeApp()
                }
            } else {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
        }
    }

    private fun handleBluetoothEnabled() {
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(BluetoothStatus.ENABLED)
        checkLocationAndProceed()
    }

    private fun checkLocationAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            proceedWithPermissionCheck()
            return
        }

        locationStatusManager.logLocationStatus()
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        when (mainViewModel.locationStatus.value) {
            LocationStatus.ENABLED -> {
                checkBatteryOptimizationAndProceed()
            }
            LocationStatus.DISABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            LocationStatus.NOT_AVAILABLE -> {
                Log.e("MainActivity", "Location services not available")
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    private fun handleLocationEnabled() {
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(LocationStatus.ENABLED)
        WifiAwareController.startIfPossible()
        checkBatteryOptimizationAndProceed()
    }

    private fun handleLocationDisabled(message: String) {
        Log.w("MainActivity", "Location services disabled or failed: $message")
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        when {
            mainViewModel.locationStatus.value == LocationStatus.NOT_AVAILABLE -> {
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
            }
        }
    }

    private fun handleBluetoothDisabled(message: String) {
        Log.w("MainActivity", "Bluetooth disabled or failed: $message")
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())

        when {
            mainViewModel.bluetoothStatus.value == BluetoothStatus.NOT_SUPPORTED -> {
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            message.contains("Permission") && permissionManager.isFirstTimeLaunch() -> {
                proceedWithPermissionCheck()
            }
            message.contains("Permission") -> {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
            }
        }
    }

    private fun handleOnboardingComplete() {
        val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
        val currentLocationStatus = locationStatusManager.checkLocationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }

        val bleRequired2 = try {
            com.bitchat.android.ui.debug.DebugPreferenceManager.getBleEnabled(true)
        } catch (_: Exception) {
            true
        }
        when {
            bleRequired2 && currentBluetoothStatus != BluetoothStatus.ENABLED -> {
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            currentLocationStatus != LocationStatus.ENABLED -> {
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            currentBatteryOptimizationStatus == BatteryOptimizationStatus.ENABLED -> {
                mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            }
        }
    }

    private fun handleOnboardingFailed(message: String) {
        Log.e("MainActivity", "Onboarding failed: $message")
        mainViewModel.updateErrorMessage(message)
        mainViewModel.updateOnboardingState(OnboardingState.ERROR)
    }

    private fun startMeshForegroundServiceBestEffort() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            pendingMeshForegroundServiceStart = true
            Log.i("MainActivity", "Deferring foreground mesh service start until activity is started")
            return
        }

        try {
            com.bitchat.android.service.MeshForegroundService.start(applicationContext)
            pendingMeshForegroundServiceStart = false
        } catch (e: Exception) {
            pendingMeshForegroundServiceStart = true
            Log.w("MainActivity", "Unable to start foreground mesh service; will retry when activity is started", e)
        }
    }

    private fun checkBatteryOptimizationAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            proceedWithPermissionCheck()
            return
        }

        if (BatteryOptimizationPreferenceManager.isSkipped(this)) {
            proceedWithPermissionCheck()
            return
        }

        batteryOptimizationManager.logBatteryOptimizationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)

        when (currentBatteryOptimizationStatus) {
            BatteryOptimizationStatus.DISABLED, BatteryOptimizationStatus.NOT_SUPPORTED -> {
                proceedWithPermissionCheck()
            }
            BatteryOptimizationStatus.ENABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
        }
    }

    private fun handleBatteryOptimizationDisabled() {
        mainViewModel.updateBatteryOptimizationLoading(false)
        mainViewModel.updateBatteryOptimizationStatus(BatteryOptimizationStatus.DISABLED)
        proceedWithPermissionCheck()
    }

    private fun handleBatteryOptimizationFailed(message: String) {
        Log.w("MainActivity", "Battery optimization disable failed: $message")
        mainViewModel.updateBatteryOptimizationLoading(false)
        val currentStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentStatus)
        mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
    }

    private fun initializeApp() {
        lifecycleScope.launch {
            try {
                delay(1000)
                PoWPreferenceManager.init(this@MainActivity)
                com.bitchat.android.nostr.LocationNotesInitializer.initialize(this@MainActivity)

                if (!permissionManager.areAllPermissionsGranted()) {
                    val missing = permissionManager.getMissingPermissions()
                    Log.w("MainActivity", "Permissions revoked during initialization: $missing")
                    handleOnboardingFailed("Some permissions were revoked. Please grant all permissions to continue.")
                    return@launch
                }

                unifiedMeshService.delegate = chatViewModel
                unifiedMeshService.startServices()
                startMeshForegroundServiceBestEffort()

                handleNotificationIntent(intent)
                handleVerificationIntent(intent)

                delay(500)
                Log.i("MainActivity", "App initialization complete")
                mainViewModel.updateOnboardingState(OnboardingState.COMPLETE)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize app", e)
                handleOnboardingFailed("Failed to initialize the app: ${e.message}")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.getBooleanExtra("ACTION_QUIT_APP", false)) {
            Log.d("MainActivity", "Quit request received, finishing activity")
            finish()
            return
        }

        com.bitchat.android.service.AppShutdownCoordinator.cancelPendingShutdown()

        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            handleNotificationIntent(intent)
            handleVerificationIntent(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        if (pendingMeshForegroundServiceStart) {
            startMeshForegroundServiceBestEffort()
        }
    }

    override fun onResume() {
        super.onResume()
        LocationChannelManager.getInstance(applicationContext).syncPermissionState()

        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            try {
                unifiedMeshService.delegate = chatViewModel
            } catch (_: Exception) {
            }

            val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
            val bleRequired = try {
                com.bitchat.android.ui.debug.DebugPreferenceManager.getBleEnabled(true)
            } catch (_: Exception) {
                true
            }
            if (bleRequired && currentBluetoothStatus != BluetoothStatus.ENABLED && !mainViewModel.isBluetoothCheckSkipped.value) {
                Log.w("MainActivity", "Bluetooth disabled while app was backgrounded")
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
                return
            }

            val currentLocationStatus = locationStatusManager.checkLocationStatus()
            if (currentLocationStatus != LocationStatus.ENABLED) {
                Log.w("MainActivity", "Location services disabled while app was backgrounded")
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            } else {
                WifiAwareController.startIfPossible()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            try {
                unifiedMeshService.delegate = null
            } catch (_: Exception) {
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val shouldOpenPrivateChat = intent.getBooleanExtra(
            com.bitchat.android.ui.NotificationManager.EXTRA_OPEN_PRIVATE_CHAT,
            false
        )

        val shouldOpenGeohashChat = intent.getBooleanExtra(
            com.bitchat.android.ui.NotificationManager.EXTRA_OPEN_GEOHASH_CHAT,
            false
        )

        when {
            shouldOpenPrivateChat -> {
                val peerID = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_PEER_ID)
                val senderNickname = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_SENDER_NICKNAME)

                if (peerID != null) {
                    Log.d("MainActivity", "Opening private chat with $senderNickname (peerID: $peerID) from notification")
                    chatViewModel.showMeshPeerList()
                    chatViewModel.showPrivateChatSheet(peerID)
                    chatViewModel.clearNotificationsForSender(peerID)
                }
            }

            shouldOpenGeohashChat -> {
                val geohash = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_GEOHASH)

                if (geohash != null) {
                    Log.d("MainActivity", "Opening geohash chat from notification")
                    val level = when (geohash.length) {
                        7 -> com.bitchat.android.geohash.GeohashChannelLevel.BLOCK
                        6 -> com.bitchat.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                        5 -> com.bitchat.android.geohash.GeohashChannelLevel.CITY
                        4 -> com.bitchat.android.geohash.GeohashChannelLevel.PROVINCE
                        2 -> com.bitchat.android.geohash.GeohashChannelLevel.REGION
                        else -> com.bitchat.android.geohash.GeohashChannelLevel.CITY
                    }
                    val geohashChannel = com.bitchat.android.geohash.GeohashChannel(level, geohash)
                    val channelId = com.bitchat.android.geohash.ChannelID.Location(geohashChannel)
                    chatViewModel.selectLocationChannel(channelId)
                    chatViewModel.setCurrentGeohash(geohash)
                    chatViewModel.clearNotificationsForGeohash(geohash)
                }
            }
        }
    }

    private fun handleVerificationIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "bitchat" || uri.host != "verify") return

        chatViewModel.showVerificationSheet()
        val qr = VerificationService.verifyScannedQR(uri.toString())
        if (qr != null) {
            chatViewModel.beginQRVerification(qr)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(forceFinishReceiver)
        } catch (_: Exception) {
        }

        try {
            locationStatusManager.cleanup()
        } catch (e: Exception) {
            Log.w("MainActivity", "Error cleaning up location status manager: ${e.message}")
        }
    }
}