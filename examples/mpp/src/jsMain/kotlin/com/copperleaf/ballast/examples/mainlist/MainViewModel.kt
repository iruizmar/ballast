package com.copperleaf.ballast.examples.mainlist

import com.copperleaf.ballast.BallastViewModelConfiguration
import com.copperleaf.ballast.core.BasicViewModel
import kotlinx.coroutines.CoroutineScope

class MainViewModel(
    coroutineScope: CoroutineScope,
    config: BallastViewModelConfiguration<
        MainContract.Inputs,
        MainContract.Events,
        MainContract.State>,
    eventHandler: MainEventHandler,
) : BasicViewModel<
    MainContract.Inputs,
    MainContract.Events,
    MainContract.State>(
    config = config,
    eventHandler = eventHandler,
    coroutineScope = coroutineScope,
)
