package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import com.projectlumen.app.core.enums.ScheduleCategory
import com.projectlumen.app.core.enums.ScheduleEditScope
import com.projectlumen.app.core.enums.ScheduleRecurrence
import com.projectlumen.app.core.enums.ScheduleReminderMethod
import com.projectlumen.app.core.repositories.ScheduleRepository
import com.projectlumen.app.core.schedule.ScheduleDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Draft of the schedule detail page. `editingId == null` means the page is creating a new entry. */
internal data class ScheduleDraftState(
    val editingId: Long? = null,
    val seriesId: Long = 0L,
    val hasSeries: Boolean = false,
    val draft: ScheduleDraft,
    /** Set while the page waits for the user to pick an edit scope; see [SchedulePendingAction]. */
    val pendingAction: SchedulePendingAction? = null,
)

internal enum class SchedulePendingAction { SAVE, DELETE }

internal class ProjectLumenScheduleFeatureEntry(
    private val scope: CoroutineScope,
    private val repository: ScheduleRepository,
    private val rearm: suspend () -> Unit,
    private val recordHandledFailure: (Throwable) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val _detailState = MutableStateFlow<ScheduleDraftState?>(null)
    val detailState: StateFlow<ScheduleDraftState?> = _detailState.asStateFlow()

    fun openNew() {
        _detailState.value = ScheduleDraftState(draft = defaultScheduleDraft(now()))
    }

    fun open(id: Long) {
        if (id <= 0L) {
            openNew()
            return
        }
        scope.launch {
            runCatching { repository.get(id) }
                .onSuccess { occurrence ->
                    // A missing row means the to-do was removed elsewhere; fall back to a usable
                    // new-entry form instead of leaving the page blank.
                    _detailState.value = occurrence?.let { draftStateOf(it) }
                        ?: ScheduleDraftState(draft = defaultScheduleDraft(now()))
                }
                .onFailure(recordHandledFailure)
        }
    }

    fun close() {
        _detailState.value = null
    }

    fun updateDraft(transform: (ScheduleDraft) -> ScheduleDraft) {
        val current = _detailState.value ?: return
        _detailState.value = current.copy(draft = transform(current.draft))
    }

    /**
     * A to-do that belongs to a repeating series has several valid interpretations of "save", so
     * the write waits for the user to pick an edit scope first. Everything else writes immediately.
     */
    fun requestSave() {
        val current = _detailState.value ?: return
        if (current.hasSeries && current.editingId != null) {
            _detailState.value = current.copy(pendingAction = SchedulePendingAction.SAVE)
            return
        }
        persist(current, ScheduleEditScope.ALL)
    }

    fun requestDelete() {
        val current = _detailState.value ?: return
        val editingId = current.editingId ?: return
        if (current.hasSeries) {
            _detailState.value = current.copy(pendingAction = SchedulePendingAction.DELETE)
            return
        }
        delete(editingId, ScheduleEditScope.ALL)
    }

    fun confirmScope(scope: ScheduleEditScope) {
        val current = _detailState.value ?: return
        val action = current.pendingAction ?: return
        // Clear the prompt up front so the dialog dismisses as soon as the choice is made.
        _detailState.value = current.copy(pendingAction = null)
        when (action) {
            SchedulePendingAction.SAVE -> persist(current, scope)
            SchedulePendingAction.DELETE -> current.editingId?.let { delete(it, scope) }
        }
    }

    fun dismissScopePrompt() {
        val current = _detailState.value ?: return
        if (current.pendingAction == null) return
        _detailState.value = current.copy(pendingAction = null)
    }

    fun setCompleted(id: Long, completed: Boolean) {
        scope.launch {
            runCatching {
                repository.setCompleted(id, completed)
                rearm()
            }.onFailure(recordHandledFailure)
        }
    }

    private fun persist(state: ScheduleDraftState, scope: ScheduleEditScope) {
        val editingId = state.editingId
        val draft = state.draft
        this.scope.launch {
            runCatching {
                if (editingId == null) {
                    repository.create(draft)
                } else {
                    repository.update(editingId, draft, scope)
                }
                rearm()
            }
                .onSuccess { _detailState.value = null }
                .onFailure(recordHandledFailure)
        }
    }

    private fun delete(id: Long, scope: ScheduleEditScope) {
        this.scope.launch {
            runCatching {
                repository.delete(id, scope)
                rearm()
            }
                .onSuccess { _detailState.value = null }
                .onFailure(recordHandledFailure)
        }
    }

    private fun draftStateOf(occurrence: ScheduleOccurrenceEntity): ScheduleDraftState {
        return ScheduleDraftState(
            editingId = occurrence.id,
            seriesId = occurrence.seriesId,
            hasSeries = occurrence.seriesId != 0L,
            draft = ScheduleDraft(
                title = occurrence.title,
                category = scheduleCategoryOf(occurrence.category),
                allDay = occurrence.allDay,
                startAt = occurrence.startAt,
                endAt = occurrence.endAt,
                reminderMinutesBefore = occurrence.reminderMinutesBefore,
                reminderMethod = scheduleReminderMethodOf(occurrence.reminderMethod),
                recurrence = scheduleRecurrenceOf(occurrence.recurrence),
                recurrenceUntil = occurrence.recurrenceUntil,
            ),
        )
    }
}

internal fun scheduleCategoryOf(value: String): ScheduleCategory =
    ScheduleCategory.entries.firstOrNull { it.name == value } ?: ScheduleCategory.PERSONAL

internal fun scheduleRecurrenceOf(value: String): ScheduleRecurrence =
    ScheduleRecurrence.entries.firstOrNull { it.name == value } ?: ScheduleRecurrence.NONE

internal fun scheduleReminderMethodOf(value: String): ScheduleReminderMethod =
    ScheduleReminderMethod.entries.firstOrNull { it.name == value } ?: ScheduleReminderMethod.NOTIFICATION

internal const val SCHEDULE_DEFAULT_DURATION_MILLIS = 60L * 60L * 1000L

/** New to-dos start on the next five-minute boundary and run for an hour. */
internal fun defaultScheduleDraft(nowMillis: Long): ScheduleDraft {
    val step = 5L * 60L * 1000L
    val startAt = ((nowMillis + step - 1L) / step) * step
    return ScheduleDraft(
        title = "",
        category = ScheduleCategory.PERSONAL,
        allDay = false,
        startAt = startAt,
        endAt = startAt + SCHEDULE_DEFAULT_DURATION_MILLIS,
        reminderMinutesBefore = SCHEDULE_REMINDER_NONE,
        reminderMethod = ScheduleReminderMethod.NOTIFICATION,
        recurrence = ScheduleRecurrence.NONE,
        recurrenceUntil = 0L,
    )
}
