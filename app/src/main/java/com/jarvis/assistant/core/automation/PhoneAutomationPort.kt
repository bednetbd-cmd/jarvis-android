package com.jarvis.assistant.core.automation

/**
 * Contract for future Android Intent & system automation features
 * (e.g., alarms, timers, app launching, device settings shortcuts).
 * Currently unimplemented in the foundation phase.
 */
interface PhoneAutomationPort {
    suspend fun executeCommand(actionName: String, params: Map<String, String> = emptyMap()): Result<Unit>
}

class UnimplementedPhoneAutomation : PhoneAutomationPort {
    override suspend fun executeCommand(
        actionName: String,
        params: Map<String, String>
    ): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("Phone automation is not implemented yet.")
        )
    }
}
