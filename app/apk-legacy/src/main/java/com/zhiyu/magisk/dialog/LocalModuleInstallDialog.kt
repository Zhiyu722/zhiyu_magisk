package com.zhiyu.magisk.dialog

import android.net.Uri
import com.zhiyu.magisk.MainDirections
import com.zhiyu.magisk.core.Const
import com.zhiyu.magisk.core.R
import com.zhiyu.magisk.events.DialogBuilder
import com.zhiyu.magisk.ui.module.ModuleViewModel
import com.zhiyu.magisk.view.MagiskDialog

class LocalModuleInstallDialog(
    private val viewModel: ModuleViewModel,
    private val uri: Uri,
    private val displayName: String
) : DialogBuilder {
    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(R.string.confirm_install_title)
            setMessage(context.getString(R.string.confirm_install, displayName))
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
                onClick {
                    viewModel.apply {
                        MainDirections.actionFlashFragment(Const.Value.FLASH_ZIP, uri).navigate()
                    }
                }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = android.R.string.cancel
            }
        }
    }
}
