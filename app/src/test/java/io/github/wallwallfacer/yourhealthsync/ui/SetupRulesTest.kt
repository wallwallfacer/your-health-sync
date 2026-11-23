package io.github.wallwallfacer.yourhealthsync.ui

import io.github.wallwallfacer.yourhealthsync.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupRulesTest {
    private val all = Metric.entries.toSet()

    @Test
    fun aFreshSetupStartsWithWhatHealthConnectAllowed() {
        assertEquals(setOf(Metric.WEIGHT, Metric.BODY_FAT), SetupRules.typesForDataStep(all, setOf(Metric.WEIGHT, Metric.BODY_FAT)))
    }

    @Test
    fun runningSetupAgainKeepsTypesTheUserTurnedOff() {
        val enabled = all - Metric.BONE_MASS

        assertEquals(enabled, SetupRules.typesForDataStep(enabled, all))
    }

    @Test
    fun withNoPermissionsTheChoiceIsLeftAlone() {
        assertEquals(all, SetupRules.typesForDataStep(all, emptySet()))
    }
}
