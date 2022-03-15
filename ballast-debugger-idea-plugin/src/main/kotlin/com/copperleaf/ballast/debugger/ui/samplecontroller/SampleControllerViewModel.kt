package com.copperleaf.ballast.debugger.ui.samplecontroller

import com.copperleaf.ballast.core.BasicViewModel
import com.copperleaf.ballast.core.DefaultViewModelConfiguration
import kotlinx.coroutines.CoroutineScope

class SampleControllerViewModel(
    coroutineScope: CoroutineScope,
    configurationBuilder: DefaultViewModelConfiguration.Builder,
    inputHandler: SampleControllerInputHandler,
    eventHandler: SampleControllerEventHandler,
) : BasicViewModel<
    SampleControllerContract.Inputs,
    SampleControllerContract.Events,
    SampleControllerContract.State>(
    config = configurationBuilder
        .forViewModel(
            initialState = SampleControllerContract.State(),
            inputHandler = inputHandler
        )
        .apply {
            name = "Sample Controller"
        }
        .build(),
    eventHandler = eventHandler,
    coroutineScope = coroutineScope
)