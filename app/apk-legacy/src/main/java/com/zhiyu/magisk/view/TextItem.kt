package com.zhiyu.magisk.view

import com.zhiyu.magisk.R
import com.zhiyu.magisk.databinding.DiffItem
import com.zhiyu.magisk.databinding.ItemWrapper
import com.zhiyu.magisk.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
