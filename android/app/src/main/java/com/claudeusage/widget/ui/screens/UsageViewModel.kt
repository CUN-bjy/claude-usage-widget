package com.claudeusage.widget.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.model.Credentials
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.repository.AuthException
import com.claudeusage.widget.data.repository.UsageRepository
import com.claudeusage.widget.service.UsageNotificationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class UiState {
    data object Loading : UiState()
    data object LoginRequired : UiState()
    data class Success(val data: UsageData) : UiState()
    data class Error(val message: String, val isAuthError: Boolean = false) : UiState()
}

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialManager = CredentialManager(application)
    private val appPreferences = AppPreferences(application)
    private val repository = UsageRepository()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastUpdated = MutableStateFlow<String?>(null)
    val lastUpdated: StateFlow<String?> = _lastUpdated.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        checkCredentialsAndLoad()
    }

    fun checkCredentialsAndLoad() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            if (credentialManager.hasCredentials()) {
                fetchUsageData()
            } else {
                _uiState.value = UiState.LoginRequired
            }
        }
    }

    fun onLoginComplete(sessionKey: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val orgResult = repository.fetchOrganizationId(sessionKey)
            orgResult.fold(
                onSuccess = { orgId ->
                    val credentials = Credentials(sessionKey, orgId)
                    credentialManager.saveCredentials(credentials)
                    fetchUsageData()
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(
                        message = error.message ?: "Failed to get organization info.",
                        isAuthError = error is AuthException
                    )
                }
            )
        }
    }

    fun onManualLogin(sessionKey: String) {
        onLoginComplete(sessionKey)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchUsageData()
            _isRefreshing.value = false
        }
    }

    fun logout() {
        autoRefreshJob?.cancel()
        credentialManager.clearCredentials()
        _uiState.value = UiState.LoginRequired
        _lastUpdated.value = null
        UsageNotificationService.stop(getApplication())
    }

    private suspend fun fetchUsageData() {
        val credentials = credentialManager.getCredentials()
        if (credentials == null) {
            _uiState.value = UiState.LoginRequired
            return
        }

        val result = repository.fetchUsageData(credentials)
        result.fold(
            onSuccess = { data ->
                _uiState.value = UiState.Success(data)
                _lastUpdated.value = formatLastUpdated()
                startAutoRefresh()
                if (appPreferences.notificationEnabled) {
                    UsageNotificationService.start(getApplication())
                }
            },
            onFailure = { error ->
                val isAuth = error is AuthException
                if (isAuth) {
                    credentialManager.clearCredentials()
                }
                // Only show error if we don't already have data
                val currentState = _uiState.value
                if (currentState is UiState.Success) {
                    // Keep existing data, just update timestamp note
                    _lastUpdated.value = "Update failed - ${formatLastUpdated()}"
                } else {
                    _uiState.value = UiState.Error(
                        message = error.message ?: "Failed to fetch usage data.",
                        isAuthError = isAuth
                    )
                }
            }
        )
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)
                fetchUsageData()
            }
        }
    }

    private fun formatLastUpdated(): String {
        val now = java.time.LocalTime.now()
        return String.format("%02d:%02d", now.hour, now.minute)
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }

    companion object {
        const val UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }
}
