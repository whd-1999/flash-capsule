package com.flashcapsule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flashcapsule.data.CaptureRepository
import com.flashcapsule.data.Settings
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.ColorTag
import com.flashcapsule.model.RawCapture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 列表筛选：全部 / 待办 / 已完成。 */
enum class InboxFilter(val label: String) { ALL("全部"), TODO("待办"), DONE("已完成") }

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val repo: CaptureRepository,
    private val settings: Settings,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val search: StateFlow<String> = query

    private val filter = MutableStateFlow(InboxFilter.ALL)
    val filterState: StateFlow<InboxFilter> = filter

    private val _lang = MutableStateFlow(settings.sttLanguage)
    val lang: StateFlow<String> = _lang

    private val _apiKey = MutableStateFlow(settings.apiKey)
    val apiKey: StateFlow<String> = _apiKey

    private val _aiError = MutableStateFlow(settings.aiError)
    val aiError: StateFlow<String> = _aiError

    private val _handleLeft = MutableStateFlow(settings.handleLeft)
    val handleLeft: StateFlow<Boolean> = _handleLeft

    val capsules: StateFlow<List<Capsule>> =
        combine(query, filter) { q, f -> q to f }
            .flatMapLatest { (q, f) ->
                val base = if (q.isBlank()) repo.observeAll() else repo.search(q)
                when (f) {
                    InboxFilter.ALL -> base
                    InboxFilter.TODO -> base.map { list -> list.filter { it.doneAt == null } }
                    InboxFilter.DONE -> base.map { list ->
                        list.filter { it.doneAt != null }.sortedByDescending { it.doneAt ?: 0L }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trash: StateFlow<List<Capsule>> =
        repo.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: InboxFilter) { filter.value = f }

    fun setLang(code: String) {
        settings.sttLanguage = code
        _lang.value = code
    }

    fun setApiKey(k: String) {
        settings.apiKey = k.trim()
        _apiKey.value = settings.apiKey
    }

    fun toggleHandleSide() {
        settings.handleLeft = !settings.handleLeft
        _handleLeft.value = settings.handleLeft
    }

    /** 语音直存（长按说话 / 磁贴 / 助理都走它）。 */
    fun captureVoice(text: String) = viewModelScope.launch {
        repo.ingest(RawCapture(text = text, source = "voice"))
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun restore(id: String) = viewModelScope.launch { repo.restore(id) }
    fun permanentDelete(id: String) = viewModelScope.launch { repo.permanentDelete(id) }
    fun toggleDone(id: String) = viewModelScope.launch { repo.toggleDone(id) }
    fun setColor(id: String, c: ColorTag?) = viewModelScope.launch { repo.setColor(id, c) }
    fun updateText(id: String, text: String) = viewModelScope.launch { repo.updateText(id, text) }
    fun updateTitle(id: String, title: String) = viewModelScope.launch { repo.updateTitle(id, title) }
    fun enrich(id: String) = viewModelScope.launch { repo.enrich(id, force = true) }
    fun togglePin(id: String) = viewModelScope.launch { repo.togglePin(id) }
    fun setReminder(id: String, time: Long?) = viewModelScope.launch { repo.setReminder(id, time) }
    fun attachmentsFor(id: String) = repo.attachmentsFor(id)
    fun addAttachment(id: String, uri: android.net.Uri, mime: String?, sizeBytes: Long) =
        viewModelScope.launch { repo.addAttachment(id, uri, mime, sizeBytes) }
    fun deleteAttachment(id: String) = viewModelScope.launch { repo.deleteAttachment(id) }
    fun export(sinkId: String, id: String) = viewModelScope.launch { repo.exportTo(sinkId, id) }

    class Factory(
        private val repo: CaptureRepository,
        private val settings: Settings,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return InboxViewModel(repo, settings) as T
        }
    }
}
