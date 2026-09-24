package com.zhiyu.magisk.arch

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhiyu.magisk.core.di.ServiceLocator
import com.zhiyu.magisk.ui.deny.DenyListViewModel
import com.zhiyu.magisk.ui.flash.FlashViewModel
import com.zhiyu.magisk.ui.home.HomeViewModel
import com.zhiyu.magisk.ui.install.InstallViewModel
import com.zhiyu.magisk.ui.log.LogViewModel
import com.zhiyu.magisk.ui.module.ActionViewModel
import com.zhiyu.magisk.ui.module.ModuleViewModel
import com.zhiyu.magisk.ui.settings.SettingsViewModel
import com.zhiyu.magisk.ui.superuser.SuperuserViewModel
import com.zhiyu.magisk.ui.surequest.SuRequestViewModel

val VMFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { HomeViewModel(ServiceLocator.networkService) }
    initializer { LogViewModel(ServiceLocator.logRepo) }
    initializer { SuperuserViewModel(ServiceLocator.policyDB) }
    initializer { InstallViewModel(ServiceLocator.networkService) }
    initializer { SuRequestViewModel(ServiceLocator.policyDB, ServiceLocator.timeoutPrefs) }
    initializer { DenyListViewModel() }
    initializer { FlashViewModel() }
    initializer { ActionViewModel() }
    initializer { ModuleViewModel() }
    initializer { SettingsViewModel() }
}
