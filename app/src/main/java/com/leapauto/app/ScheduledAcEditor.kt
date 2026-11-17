package com.leapauto.app

fun ScheduledAcState.editorPlan(localPlan: ScheduledAcPlan): ScheduledAcPlan =
    if (hasSchedule) {
        plan.copy(
            closeWindows = localPlan.closeWindows,
            closeSunshade = localPlan.closeSunshade,
            driverHeating = localPlan.driverHeating,
            driverVent = localPlan.driverVent,
            passengerHeating = localPlan.passengerHeating,
            passengerVent = localPlan.passengerVent,
            leftRearHeating = localPlan.leftRearHeating,
            leftRearVent = localPlan.leftRearVent,
            rightRearHeating = localPlan.rightRearHeating,
            rightRearVent = localPlan.rightRearVent,
            steeringWheelHeating = localPlan.steeringWheelHeating
        )
    } else {
        localPlan.copy(enabled = false)
    }
