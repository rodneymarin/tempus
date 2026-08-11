package com.rodneymarin.tempus.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val EDITOR = "tracker/edit?trackerId={trackerId}"
    const val DETAIL = "tracker/{trackerId}"

    fun editor(trackerId: Long? = null): String =
        if (trackerId == null) "tracker/edit" else "tracker/edit?trackerId=$trackerId"
    fun detail(trackerId: Long): String = "tracker/$trackerId"
}
