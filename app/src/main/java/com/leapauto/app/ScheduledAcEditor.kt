package com.leapauto.app

fun ScheduledAcState.editorPlan(localPlan: ScheduledAcPlan): ScheduledAcPlan =
    if (hasSchedule) {
        plan.copy(
            closeWindows = localPlan.closeWindows,
            closeSunshade = localPlan.closeSunshade
        )
    } else {
        localPlan.copy(enabled = false)
    }
