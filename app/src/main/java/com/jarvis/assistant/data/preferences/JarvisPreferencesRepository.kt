package com.jarvis.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.domain.model.AiProviderConfig
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.AssistantPersonality
import com.jarvis.assistant.domain.model.JarvisAccentTheme
import com.jarvis.assistant.domain.model.JarvisPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_preferences"
)

interface JarvisPreferencesPort {
    val preferences: Flow<JarvisPreferences>
    suspend fun setTtsEnabled(enabled: Boolean)
    suspend fun setSpeechRate(rate: Float)
    suspend fun setSpeechPitch(pitch: Float)
    suspend fun setSpeechLanguageTag(languageTag: String)
    suspend fun setPreferOfflineVoice(enabled: Boolean)
    suspend fun setAccentTheme(theme: JarvisAccentTheme)
    suspend fun setShowWaveform(enabled: Boolean)
    suspend fun setBiometricLockEnabled(enabled: Boolean)
    suspend fun setConfirmSensitiveActions(enabled: Boolean)
    suspend fun setEncryptConversationHistory(enabled: Boolean)
    suspend fun setActiveAiProvider(provider: AiProviderType)
    suspend fun setAssistantPersonality(personality: AssistantPersonality)
    suspend fun setOllamaConfig(baseUrl: String, model: String)
    suspend fun setOpenAiConfig(baseUrl: String, model: String)
    suspend fun setGeminiModel(model: String)
}

class DataStoreJarvisPreferencesRepository(
    context: Context
) : JarvisPreferencesPort {

    private val dataStore = context.applicationContext.jarvisDataStore

    override val preferences: Flow<JarvisPreferences> = dataStore.data.map { prefs ->
        val themeName = prefs[KEY_ACCENT_THEME] ?: JarvisAccentTheme.ARC_CYAN.name
        val theme = runCatching { JarvisAccentTheme.valueOf(themeName) }
            .getOrDefault(JarvisAccentTheme.ARC_CYAN)

        val providerName = prefs[KEY_AI_PROVIDER] ?: AiProviderType.ON_DEVICE.name
        val provider = runCatching { AiProviderType.valueOf(providerName) }
            .getOrDefault(AiProviderType.ON_DEVICE)

        val personalityName = prefs[KEY_AI_PERSONALITY] ?: AssistantPersonality.FORMAL_JARVIS.name
        val personality = runCatching { AssistantPersonality.valueOf(personalityName) }
            .getOrDefault(AssistantPersonality.FORMAL_JARVIS)

        val defaults = AiProviderConfig()

        JarvisPreferences(
            ttsEnabled = prefs[KEY_TTS_ENABLED] ?: true,
            speechRate = JarvisPreferences.clampSpeechRate(prefs[KEY_SPEECH_RATE] ?: 1.02f),
            speechPitch = JarvisPreferences.clampSpeechPitch(prefs[KEY_SPEECH_PITCH] ?: 1.0f),
            speechLanguageTag = prefs[KEY_SPEECH_LANGUAGE] ?: "en-US",
            preferOfflineVoice = prefs[KEY_PREFER_OFFLINE_VOICE] ?: false,
            accentTheme = theme,
            showWaveform = prefs[KEY_SHOW_WAVEFORM] ?: true,
            biometricLockEnabled = prefs[KEY_BIOMETRIC_LOCK] ?: false,
            confirmSensitiveActions = prefs[KEY_CONFIRM_SENSITIVE] ?: true,
            encryptConversationHistory = prefs[KEY_ENCRYPT_HISTORY] ?: true,
            aiProviderConfig = AiProviderConfig(
                activeProvider = provider,
                personality = personality,
                ollamaBaseUrl = prefs[KEY_OLLAMA_BASE_URL]?.takeIf { it.isNotBlank() }
                    ?: defaults.ollamaBaseUrl,
                ollamaModel = prefs[KEY_OLLAMA_MODEL]?.takeIf { it.isNotBlank() }
                    ?: defaults.ollamaModel,
                openAiBaseUrl = prefs[KEY_OPENAI_BASE_URL]?.takeIf { it.isNotBlank() }
                    ?: defaults.openAiBaseUrl,
                openAiModel = prefs[KEY_OPENAI_MODEL]?.takeIf { it.isNotBlank() }
                    ?: defaults.openAiModel,
                geminiModel = prefs[KEY_GEMINI_MODEL]?.takeIf { it.isNotBlank() }
                    ?: defaults.geminiModel
            )
        )
    }

    override suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TTS_ENABLED] = enabled }
    }

    override suspend fun setSpeechRate(rate: Float) {
        dataStore.edit { it[KEY_SPEECH_RATE] = JarvisPreferences.clampSpeechRate(rate) }
    }

    override suspend fun setSpeechPitch(pitch: Float) {
        dataStore.edit { it[KEY_SPEECH_PITCH] = JarvisPreferences.clampSpeechPitch(pitch) }
    }

    override suspend fun setSpeechLanguageTag(languageTag: String) {
        dataStore.edit { it[KEY_SPEECH_LANGUAGE] = languageTag }
    }

    override suspend fun setPreferOfflineVoice(enabled: Boolean) {
        dataStore.edit { it[KEY_PREFER_OFFLINE_VOICE] = enabled }
    }

    override suspend fun setAccentTheme(theme: JarvisAccentTheme) {
        dataStore.edit { it[KEY_ACCENT_THEME] = theme.name }
    }

    override suspend fun setShowWaveform(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_WAVEFORM] = enabled }
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }

    override suspend fun setConfirmSensitiveActions(enabled: Boolean) {
        dataStore.edit { it[KEY_CONFIRM_SENSITIVE] = enabled }
    }

    override suspend fun setEncryptConversationHistory(enabled: Boolean) {
        dataStore.edit { it[KEY_ENCRYPT_HISTORY] = enabled }
    }

    override suspend fun setActiveAiProvider(provider: AiProviderType) {
        dataStore.edit { it[KEY_AI_PROVIDER] = provider.name }
    }

    override suspend fun setAssistantPersonality(personality: AssistantPersonality) {
        dataStore.edit { it[KEY_AI_PERSONALITY] = personality.name }
    }

    override suspend fun setOllamaConfig(baseUrl: String, model: String) {
        dataStore.edit {
            it[KEY_OLLAMA_BASE_URL] = baseUrl.trim()
            it[KEY_OLLAMA_MODEL] = model.trim()
        }
    }

    override suspend fun setOpenAiConfig(baseUrl: String, model: String) {
        dataStore.edit {
            it[KEY_OPENAI_BASE_URL] = baseUrl.trim()
            it[KEY_OPENAI_MODEL] = model.trim()
        }
    }

    override suspend fun setGeminiModel(model: String) {
        dataStore.edit { it[KEY_GEMINI_MODEL] = model.trim() }
    }

    private companion object {
        val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val KEY_SPEECH_LANGUAGE = stringPreferencesKey("speech_language_tag")
        val KEY_PREFER_OFFLINE_VOICE = booleanPreferencesKey("prefer_offline_voice")
        val KEY_ACCENT_THEME = stringPreferencesKey("accent_theme")
        val KEY_SHOW_WAVEFORM = booleanPreferencesKey("show_waveform")
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val KEY_CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive_actions")
        val KEY_ENCRYPT_HISTORY = booleanPreferencesKey("encrypt_conversation_history")
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_AI_PERSONALITY = stringPreferencesKey("ai_personality")
        val KEY_OLLAMA_BASE_URL = stringPreferencesKey("ollama_base_url")
        val KEY_OLLAMA_MODEL = stringPreferencesKey("ollama_model")
        val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        val KEY_OPENAI_MODEL = stringPreferencesKey("openai_model")
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
    }
}
