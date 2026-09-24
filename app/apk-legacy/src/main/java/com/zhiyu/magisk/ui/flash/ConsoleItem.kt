package com.zhiyu.magisk.ui.flash

import com.zhiyu.magisk.R
import com.zhiyu.magisk.databinding.DiffItem
import com.zhiyu.magisk.databinding.ItemWrapper
import com.zhiyu.magisk.databinding.RvItem

class ConsoleItem(
    override val item: String
) : RvItem(), DiffItem<ConsoleItem>, ItemWrapper<String> {
    override val layoutRes = R.layout.item_console_md2
}
