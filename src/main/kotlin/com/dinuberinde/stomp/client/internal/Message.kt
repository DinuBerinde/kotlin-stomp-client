package com.dinuberinde.stomp.client.internal

import com.dinuberinde.stomp.client.internal.stomp.StompCommand
import com.dinuberinde.stomp.client.internal.stomp.StompHeaders

/**
 * It represents a STOMP message, a class which holds the STOMP command,
 * the STOMP headers and the STOMP payload.
 */
data class Message(
    val command: StompCommand,
    val headers: StompHeaders,
    val payload: String? = null
)