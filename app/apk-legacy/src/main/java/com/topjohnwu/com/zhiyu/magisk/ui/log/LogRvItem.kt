package com.zhiyu.magisk.ui.log

import com.zhiyu.magisk.R
import com.zhiyu.magisk.databinding.DiffItem
import com.zhiyu.magisk.databinding.ItemWrapper
import com.zhiyu.magisk.databinding.ObservableRvItem

class LogRvItem(
    override val item: String
) : ObservableRvItem(), DiffItem<LogRvItem>, ItemWrapper<String> {
    override val layoutRes = R.layout.item_log_textview
}
