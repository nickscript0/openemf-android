package com.openemf.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openemf.data.repository.RecordingSessionRepository
import com.openemf.recording.RecordingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val controller: RecordingController,
    journal: RecordingSessionRepository
) : ViewModel() {
    val state = controller.state
    private val mutableJournalError = MutableStateFlow<String?>(null)
    val journalError = mutableJournalError.asStateFlow()
    val recentSessions = journal.recent.catch {
        mutableJournalError.value = "Could not read recording sessions: ${it.message ?: "storage unavailable"}"
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun stop() = controller.stop()
}
