package io.kotlin.stomp.client.internal

import io.hotmoka.network.thin.client.webSockets.stomp.ResultHandler
import io.kotlin.stomp.client.Subscription

/**
 * Internal subscription class which holds the client subscription,
 * the result handler of the topic subscription, and an optional callback
 * to be invoked after a successful subscription.
 */
class InternalSubscription(
    val clientSubscription: Subscription,
    val resultHandler: ResultHandler<*>,
    val afterSubscribed: (() -> Unit)? = null
)