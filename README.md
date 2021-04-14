# KotlinStompClient

## Overview
A thread safe webSocket client build on top of the OkHttp library
 which implements the STOMP protocol [https://stomp.github.io/index.html].
It exchanges only json messages and uses the GSON library to 
serialize and deserialize the json.

## Build
- Normal build (without tests)      
  `gradlew clean build -x test`
- Fat Jar     
  `gradlew shadowJar` which includes the **okhttp3** and **gson** library

## Tests
In order to execute the tests, be sure to download and launch 
locally the server webSocket implementation https://github.com/DinuBerinde/SpringStompWebSockets  