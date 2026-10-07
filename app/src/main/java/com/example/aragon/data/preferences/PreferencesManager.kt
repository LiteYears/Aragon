package com.example.aragon.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.domain.model.AutonomyLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aragon_secure_prefs", Context.MODE_PRIVATE)

    private val _nvidiaApiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "") ?: "")
    val nvidiaApiKey: StateFlow<String> = _nvidiaApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        prefs.getString(KEY_SELECTED_MODEL, "meta/llama-3.3-70b-instruct") ?: "meta/llama-3.3-70b-instruct"
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _defaultMode = MutableStateFlow(
        runCatching { AgentMode.valueOf(prefs.getString(KEY_DEFAULT_MODE, AgentMode.AGENT.name)!!) }
            .getOrDefault(AgentMode.AGENT)
    )
    val defaultMode: StateFlow<AgentMode> = _defaultMode.asStateFlow()

    private val _autonomyLevel = MutableStateFlow(
        runCatching { AutonomyLevel.valueOf(prefs.getString(KEY_AUTONOMY, AutonomyLevel.FULL.name)!!) }
            .getOrDefault(AutonomyLevel.FULL)
    )
    val autonomyLevel: StateFlow<AutonomyLevel> = _autonomyLevel.asStateFlow()

    private val _maxIterations = MutableStateFlow(prefs.getInt(KEY_MAX_ITERATIONS, 25))
    val maxIterations: StateFlow<Int> = _maxIterations.asStateFlow()

    private val _temperature = MutableStateFlow(prefs.getFloat(KEY_TEMPERATURE, 0.2f))
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    fun setNvidiaApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_API_KEY, trimmed).apply()
        _nvidiaApiKey.value = trimmed
    }

    fun setSelectedModel(modelId: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
        _selectedModel.value = modelId
    }

    fun setDefaultMode(mode: AgentMode) {
        prefs.edit().putString(KEY_DEFAULT_MODE, mode.name).apply()
        _defaultMode.value = mode
    }

    fun setAutonomyLevel(level: AutonomyLevel) {
        prefs.edit().putString(KEY_AUTONOMY, level.name).apply()
        _autonomyLevel.value = level
    }

    fun setMaxIterations(iterations: Int) {
        prefs.edit().putInt(KEY_MAX_ITERATIONS, iterations).apply()
        _maxIterations.value = iterations
    }

    fun setTemperature(temp: Float) {
        prefs.edit().putFloat(KEY_TEMPERATURE, temp).apply()
        _temperature.value = temp
    }

    companion object {
        private const val KEY_API_KEY = "nvidia_nim_api_key"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_DEFAULT_MODE = "default_mode"
        private const val KEY_AUTONOMY = "autonomy_level"
        private const val KEY_MAX_ITERATIONS = "max_iterations"
        private const val KEY_TEMPERATURE = "temperature"
    }
}
