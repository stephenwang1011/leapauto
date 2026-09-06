package com.leapauto.app

/** Pure daily cadence rule for the voluntary author-support reminder. */
internal object AuthorSupportReminder {
    fun shouldShow(
        lastShownEpochDay: Long,
        todayEpochDay: Long,
        permanentlyDisabled: Boolean
    ): Boolean = !permanentlyDisabled && lastShownEpochDay != todayEpochDay
}
