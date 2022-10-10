package com.copperleaf.ballast

@BallastDsl
public interface EventHandlerScope<Inputs : Any, Events : Any, State : Any> {

    public val logger: BallastLogger

    /**
     * As the response to handling an Event, post another Input back to the VM.
     */
    public suspend fun postInput(input: Inputs)
}