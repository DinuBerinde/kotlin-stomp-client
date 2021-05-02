package com.dinuberinde.stomp.client.internal.stomp

import com.dinuberinde.stomp.client.internal.Message
import com.google.gson.GsonBuilder


class StompMessageHelper {

    /**
     * Builder class which builds the STOMP messages by command. The following commands are implemented:
     * <ul>
     *     <li>connect - to connect to a webSocket</li>
     *     <li>subscribe - to subscribe to a topic</li>
     *     <li>unsubscribe - to unsubscribe from a topic</li>
     *     <li>send - to send a payload to a destination</li>
     * </ul>
     */
    companion object {
        private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
        private val END = "\u0000"
        private val NEW_LINE = "\n"
        private val DESTINATION = "destination"
        private val ID = "id"
        private val ACK = "ack"
        private val RECEIPT = "receipt"
        private val EMPTY_LINE = ""
        private val DELIMITER = ":"

        /**
         * It parses the current STOMP message and returns a [Message].
         * @return the wrapped STOMP message as [Message]
         */
        @Throws(IllegalStateException::class)
        fun parseStompMessage(stompMessage: String): Message {
            val splitMessage = stompMessage.split(NEW_LINE.toRegex()).toTypedArray()

            if (splitMessage.isEmpty())
                throw IllegalStateException("Did not received any message")

            val command = splitMessage[0]
            val stompHeaders = StompHeaders()
            var body = ""

            var cursor = 1
            for (i in cursor until splitMessage.size) {
                // empty line
                if (splitMessage[i] == EMPTY_LINE) {
                    cursor = i
                    break
                } else {
                    val header: List<String> = splitMessage[i].split(DELIMITER)
                    stompHeaders.add(header[0], header[1])
                }
            }

            for (i in cursor until splitMessage.size) {
                body += splitMessage[i]
            }

            return if (body.isNotEmpty())
                Message(StompCommand.valueOf(command), stompHeaders, body.replace(END,""))
            else
                Message(StompCommand.valueOf(command), stompHeaders)
        }

        fun buildSubscribeMessage(destination: String, id: String): String {
            var headers = ""

            headers += buildHeader(StompCommand.SUBSCRIBE.name)
            headers += buildHeader(DESTINATION, destination)
            headers += buildHeader(ID, id)
            headers += buildHeader(ACK, "auto")
            headers += buildHeader(RECEIPT, "receipt_$destination")

            return "$headers$NEW_LINE$END"
        }

        fun buildUnsubscribeMessage(subscriptionId: String): String {
            var headers = ""

            headers += buildHeader(StompCommand.UNSUBSCRIBE.name)
            headers += buildHeader(ID, subscriptionId)

            return "$headers$NEW_LINE$END"
        }

        fun buildConnectMessage(): String {
            var headers = ""

            headers += buildHeader(StompCommand.CONNECT.name)
            headers += buildHeader("accept-version", "1.0,1.1,2.0")
            headers += buildHeader("host", "stomp.github.org")
            headers += buildHeader("heart-beat", "0,0")

            return "$headers$NEW_LINE$END"
        }

        fun <T> buildSendMessage(destination: String, payload: T?): String {
            val body = if (payload != null) gson.toJson(payload) else ""
            var headers = ""

            headers += buildHeader(StompCommand.SEND.name)
            headers += buildHeader(DESTINATION, destination)

            return "$headers$NEW_LINE$body$NEW_LINE$END"
        }

        private fun buildHeader(key: String, value: String? = null): String {
            return if (value != null) "$key:$value$NEW_LINE" else "$key$NEW_LINE"
        }
    }
}