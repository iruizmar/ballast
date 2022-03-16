package com.copperleaf.ballast.internal

import com.copperleaf.ballast.BallastLogger
import com.copperleaf.ballast.InputHandlerScope
import com.copperleaf.ballast.InputStrategy
import com.copperleaf.ballast.SideEffectScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

internal class InputHandlerScopeImpl<Inputs : Any, Events : Any, State : Any>(
    override val logger: BallastLogger,
    private val guardian: InputStrategy.Guardian,
    private val _state: MutableStateFlow<State>,
    private val sendEventToQueue: suspend (Events) -> Unit,
) : InputHandlerScope<Inputs, Events, State> {
    private val sideEffects = mutableListOf<SideEffectRequest<Inputs, Events, State>>()

    override suspend fun getCurrentState(): State {
        guardian.checkStateAccess()
        return _state.value
    }

    override suspend fun updateState(block: (State) -> State) {
        guardian.checkStateUpdate()
        _state.update(block)
    }

    override suspend fun updateStateAndGet(block: (State) -> State): State {
        guardian.checkStateUpdate()
        return _state.updateAndGet(block)
    }

    override suspend fun getAndUpdateState(block: (State) -> State): State {
        guardian.checkStateUpdate()
        return _state.getAndUpdate(block)
    }

    override suspend fun postEvent(event: Events) {
        guardian.checkPostEvent()
        sendEventToQueue(event)
    }

    override fun sideEffect(
        key: String,
        block: suspend SideEffectScope<Inputs, Events, State>.() -> Unit
    ) {
        guardian.checkSideEffect()
        sideEffects += SideEffectRequest(key, block)
    }

    override fun noOp() {
        guardian.checkNoOp()
    }

    internal fun close(): List<SideEffectRequest<Inputs, Events, State>> {
        guardian.close()
        return sideEffects.toList()
    }
}
