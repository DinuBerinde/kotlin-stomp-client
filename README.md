# KotlinStompClient

## Overview
A synchronous and asynchronous thread safe webSocket client build on top of the OkHttp library
 which implements the STOMP protocol [https://stomp.github.io/index.html].

## Build
- Normal build (without tests)      
  `gradlew clean build -x test`
- Fat Jar     
  `gradlew shadowJar` which includes the **okhttp3**, **gson** and the **kotlin** libraries


## Usage

#### Asynchronous

```kotlin
// create an instance of the stomp client
val stompClient = StompClient(endpoint)

// connect to the endpoint
stompClient.connect()

// subscribe to a topic an suppose to receive a result of type Event
stompClient.subscribeToTopic("/topic/events", Event::class.java) { result, error -> 
      
        when {
            error != null -> {
                println("Got an error: ${error.message}")
            }
            result != null-> {
                println("Got result: ${result.message}")
            }
            else -> {
                println("Received an empty result")
            }
        }
}

// close the stomp client
stompClient.close()
```


#### Synchronous

```kotlin
// create an instance of the stomp client
val stompClient = StompClient(endpoint)

// connect to the endpoint
stompClient.connect()

// send a message and wait for the result from the destination topic 
val result: EchoModel? = stompClient.send("/echo/message", EchoModel::class.java, EchoModel("hello world"))
println("Got result: ${result?.message}")

// close the stomp client
stompClient.close()
```

## Tests
In order to execute the tests, be sure to download and launch 
locally the webSocket server implementation https://github.com/DinuBerinde/SpringStompWebSockets  