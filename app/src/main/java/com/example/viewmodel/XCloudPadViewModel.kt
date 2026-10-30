package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.FrameLayout
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cast.ExternalDisplayController
import com.example.data.XCloudPadRepository
import com.example.model.ControlElementId
import com.example.model.ControlTransform
import com.example.model.ControllerProfile
import com.example.model.ExternalDisplayInfo
import com.example.model.GamepadState
import com.example.model.GyroMode
import com.example.model.StreamEngineSettings
import com.example.model.StreamTelemetry
import com.example.model.TvAspectRatioMode
import com.example.model.XboxButton
import com.example.webview.XCloudWebViewManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class XCloudPadViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    val repository = XCloudPadRepository(application)

    private val _gamepadState = MutableStateFlow(GamepadState())
    val gamepadState: StateFlow<GamepadState> = _gamepadState.asStateFlow()

    private val _telemetry = MutableStateFlow(StreamTelemetry())
    val telemetry: StateFlow<StreamTelemetry> = _telemetry.asStateFlow()

    private val _availableDisplays = MutableStateFlow<List<ExternalDisplayInfo>>(emptyList())
    val availableDisplays: StateFlow<List<ExternalDisplayInfo>> = _availableDisplays.asStateFlow()

    private val _activeCastDisplay = MutableStateFlow<ExternalDisplayInfo?>(null)
    val activeCastDisplay: StateFlow<ExternalDisplayInfo?> = _activeCastDisplay.asStateFlow()

    private val _engineLogs = MutableStateFlow<List<String>>(listOf("xCloud Pad Engine initialized"))
    val engineLogs: StateFlow<List<String>> = _engineLogs.asStateFlow()

    private val _currentUrl = MutableStateFlow(XCloudWebViewManager.XCLOUD_PLAY_URL)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _showControllerOverlay = MutableStateFlow(true)
    val showControllerOverlay: StateFlow<Boolean> = _showControllerOverlay.asStateFlow()

    private val _showDebugPanel = MutableStateFlow(false)
    val showDebugPanel: StateFlow<Boolean> = _showDebugPanel.asStateFlow()

    private val _isSideMenuOpen = MutableStateFlow(false)
    val isSideMenuOpen: StateFlow<Boolean> = _isSideMenuOpen.asStateFlow()

    private val _isLayoutEditorMode = MutableStateFlow(false)
    val isLayoutEditorMode: StateFlow<Boolean> = _isLayoutEditorMode.asStateFlow()

    private val _selectedControlElement = MutableStateFlow(ControlElementId.ABXY_CLUSTER)
    val selectedControlElement: StateFlow<ControlElementId> = _selectedControlElement.asStateFlow()

    val streamSettings: StateFlow<StreamEngineSettings> = repository.streamSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StreamEngineSettings()
    )

    val profiles: StateFlow<List<ControllerProfile>> = repository.profilesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.defaultProfiles()
    )

    private val activeProfileId: StateFlow<String> = repository.activeProfileIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "default"
    )

    val activeProfile: StateFlow<ControllerProfile> = combine(profiles, activeProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull() ?: ControllerProfile()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ControllerProfile()
    )

    private var touchRx: Float = 0f
    private var touchRy: Float = 0f
    private var gyroRx: Float = 0f
    private var gyroRy: Float = 0f

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var gyroRegistered = false

    lateinit var externalDisplayController: ExternalDisplayController

    val webViewManager: XCloudWebViewManager = XCloudWebViewManager(
        appContext = application,
        repository = repository,
        scope = viewModelScope,
        onTelemetryUpdate = { t ->
            _telemetry.value = t
            if (t.width > 0 && t.height > 0 && _activeCastDisplay.value != null && ::externalDisplayController.isInitialized) {
                externalDisplayController.updateCastAspectOrResolution(
                    streamSettings.value.tvAspectRatioMode,
                    t.width,
                    t.height
                )
            }
        },
        onStreamStateUpdate = { isStreaming, gameTitle ->
            _telemetry.value = _telemetry.value.copy(isStreaming = isStreaming, gameTitle = gameTitle)
            if (isStreaming && gameTitle.isNotBlank()) {
                autoMatchProfileForGame(gameTitle)
            }
        },
        onEngineLog = { msg ->
            _engineLogs.value = (_engineLogs.value + msg).takeLast(20)
        },
        onPageUrlChanged = { url ->
            _currentUrl.value = url
        }
    )

    init {
        externalDisplayController = ExternalDisplayController(
            context = application,
            webViewManager = webViewManager,
            onDisplaysChanged = { list, activeCast ->
                _availableDisplays.value = list
                _activeCastDisplay.value = activeCast
            }
        )
        externalDisplayController.register()
        viewModelScope.launch {
            streamSettings.collect { settings ->
                webViewManager.applyStreamEngineSettings(settings)
                if (::externalDisplayController.isInitialized) {
                    externalDisplayController.updateCastAspectOrResolution(
                        settings.tvAspectRatioMode,
                        _telemetry.value.width,
                        _telemetry.value.height
                    )
                }
            }
        }
        viewModelScope.launch {
            activeProfile.collect { prof ->
                updateGyroListenerRegistration(prof.gyroMode)
            }
        }
    }

    private fun updateGyroListenerRegistration(mode: GyroMode) {
        if (mode != GyroMode.OFF && gyroSensor != null && !gyroRegistered) {
            sensorManager?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            gyroRegistered = true
        } else if (mode == GyroMode.OFF && gyroRegistered) {
            sensorManager?.unregisterListener(this)
            gyroRegistered = false
            gyroRx = 0f
            gyroRy = 0f
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_GYROSCOPE) return
        val prof = activeProfile.value
        val active = when (prof.gyroMode) {
            GyroMode.OFF -> false
            GyroMode.ALWAYS_ON -> true
            GyroMode.WHILE_LT -> _gamepadState.value.isPressed(XboxButton.LT)
        }
        if (!active) {
            if (gyroRx != 0f || gyroRy != 0f) {
                gyroRx = 0f
                gyroRy = 0f
                publishRightStickCombined()
            }
            return
        }
        val sens = prof.gyroSensitivity * 0.18f
        gyroRx = (-event.values[1] * sens).coerceIn(-0.65f, 0.65f)
        gyroRy = (-event.values[0] * sens).coerceIn(-0.65f, 0.65f)
        publishRightStickCombined()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publishRightStickCombined() {
        val combinedRx = (touchRx + gyroRx).coerceIn(-1f, 1f)
        val combinedRy = (touchRy + gyroRy).coerceIn(-1f, 1f)
        val next = _gamepadState.value.copy(rx = combinedRx, ry = combinedRy)
        if (next != _gamepadState.value) {
            _gamepadState.value = next
            webViewManager.sendGamepadStateImmediate(next)
        }
    }

    fun updateButtonState(buttonIndex: Int, pressed: Boolean) {
        val next = _gamepadState.value.withButton(buttonIndex, pressed)
        if (next != _gamepadState.value) {
            _gamepadState.value = next
            webViewManager.sendGamepadStateImmediate(next)
        }
    }

    fun updateMultiButtons(pressedSet: Set<Int>, releasedSet: Set<Int>) {
        var mask = _gamepadState.value.buttonsMask
        for (b in releasedSet) mask = mask and (1 shl b).inv()
        for (b in pressedSet) mask = mask or (1 shl b)
        val next = _gamepadState.value.copy(buttonsMask = mask)
        if (next != _gamepadState.value) {
            _gamepadState.value = next
            webViewManager.sendGamepadStateImmediate(next)
        }
    }

    fun updateTriggerState(isLeft: Boolean, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        val cur = _gamepadState.value
        val next = if (isLeft) {
            val mask = if (clamped > 0.12f) cur.buttonsMask or (1 shl XboxButton.LT) else cur.buttonsMask and (1 shl XboxButton.LT).inv()
            cur.copy(lt = clamped, buttonsMask = mask)
        } else {
            val mask = if (clamped > 0.12f) cur.buttonsMask or (1 shl XboxButton.RT) else cur.buttonsMask and (1 shl XboxButton.RT).inv()
            cur.copy(rt = clamped, buttonsMask = mask)
        }
        if (next != cur) {
            _gamepadState.value = next
            webViewManager.sendGamepadStateImmediate(next)
        }
    }

    fun updateStickState(isLeft: Boolean, x: Float, y: Float) {
        val cur = _gamepadState.value
        if (isLeft) {
            val next = cur.copy(lx = x.coerceIn(-1f, 1f), ly = y.coerceIn(-1f, 1f))
            if (next != cur) {
                _gamepadState.value = next
                webViewManager.sendGamepadStateImmediate(next)
            }
        } else {
            touchRx = x.coerceIn(-1f, 1f)
            touchRy = y.coerceIn(-1f, 1f)
            publishRightStickCombined()
        }
    }

    fun bindPhoneWebViewContainer(container: FrameLayout) {
        externalDisplayController.bindPhoneContainer(container)
    }

    fun startCastingToTv(displayId: Int, aspectMode: TvAspectRatioMode) {
        val t = _telemetry.value
        externalDisplayController.startCasting(displayId, aspectMode, t.width, t.height)
    }

    fun stopCastingToTv() {
        externalDisplayController.stopCasting()
    }

    fun refreshExternalDisplays() {
        externalDisplayController.refreshDisplays()
    }

    fun updateStreamEngineSettings(updated: StreamEngineSettings) {
        viewModelScope.launch { repository.saveStreamSettings(updated) }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch { repository.saveProfiles(profiles.value, profileId) }
    }

    fun updateActiveProfile(updated: ControllerProfile) {
        val newList = profiles.value.map { if (it.id == updated.id) updated else it }
        viewModelScope.launch { repository.saveProfiles(newList, updated.id) }
    }

    fun updateControlTransform(isPortrait: Boolean, elementId: ControlElementId, transform: ControlTransform) {
        val cur = activeProfile.value
        val updated = if (isPortrait) {
            val map = cur.portraitLayout.toMutableMap().apply { put(elementId, transform) }
            cur.copy(portraitLayout = map)
        } else {
            val map = cur.landscapeLayout.toMutableMap().apply { put(elementId, transform) }
            cur.copy(landscapeLayout = map)
        }
        updateActiveProfile(updated)
    }

    fun resetActiveLayout(isPortrait: Boolean) {
        val cur = activeProfile.value
        val defaults = ControllerProfile.defaultControlMap()
        val updated = if (isPortrait) cur.copy(portraitLayout = defaults) else cur.copy(landscapeLayout = defaults)
        updateActiveProfile(updated)
    }

    fun createGameProfile(name: String, gameTitle: String) {
        val id = "custom_${System.currentTimeMillis()}"
        val copy = activeProfile.value.copy(id = id, name = name, gameTitleMatch = gameTitle.lowercase())
        val newList = profiles.value + copy
        viewModelScope.launch { repository.saveProfiles(newList, id) }
    }

    private fun autoMatchProfileForGame(gameTitle: String) {
        val lower = gameTitle.lowercase()
        val matched = profiles.value.firstOrNull { prof ->
            prof.gameTitleMatch.isNotBlank() && prof.gameTitleMatch.split(",").any { token ->
                token.trim().isNotEmpty() && lower.contains(token.trim())
            }
        }
        if (matched != null && matched.id != activeProfile.value.id) selectProfile(matched.id)
    }

    fun setSideMenuOpen(open: Boolean) { _isSideMenuOpen.value = open }
    fun setControllerOverlayVisible(visible: Boolean) { _showControllerOverlay.value = visible }
    fun setDebugPanelVisible(visible: Boolean) { _showDebugPanel.value = visible }
    fun setLayoutEditorMode(enabled: Boolean) {
        _isLayoutEditorMode.value = enabled
        if (enabled) _showControllerOverlay.value = true
    }
    fun selectControlElement(elementId: ControlElementId) { _selectedControlElement.value = elementId }

    override fun onCleared() {
        super.onCleared()
        if (gyroRegistered) sensorManager?.unregisterListener(this)
        externalDisplayController.unregister()
        webViewManager.flushCookies()
    }
}
