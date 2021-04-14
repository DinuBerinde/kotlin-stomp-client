package com.dinuberinde.stomp.client.internal

/**
 * Enum class which represents the STOMP protocol commands.
 */
enum class StompCommand {
    SUBSCRIBE,
    UNSUBSCRIBE,
    CONNECT,
    CONNECTED,
    RECEIPT,
    SEND,
    ERROR,
    MESSAGE
}