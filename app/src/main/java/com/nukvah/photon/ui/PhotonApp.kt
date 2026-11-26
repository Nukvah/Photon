package com.nukvah.photon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Everything the screens can ask the outside world to do (pickers, share sheets). */
interface PhotonActions {
    fun selectPhotos()
    fun shareEmail(batch: UiState.Done)
    fun saveToFolder(batch: UiState.Done)
    fun otherApps(batch: UiState.Done)
    fun shareOne(batch: UiState.Done, index: Int)
    fun openSettings()
}

/** Shows the screen for the current state; back on Processing cancels, on Done closes. */
@Composable
fun PhotonApp(state: UiState, viewModel: PhotonViewModel, actions: PhotonActions, modifier: Modifier = Modifier) {
    when (state) {
        is UiState.Idle -> IdleScreen(
            state = state,
            onSelectPhotos = actions::selectPhotos,
            onSizeChange = viewModel::selectSize,
            onShareAgain = viewModel::reopenLastBatch,
            onSettings = actions::openSettings,
            modifier = modifier,
        )
        is UiState.Processing -> {
            BackHandler(onBack = viewModel::cancel)
            ProcessingScreen(state, onCancel = viewModel::cancel, modifier = modifier)
        }
        is UiState.Done -> {
            BackHandler(onBack = viewModel::close)
            DoneScreen(
                state = state,
                onClose = viewModel::close,
                onSizeChange = viewModel::changeSize,
                onShareEmail = { actions.shareEmail(state) },
                onSaveToFolder = { actions.saveToFolder(state) },
                onOtherApps = { actions.otherApps(state) },
                onShareOne = { actions.shareOne(state, it) },
                modifier = modifier,
            )
        }
    }
}

