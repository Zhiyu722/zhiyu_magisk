package com.zhiyu.magisk.ui.install

import com.zhiyu.magisk.R
import com.zhiyu.magisk.arch.BaseFragment
import com.zhiyu.magisk.arch.viewModel
import com.zhiyu.magisk.databinding.FragmentInstallMd2Binding
import com.zhiyu.magisk.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
