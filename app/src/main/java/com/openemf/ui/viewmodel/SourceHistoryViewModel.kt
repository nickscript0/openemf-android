package com.openemf.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openemf.data.database.ObservationRecord
import com.openemf.data.repository.MeasurementRepository
import com.openemf.sensors.api.SourceType
import com.openemf.ui.model.HistoryRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject

data class SourceTimelineState(
    val records: List<ObservationRecord> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)
data class SourceChartState(
    val range: HistoryRange = HistoryRange.DAY,
    val records: List<ObservationRecord> = emptyList(),
    val since: Instant = Instant.now().minusSeconds(86400),
    val until: Instant = Instant.now(),
    val truncated: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SourceHistoryViewModel @Inject constructor(
    private val repository: MeasurementRepository,
    private val saved: SavedStateHandle
) : ViewModel() {
    val type = SourceType.valueOf(checkNotNull(saved.get<String>("type")))
    val sourceId = checkNotNull(saved.get<String>("sourceId"))
    private val range = saved.getStateFlow("chartRange", HistoryRange.DAY.name)
    private val refresh = MutableStateFlow(0)
    private val mutableTimeline = MutableStateFlow(SourceTimelineState())
    val timeline = mutableTimeline.asStateFlow()
    private var pageJob: Job? = null

    private val minute = flow {
        while (currentCoroutineContext().isActive) { emit(Instant.now()); delay(60_000) }
    }
    val chart = combine(range, minute, refresh) { name, now, _ -> HistoryRange.valueOf(name) to now }
        .flatMapLatest { (selected, now) ->
            val initial = SourceChartState(range = selected, since = now.minusSeconds(selected.seconds), until = now)
            repository.observeSourceChart(type, sourceId, initial.since)
                .map { rows ->
                    val current = Instant.now()
                    initial.copy(records = rows.take(2000), truncated = rows.size > 2000, loading = false,
                        since = current.minusSeconds(selected.seconds), until = current)
                }
                .onStart { emit(initial) }
                .catch { emit(initial.copy(loading = false, error = it.message ?: "Could not read chart history")) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SourceChartState())

    init { loadMore() }
    fun selectRange(value: HistoryRange) { saved["chartRange"] = value.name }
    fun refresh() {
        pageJob?.cancel()
        pageJob = null
        mutableTimeline.value = SourceTimelineState()
        refresh.value++
        loadMore()
    }
    fun loadMore() {
        if (pageJob?.isActive == true || !timeline.value.hasMore) return
        pageJob = viewModelScope.launch {
            mutableTimeline.update { it.copy(loading = true, error = null) }
            try {
                val page = repository.getSourceHistory(type, sourceId,
                    beforeRowId = timeline.value.records.lastOrNull()?.observation?.rowId)
                mutableTimeline.update { it.copy(records = it.records + page, hasMore = page.size == 100) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutableTimeline.update { it.copy(error = e.message ?: "Could not load signal history") }
            } finally { if (currentCoroutineContext().isActive) mutableTimeline.update { it.copy(loading = false) } }
        }
    }
}
