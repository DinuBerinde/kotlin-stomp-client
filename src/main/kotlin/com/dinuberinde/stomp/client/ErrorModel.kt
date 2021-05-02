package com.dinuberinde.stomp.client

/**
 * The model of an exception.
 */
class ErrorModel(
        /**
         * The message of the exception.
         */
        val message: String,
        /**
         * The fully-qualified name of the class of the exception.
         */
        val exceptionClassName: String
) {


        companion object {

                fun toString(errorModel: ErrorModel): String {
                        return "${errorModel.exceptionClassName} {$errorModel.message}"
                }
        }
}