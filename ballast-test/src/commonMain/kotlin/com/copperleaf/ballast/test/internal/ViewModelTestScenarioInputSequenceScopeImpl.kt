package com.copperleaf.ballast.test.internal

import com.copperleaf.ballast.BallastLogger
import com.copperleaf.ballast.test.ViewModelTestScenarioInputSequenceScope
import com.copperleaf.ballast.test.internal.vm.TestViewModel
import kotlinx.coroutines.CompletableDeferred

internal class ViewModelTestScenarioInputSequenceScopeImpl<Inputs : Any, Events : Any, State : Any>(
    private val logger: BallastLogger,
    private val vm: TestViewModel<Inputs, Events, State>
) : ViewModelTestScenarioInputSequenceScope<Inputs, Events, State> {

    override suspend fun send(input: Inputs) {
        logger.debug("        before send ProcessInput")
        val deferred = CompletableDeferred<Unit>()
        vm.send(
            TestViewModel.Inputs.ProcessInput(input, deferred)
        )
        logger.debug("        after send ProcessInput")
        deferred.await()
        logger.debug("        after await ProcessInput")
    }

    override suspend fun sendAndAwait(input: Inputs): State {
        logger.debug("        before send AwaitInput")
        val deferred = CompletableDeferred<Unit>()
        vm.send(
            TestViewModel.Inputs.AwaitInput(input, deferred)
        )
        logger.debug("        after send AwaitInput")
        deferred.await()
        val latestStateAfterProcessing = vm.observeStates().value
        logger.debug("        after await AwaitInput: $latestStateAfterProcessing")
        return latestStateAfterProcessing
    }

    internal suspend fun finish() {
        logger.debug("        before send TestCompleted")
        val deferred = CompletableDeferred<Unit>()
        vm.send(
            TestViewModel.Inputs.TestCompleted(deferred)
        )
        logger.debug("        after send TestCompleted")

        // close the channel to prevent new items from coming in and cancelling the TestCompleted event
        vm.close()

        deferred.await()
        logger.debug("        after await TestCompleted")
    }
}
