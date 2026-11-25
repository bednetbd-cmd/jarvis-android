package com.jarvis.assistant.domain.model

enum class AiProviderType(
    val displayName: String,
    val badgeLabel: String,
    val requiresNetwork: Boolean
) {
    ON_DEVICE("On-Device Engine", "ON-DEVICE", false),
    LOCAL_OLLAMA("Local Ollama (LAN)", "OLLAMA", true),
    OPENAI_COMPATIBLE("Free OpenAI-Compatible", "CUSTOM AI", true),
    GEMINI_REST("Gemini Free-Tier REST", "GEMINI", true)
}

enum class AssistantPersonality(
    val displayName: String,
    val systemPromptDirective: String
) {
    FORMAL_JARVIS(
        displayName = "Formal JARVIS",
        systemPromptDirective = "You are JARVIS, a calm, precise, British-mannered AI personal assistant on Android. Be concise, accurate, and helpful."
    ),
    TACTICAL_CONCISE(
        displayName = "Tactical Concise",
        systemPromptDirective = "You are JARVIS in tactical mode. Give ultra-brief, direct, bulleted answers with zero filler."
    ),
    FRIENDLY_MENTOR(
        displayName = "Friendly Mentor",
        systemPromptDirective = "You are JARVIS in mentor mode. Explain concepts clearly step-by-step in warm plain language."
    )
}

data class AiProviderConfig(
    val activeProvider: AiProviderType = AiProviderType.ON_DEVICE,
    val personality: AssistantPersonality = AssistantPersonality.FORMAL_JARVIS,
    val ollamaBaseUrl: String = "http://127.0.0.1:11434",
    val ollamaModel: String = "llama3.2",
    val openAiBaseUrl: String = "https://api.groq.com/openai/v1",
    val openAiModel: String = "llama-3.1-8b-instant",
    val geminiModel: String = "gemini-2.5-flash",
    val hasOpenAiToken: Boolean = false,
    val hasGeminiToken: Boolean = false
)
