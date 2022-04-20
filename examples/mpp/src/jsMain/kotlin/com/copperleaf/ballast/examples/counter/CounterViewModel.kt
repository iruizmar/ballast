package com.copperleaf.ballast.examples.counter

import com.copperleaf.ballast.BallastViewModelConfiguration
import com.copperleaf.ballast.core.BasicViewModel
import com.copperleaf.ballast.examples.counter.CounterContract
import com.copperleaf.ballast.examples.counter.CounterEventHandler
import kotlinx.coroutines.CoroutineScope

class CounterViewModel(
    viewModelCoroutineScope: CoroutineScope,
    config: BallastViewModelConfiguration<
        CounterContract.Inputs,
        CounterContract.Events,
        CounterContract.State>,
    eventHandler: CounterEventHandler
) : BasicViewModel<
    CounterContract.Inputs,
    CounterContract.Events,
    CounterContract.State>(
    config = config,
    eventHandler = eventHandler,
    coroutineScope = viewModelCoroutineScope,
)