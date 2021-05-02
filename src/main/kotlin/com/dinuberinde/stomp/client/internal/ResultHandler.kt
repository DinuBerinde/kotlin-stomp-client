package com.dinuberinde.stomp.client.internal

import com.dinuberinde.stomp.client.ErrorModel
import com.dinuberinde.stomp.client.exceptions.InternalFailureException
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Abstract class to handle the result published by a topic.
 * @param <T> the result type
 */
abstract class ResultHandler<T>(val resultTypeClass: Class<T>) {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()


    /**
     * Yields the model <T> of the STOMP message from the json representation.
     *
     * @param payload the json
     * @return the model <T>
     */
    @Throws(InternalFailureException::class)
    fun <T> toModel(payload: String): T {
        try {
            return gson.fromJson<T>(payload, resultTypeClass)
        } catch (e: Exception) {
            throw InternalFailureException("Error deserializing model of type ${resultTypeClass.name}")
        }
    }

    /**
     * It delivers the result of a parsed STOMP message response.
     * @param result the result as JSON.
     */
    abstract fun deliverResult(result: String)

    /**
     * It delivers an [ErrorModel] which wraps an error.
     * @param errorModel the error model
     */
    abstract fun deliverError(errorModel: ErrorModel)

    /**
     * Special method to deliver a NOP.
     */
    abstract fun deliverNothing()
}