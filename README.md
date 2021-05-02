# KotlinStompClient

## Overview
A thread safe webSocket client build on top of the OkHttp library
 which implements the STOMP protocol [https://stomp.github.io/index.html].

## Build
- Normal build (without tests)      
  `gradlew clean build -x test`
- Fat Jar     
  `gradlew shadowJar` which includes the **okhttp3**, **gson** and the **kotlin** libraries

## Tests
In order to execute the tests, be sure to download and launch 
locally the webSocket server implementation https://github.com/DinuBerinde/SpringStompWebSockets  