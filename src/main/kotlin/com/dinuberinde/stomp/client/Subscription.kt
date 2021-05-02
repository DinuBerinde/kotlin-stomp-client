package com.dinuberinde.stomp.client

import com.dinuberinde.stomp.client.internal.ResultHandler
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Subscription to a topic with its result handler.
 */
class Subscription(
    val topic: String,
    val subscriptionId: String,
    val resultHandler: ResultHandler<*>,
) {
    private val LOGGER: Logger = Logger.getLogger("Subscription")
    private val LOCK = Object()
    private var isSubscribed = false

    /**
     * Emits that the subscription is completed.
     */
    fun emitSubscription() {
        synchronized(LOCK) { LOCK.notify() }
    }

    /**
     * Awaits if necessary for the subscription to complete.
     */
    fun awaitSubscription() {
        synchronized(LOCK) {
            if (!isSubscribed) try {
                LOCK.wait()
                isSubscribed = true
            } catch (e: InterruptedException) {
                LOGGER.log(
                    Level.SEVERE,
                    "Interrupted while waiting for subscription",
                    e
                )
            }
        }
    }

}