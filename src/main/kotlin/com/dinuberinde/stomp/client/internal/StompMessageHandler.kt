package com.dinuberinde.stomp.client.internal

import com.google.gson.Gson
import com.dinuberinde.stomp.client.exceptions.InternalFailureException
import com.dinuberinde.stomp.client.models.ErrorModel
import java.util.function.BiConsumer
import kotlin.jvm.Throws

/**
 * This is a handler for a STOMP result subscription.
 */
class ResultHandler<T>(val handler: BiConsumer<T?, ErrorModel?>, val resultTypeClass: Class<T>) {

    /**
     * Yields the model <T> of the STOMP message payload from the json representation.
     * @param payload the json representation of the payload
     * @param gson the GSON instance which handles the deserialization of the json
     * @return the model <T>
     */
    @Throws(InternalFailureException::class)
    fun <T> toModel(payload: String, gson: Gson): T {
        try {
            return gson.fromJson<T>(payload, resultTypeClass)
        } catch (e: Exception) {
            throw InternalFailureException("Error deserializing model of type ${resultTypeClass.name}")
        }
    }
}