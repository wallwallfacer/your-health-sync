package io.github.wallwallfacer.yourhealthsync.ui

import io.github.wallwallfacer.yourhealthsync.model.BodySample

/** What one data-type row on the dashboard shows. */
sealed interface RowState {
    data object Off : RowState
    data object NotAllowed : RowState
    data object Waiting : RowState
    data class Synced(val latest: BodySample) : RowState

    companion object {
        fun of(enabled: Boolean, granted: Boolean, latest: BodySample?): RowState = when {
            !enabled -> Off
            !granted -> NotAllowed
            latest == null -> Waiting
            else -> Synced(latest)
        }
    }
}
