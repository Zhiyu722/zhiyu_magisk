package com.zhiyu.magisk.ui.module

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhiyu.magisk.R
import com.zhiyu.magisk.ui.component.rememberExternalStoragePermissionLauncher
import com.zhiyu.magisk.ui.glass.GlassScreen
import com.zhiyu.magisk.ui.glass.GlassTopAppBar
import com.zhiyu.magisk.ui.terminal.TerminalScreen
import com.zhiyu.magisk.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionScreen(
    viewModel: ActionViewModel,
    actionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val finished = actionState != ActionViewModel.State.RUNNING
    val saveLog = rememberExternalStoragePermissionLauncher {
        viewModel.saveLog()
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    GlassScreen(
        modifier = modifier,
        topBar = {
            GlassTopAppBar(
                title = { Text(actionName) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (finished) {
                        IconButton(
                            onClick = saveLog
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_save),
                                contentDescription = stringResource(CoreR.string.menuSaveLog),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        TerminalScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onEmulatorCreated = { viewModel.onEmulatorCreated(it) },
        )
    }
}
