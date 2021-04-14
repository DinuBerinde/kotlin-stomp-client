
import com.dinuberinde.stomp.client.StompClient
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail


class StompClientTest {
    private val endpoint = "ws://localhost:8080/"

    @Test fun stompClientTopic() {

        val completableFuture = CompletableFuture<Boolean>()
        val stompClient = StompClient(endpoint)
        stompClient.use { client ->

            client.connect(
                {
                    CompletableFuture.runAsync {
                        client.subscribeTo("/topic/events", Event::class.java, { result, error ->

                            when {
                                error != null -> {
                                    fail("unexpected error")
                                }
                                result != null -> {
                                    completableFuture.complete("testing stomp client" == result.name)
                                }
                                else -> {
                                    fail("unexpected payload")
                                }
                            }
                        }, {
                            client.sendTo("/events/add", Event("testing stomp client"))
                        })
                    }

                }, {
                    fail("Connection failed")
                }
            )

            assertEquals(true, completableFuture.get(4, TimeUnit.SECONDS))
        }
    }

    @Test fun stompClientEchoMessage() {

        val completableFuture = CompletableFuture<Boolean>()
        val stompClient = StompClient(endpoint)
        stompClient.use { client ->

            client.connect(
                {

                    CompletableFuture.runAsync {
                        val topic = "/user/${client.getClientKey()}/echo/message"
                        client.subscribeTo(topic, EchoModel::class.java, { result, error ->

                            when {
                                error != null -> {
                                    fail("unexpected error")
                                }
                                result != null -> {
                                    completableFuture.complete("hello world" == result.message)
                                }
                                else -> {
                                    fail("unexpected payload")
                                }
                            }
                        }, {
                            client.sendTo("/echo/message", EchoModel("hello world"))
                        })
                    }

                }, {
                    fail("Connection failed")
                }
            )

            assertEquals(true, completableFuture.get(4L, TimeUnit.SECONDS))
        }
    }


    @Test fun concurrentlySendEchoMessages() {
        val numOfThreads = 40
        val pool = Executors.newCachedThreadPool()
        val completableFuture = CompletableFuture<Boolean>()
        val results = HashMap<String, Boolean>(numOfThreads)

        val stompClient = StompClient(endpoint)
        stompClient.use { client ->

            stompClient.connect({

                val delayedTask = CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS)
                CompletableFuture.runAsync {

                    // subscribe
                    val topic = "/user/${stompClient.getClientKey()}/echo/message"
                    stompClient.subscribeTo(topic, EchoModel::class.java, { result, error ->

                        when {
                            error != null -> {
                                fail("unexpected error")
                            }
                            result != null -> {
                                results[result.message] = true
                            }
                            else -> {
                                fail("unexpected payload")
                            }
                        }

                    }, {

                        // build workers
                        val workers = arrayListOf<StompClientSendWorker>()
                        for (i in 0..numOfThreads) {
                            workers.add(StompClientSendWorker(EchoModel("hello world $i"), pool, client))
                            results["hello world $i"] = false
                        }

                        // concurrent requests
                        workers.parallelStream().forEach{ worker -> worker.call() }
                        pool.shutdown()
                    })

                }.thenRunAsync(
                    {

                        // check results
                        completableFuture.complete(results.values.parallelStream().allMatch { ok: Boolean? -> ok!! })
                    },
                    delayedTask
                )

            })

            assertEquals(true, completableFuture.get(4, TimeUnit.SECONDS))
        }
    }



    /**
     * Class model used for testing.
     */
    class Event(val name: String)

    /**
     * Class model used for testing.
     */
    class EchoModel(val message: String)


    /**
     * STOMP client send worker to send messages to a destination.
     */
    class StompClientSendWorker(
        private val echoModel: EchoModel,
        private val pool: ExecutorService,
        private val stompClient: StompClient
    ) {

        fun call() {
            CompletableFuture.runAsync({
                stompClient.sendTo("/echo/message", echoModel)
            }, pool)
        }
    }
}