package com.dinuberinde.stomp.client

import com.dinuberinde.stomp.client.exceptions.InternalFailureException
import com.dinuberinde.stomp.client.exceptions.NetworkExceptionResponse
import com.dinuberinde.stomp.client.internal.ResultHandler
import com.dinuberinde.stomp.client.internal.stomp.StompCommand
import com.dinuberinde.stomp.client.internal.stomp.StompMessageHelper
import okhttp3.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.logging.Level


/**
 * A thread safe webSocket client which implements the STOMP protocol [https://stomp.github.io/index.html].
 * @param url the url of the webSocket endpoint, e.g ws://localhost:8080
 */
class StompClient(private val url: String) : AutoCloseable {

    /**
     * The unique identifier of this client. This allows more clients to connect to the same server.
     */
    private val clientKey = generateClientKey()

    /**
     * The okHttpClient.
     */
    private val okHttpClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    /**
     * The websockets subscriptions open so far with this client, per topic.
     */
    private val subscriptions: ConcurrentHashMap<String, Subscription> = ConcurrentHashMap()

    /**
     * The websockets queues where the results are published and consumed, per topic.
     */
    private val queues: ConcurrentHashMap<String, BlockingQueue<Any>> = ConcurrentHashMap()

    /**
     * Lock to synchronize the initial connection of the websocket client.
     */
    private val CONNECTION_LOCK = Object()

    /**
     * Boolean to track whether the client is connected.
     */
    private var isClientConnected = false

    /**
     * The websocket instance.
     */
    private lateinit var webSocket: WebSocket


    /**
     * It opens a webSocket connection and connects to the STOMP endpoint.
     * @param onStompConnectionOpened handler for a successful STOMP endpoint connection
     * @param onWebSocketFailure handler for the webSocket connection failure due to an error reading from or writing to the network
     * @param onWebSocketClosed handler for the webSocket connection when both peers have indicated that no more messages
     * will be transmitted and the connection has been successfully released.
     */
    fun connect(
        onStompConnectionOpened: (() -> Unit)? = null,
        onWebSocketFailure: (() -> Unit)? = null,
        onWebSocketClosed: (() -> Unit)? = null
    ) {
        println("[Stomp client] Connecting to $url ...")

        val request = Request.Builder()
            .url(url)
            .addHeader("uuid", clientKey)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                // we open the stomp session
                webSocket.send(StompMessageHelper.buildConnectMessage())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {

                try {
                    val message = StompMessageHelper.parseStompMessage(text)
                    val payload = message.payload

                    when (message.command) {

                        StompCommand.CONNECTED -> {
                            println("[Stomp client] Connected to stomp session")
                            onStompConnectionOpened?.invoke()
                            emitClientConnected()
                        }
                        StompCommand.RECEIPT -> {
                            val destination = message.headers.getDestination()
                            println("[Stomp client] Subscribed to topic $destination")

                            val subscription =
                                subscriptions[destination] ?: throw NoSuchElementException("Topic not found")
                            subscription.emitSubscription()
                        }
                        StompCommand.ERROR -> {
                            println("[Stomp client] STOMP Session Error: $payload")
                            // clean-up client resources because the server closed the connection
                            close()
                            onWebSocketFailure?.invoke()
                        }
                        StompCommand.MESSAGE -> {
                            val destination = message.headers.getDestination()
                            println("[Stomp client] Received message from topic $destination")
                            handleStompDestinationResult(payload, destination)
                        }
                        else -> println("Got an unknown message")
                    }

                } catch (e: Exception) {
                    println("[Stomp client] Got an exception while handling message")
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("[Stomp client] WebSocket Session error")
                t.printStackTrace()

                close()
                onWebSocketFailure?.invoke()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                println("[Stomp client] WebSocket session closed")
                onWebSocketClosed?.invoke()
            }
        })

        awaitClientConnection()
    }

    /**
     * Emits that the client is connected.
     */
    private fun emitClientConnected() {
        synchronized(CONNECTION_LOCK) { CONNECTION_LOCK.notify() }
    }

    /**
     * Awaits if necessary until the websocket client is connected.
     */
    private fun awaitClientConnection() {
        synchronized(CONNECTION_LOCK) {
            if (!isClientConnected) try {
                CONNECTION_LOCK.wait()
                isClientConnected = true
            } catch (e: InterruptedException) {
                println("[Stomp client] Interrupted while waiting for subscription")
                e.printStackTrace()
            }
        }
    }

    /**
     * Returns the key of this client instance. Each instance has a different key.
     */
    fun getClientKey(): String {
        return this.clientKey
    }

    /**
     * Subscribes to a topic providing a {@link BiConsumer} handler to handle the result published by the topic.
     * The subscription is recycled and the method awaits for the subscription to complete.
     * @param topic the topic
     * @param resultType the result type class
     * @param handler the handler to consume with the result and/or error
     */
    fun <T> subscribeToTopic(
        topic: String,
        resultType: Class<T>,
        handler: BiConsumer<T?, ErrorModel?>
    ) {

        val subscription = subscriptions.computeIfAbsent(topic) {

            val resultHandler = object : ResultHandler<T>(resultType) {

                override fun deliverResult(result: String) {
                    try {
                        handler.accept(toModel(result), null)
                    } catch (e: InternalFailureException) {
                        deliverError(
                            ErrorModel(
                                e.message ?: "Got a deserialization error",
                                InternalFailureException::class.java.name
                            )
                        )
                    }
                }

                override fun deliverError(errorModel: ErrorModel) {
                    handler.accept(null, errorModel)
                }

                override fun deliverNothing() {
                    handler.accept(null, null)
                }
            }

            subscribeInternal(it, resultHandler)
        }

        subscription.awaitSubscription()
    }

    /**
     * It sends a payload to a previous subscribed topic. The method {@link #subscribeToTopic(String, Class, BiConsumer)}}
     * is used to subscribe to a topic.
     *
     * @param topic   the topic
     * @param payload the payload
     */
    fun <T> sendToTopic(
        topic: String,
        payload: T
    ) {
        println("[Stomp client] Sending to ${topic}")
        webSocket.send(StompMessageHelper.buildSendMessage(topic, payload))
    }

    /**
     * It sends a message payload to the standard "user" topic destination by performing an initial subscription
     * and then it waits for the result. The subscription is recycled.
     *
     * @param topic      the topic
     * @param resultType the result type
     */
    fun <T> subscribeAndSend(
        topicDestination: String,
        resultType: Class<T>,
    ): T? {
        return subscribeAndSend(topicDestination, resultType, null)
    }

    /**
     * It sends a message payload to the standard "user" topic destination by performing an initial subscription
     * and then it waits for the result. The subscription is recycled.
     *
     * @param <T>        the type of the expected result
     * @param <P>        the type of the payload
     * @param topicDestination      the topic
     * @param resultType the result type
     * @param payload    the payload
     */
    fun <T, P> subscribeAndSend(
        topicDestination: String,
        resultType: Class<T>,
        payload: P
    ): T? {
        println("[Stomp client] Subscribing to ${topicDestination}")

        val resultTopic = "/user/$clientKey$topicDestination"
        val result: Any

        val queue = queues.computeIfAbsent(topicDestination) { LinkedBlockingQueue(1) }
        synchronized(queue) {
            subscribe(resultTopic, resultType, queue)

            println("[Stomp client] Sending payload to  $topicDestination")
            webSocket.send(StompMessageHelper.buildSendMessage(topicDestination, payload))
            result = queue.take()
        }

        return if (result is Nothing) null else if (result is ErrorModel) throw NetworkExceptionResponse(
            ErrorModel.toString(
                result
            )
        ) else result as T
    }

    /**
     * Subscribes to a topic.
     *
     * @param topic      the topic
     * @param resultType the result type
     * @param queue      the queue
     * @param <T>        the result type
     */
    private fun <T> subscribe(
        topic: String,
        resultType: Class<T>,
        queue: BlockingQueue<Any>
    ) {

        val subscription = subscriptions.computeIfAbsent(topic) {

            val resultHandler = object : ResultHandler<T>(resultType) {

                override fun deliverResult(result: String) {
                    try {
                        deliverInternal(toModel(result))
                    } catch (e: InternalFailureException) {
                        deliverError(
                            ErrorModel(
                                e.message ?: "Got a deserialization error",
                                InternalFailureException::class.java.name
                            )
                        )
                    }
                }

                override fun deliverError(errorModel: ErrorModel) {
                    deliverInternal(errorModel)
                }

                override fun deliverNothing() {
                    deliverInternal(Nothing())
                }

                private fun deliverInternal(result: Any) {
                    try {
                        queue.put(result)
                    } catch (e: java.lang.Exception) {
                        println("[Stomp client] Queue put error")
                        e.printStackTrace()
                    }
                }
            }

            subscribeInternal(it, resultHandler)
        }
        subscription.awaitSubscription()
    }


    /**
     * It sends a payload to a destination.
     * @param destination the destination
     * @param payload the payload
     */
    fun <T> sendTo(destination: String, payload: T?) {
        println("[Stomp client] Sending message to destination $destination")
        webSocket.send(StompMessageHelper.buildSendMessage(destination, payload))
    }

    /**
     * It handles a STOMP result message of a destination.
     *
     * @param result      the result
     * @param destination the destination
     */
    private fun handleStompDestinationResult(result: String?, destination: String) {
        val subscription = subscriptions[destination]
        subscription?.let {
            val resultHandler = it.resultHandler
            if (resultHandler.resultTypeClass == Unit::class.java || result == null || result == "null")
                resultHandler.deliverNothing()
            else
                resultHandler.deliverResult(result)
        }
    }

    /**
     * Internal method to subscribe to a topic. The subscription is recycled.
     *
     * @param topic   the topic
     * @param handler the result handler of the topic
     * @return the subscription
     */
    private fun subscribeInternal(topic: String, handler: ResultHandler<*>): Subscription {
        val subscriptionId = "" + (subscriptions.size + 1)
        val subscription = Subscription(topic, subscriptionId, handler)
        webSocket.send(StompMessageHelper.buildSubscribeMessage(subscription.topic, subscription.subscriptionId))

        return subscription
    }

    /**
     * It unsubscribes from a topic.
     *
     * @param subscription the subscription
     */
    private fun unsubscribeFrom(subscription: Subscription) {
        println("[Stomp client] Unsubscribing from ${subscription.topic}")
        webSocket.send(StompMessageHelper.buildUnsubscribeMessage(subscription.subscriptionId));
    }

    /**
     * Clears the subscriptions and closes the webSocket connection.
     */
    override fun close() {
        println("[Stomp client] Closing webSocket session")

        subscriptions.values.forEach { unsubscribeFrom(it) }
        subscriptions.clear()

        // indicates a normal closure
        webSocket.close(1000, null)
    }

    /**
     * Generates an UUID for this webSocket client.
     */
    private fun generateClientKey(): String {
        return try {
            val salt = MessageDigest.getInstance("SHA-256")
            salt.update(UUID.randomUUID().toString().toByteArray(StandardCharsets.UTF_8))
            bytesToHex(salt.digest())
        } catch (e: java.lang.Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val HEX_ARRAY = "0123456789abcdef".toByteArray()
        val hexChars = ByteArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars, StandardCharsets.UTF_8)
    }

    /**
     * Special object to wrap a NOP.
     */
    inner class Nothing
}