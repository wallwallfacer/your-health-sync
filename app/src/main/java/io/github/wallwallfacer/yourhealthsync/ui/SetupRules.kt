package io.github.wallwallfacer.yourhealthsync.ui

import io.github.wallwallfacer.yourhealthsync.model.Metric

object SetupRules {
    /**
     * Data types ticked when the wizard reaches its data-types step: the
     * enabled types narrowed to what Health Connect allowed. Narrowing (not
     * replacing) keeps types the user turned off when setup runs again.
     */
    fun typesForDataStep(enabled: Set<Metric>, granted: Set<Metric>): Set<Metric> =
        if (granted.isEmpty()) enabled else enabled intersect granted
}
