package io.github.wallwallfacer.yourhealthsync.ui

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Test

class RowStateTest {
    private val sample = BodySample(Metric.WEIGHT, 1_800_000_000_000L, 72.4)

    @Test
    fun aDisabledTypeIsOffWhateverElseIsTrue() {
        assertEquals(RowState.Off, RowState.of(enabled = false, granted = true, latest = sample))
    }

    @Test
    fun anEnabledTypeWithoutPermissionIsNotAllowed() {
        assertEquals(RowState.NotAllowed, RowState.of(enabled = true, granted = false, latest = sample))
    }

    @Test
    fun anEnabledGrantedTypeWithoutDataIsWaiting() {
        assertEquals(RowState.Waiting, RowState.of(enabled = true, granted = true, latest = null))
    }

    @Test
    fun anEnabledGrantedTypeWithDataShowsItsLatestSample() {
        assertEquals(RowState.Synced(sample), RowState.of(enabled = true, granted = true, latest = sample))
    }
}
