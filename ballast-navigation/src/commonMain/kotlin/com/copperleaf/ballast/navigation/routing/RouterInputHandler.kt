package com.copperleaf.ballast.navigation.routing

import com.copperleaf.ballast.InputHandler
import com.copperleaf.ballast.InputHandlerScope

public class RouterInputHandler : InputHandler<
    RouterContract.Inputs,
    RouterContract.Events,
    RouterContract.State> {

    override suspend fun InputHandlerScope<
        RouterContract.Inputs,
        RouterContract.Events,
        RouterContract.State>.handleInput(
        input: RouterContract.Inputs
    ) {
        updateBackstackAndSendNotifications(input.token) {
            input.updateBackstack(it)
        }
    }

    private suspend fun InputHandlerScope<
        RouterContract.Inputs,
        RouterContract.Events,
        RouterContract.State>.updateBackstackAndSendNotifications(
        token: Any?,
        updateFn: (RouterContract.State) -> RouterContract.State
    ) {
        val originalState = getCurrentState()

        val updatedState = updateStateAndGet(updateFn)

        postEvent(
            if (updatedState.backstack.isEmpty()) {
                if (originalState.backstack.isEmpty()) {
                    error("cannot go back, backstack was empty")
                } else {
                    RouterContract.Events.OnBackstackEmptied(token = token)
                }
            } else {
                if (updatedState != originalState) {
                    RouterContract.Events.NewDestination(
                        backstack = updatedState.backstack,
                        currentDestinationOrNotFound = updatedState.currentDestinationOrNotFound,
                        currentTag = updatedState.currentTag,
                        token = token
                    )
                } else {
                    RouterContract.Events.NoChange(token = token)
                }
            }
        )
    }
}
