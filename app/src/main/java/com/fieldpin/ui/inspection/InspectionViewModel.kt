package com.fieldpin.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldpin.data.repo.InspectionRepository
import com.fieldpin.domain.model.Condition
import com.fieldpin.domain.model.Inspection
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class InspectionViewModel(
    private val id: String,
    private val repo: InspectionRepository,
) : ViewModel() {
    val inspection: StateFlow<Inspection?> = repo.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Typed text lives here for instant UI; it is saved to the draft shortly after typing stops. */
    val notes = MutableStateFlow("")

    init {
        viewModelScope.launch {
            notes.value = repo.get(id)?.notes.orEmpty()
            notes.debounce(400).distinctUntilChanged().collect { repo.setNotes(id, it) }
        }
    }

    fun setCondition(condition: Condition) {
        viewModelScope.launch { repo.setCondition(id, condition) }
    }

    fun setFlagNeedsRepair(flag: Boolean) {
        viewModelScope.launch { repo.setFlagNeedsRepair(id, flag) }
    }

    fun removePhoto(path: String) {
        viewModelScope.launch { repo.removePhoto(id, path) }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.discardDraft(id)
            onDone()
        }
    }

    fun submit(onDone: () -> Unit) {
        viewModelScope.launch {
            if (repo.submit(id, notes.value)) onDone()
        }
    }
}
