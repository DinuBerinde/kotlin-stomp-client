package io.kotlin.stomp.client

import io.hotmoka.network.thin.client.webSockets.StompClient
import io.hotmoka.network.thin.client.webSockets.stomp.ResultHandler

/**
 * A subscription to a webSocket topic.
 */
class Subscription(
    val topic: String,
    val subscriptionId: String,
    private val stompClient: StompClient,
    val resultHandler: ResultHandler<*>
) {

    /**
     * It unsubscribes from the topic.
     */
    fun unsubscribe() {
        stompClient.unsubscribeFrom(topic)
    }
}