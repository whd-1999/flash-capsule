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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val repo: CaptureRepository,
    private val settings: Settings,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val search: StateFlow<String> = query

    private val _lang = MutableStateFlow(settings.sttLanguage)
    val lang: StateFlow<String> = _lang

    val capsules: StateFlow<List<Capsule>> =
        query.flatMapLatest { q ->
            if (q.isBlank()) repo.observeAll() else repo.search(q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { query.value = q }

    fun setLang(code: String) {
        settings.sttLanguage = code
        _lang.value = code
    }

    /** 语音直存（长按说话 / 磁贴 / 助理都走它）。 */
    fun captureVoice(text: String) = viewModelScope.launch {
        repo.ingest(RawCapture(text = text, source = "voice"))
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun setColor(id: String, c: ColorTag?) = viewModelScope.launch { repo.setColor(id, c) }
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
