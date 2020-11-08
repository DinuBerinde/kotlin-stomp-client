import io.hotmoka.network.thin.client.webSockets.StompClient
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail


class StompClientTest {
    private val endpoint = "localhost:8080/"

    @Test fun stompClientTopic() {

        val completableFuture = CompletableFuture<Boolean>()
        val stompClient = StompClient(endpoint)
        stompClient.use { client ->

            client.connect(
                {
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

                }, {
                    fail("Connection failed")
                }
            )

            assertEquals(true, completableFuture.get(4L, TimeUnit.SECONDS))
        }
    }

    @Test fun stompClientEchoMessage() {

        val completableFuture = CompletableFuture<Boolean>()
        val stompClient = StompClient(endpoint)
        stompClient.use { client ->

            client.connect(
                {
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

                }, {
                    fail("Connection failed")
                }
            )

            assertEquals(true, completableFuture.get(4L, TimeUnit.SECONDS))
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
}