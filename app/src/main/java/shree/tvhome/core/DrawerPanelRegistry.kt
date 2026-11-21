package shree.tvhome.core

import android.content.Context
import android.widget.LinearLayout
import shree.tvhome.source.LauncherPreferences

object DrawerPanelRegistry {

    data class PanelDestinationSpec(
        val mode: String,
        val headerIcon: String,
        val headerTitle: String,
        val navTag: String,
        val showInControlCenter: Boolean = false,
        val tileIcon: String = "",
        val tileTitle: String = "",
        val subtitleResolver: ((Context, LauncherPreferences) -> String)? = null,
        val contentBuilder: ((
            ctx: Context,
            panel: LinearLayout,
            lp: LauncherPreferences,
            navigateTo: (String) -> Unit,
            refreshCurrent: () -> Unit,
            closeDrawer: () -> Unit
        ) -> Unit)? = null
    )

    private var destinations: List<PanelDestinationSpec> = emptyList()
    private var destinationMap: Map<String, PanelDestinationSpec> = emptyMap()

    fun registerDestinations(items: List<PanelDestinationSpec>) {
        destinations = items
        destinationMap = items.associateBy { it.mode }
    }

    fun getDestination(mode: String): PanelDestinationSpec? = destinationMap[mode]

    fun getControlCenterTiles(): List<PanelDestinationSpec> =
        destinations.filter { it.showInControlCenter }
}
