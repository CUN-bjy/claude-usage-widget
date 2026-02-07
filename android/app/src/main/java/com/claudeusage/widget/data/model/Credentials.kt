package com.claudeusage.widget.data.model

data class Credentials(
    val sessionKey: String,
    val organizationId: String
) {
    val isValid: Boolean
        get() = sessionKey.isNotBlank() && organizationId.isNotBlank()
}
