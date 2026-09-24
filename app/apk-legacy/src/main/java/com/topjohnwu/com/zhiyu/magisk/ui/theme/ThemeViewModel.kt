package com.zhiyu.magisk.ui.theme

import com.zhiyu.magisk.arch.BaseViewModel
import com.zhiyu.magisk.core.Config
import com.zhiyu.magisk.dialog.DarkThemeDialog
import com.zhiyu.magisk.events.RecreateEvent
import com.zhiyu.magisk.view.TappableHeadlineItem

class ThemeViewModel : BaseViewModel(), TappableHeadlineItem.Listener {

    val themeHeadline = TappableHeadlineItem.ThemeMode

    override fun onItemPressed(item: TappableHeadlineItem) = when (item) {
        is TappableHeadlineItem.ThemeMode -> DarkThemeDialog().show()
    }

    fun saveTheme(theme: Theme) {
        if (!theme.isSelected) {
            Config.themeOrdinal = theme.ordinal
            RecreateEvent().publish()
        }
    }
}
