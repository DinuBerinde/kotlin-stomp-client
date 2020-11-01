import io.hotmoka.network.thin.client.webSockets.StompClient
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask
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
                    client.subscribeTo("/topic/events", Event::class.java) { result, error ->

                        when {
                            error != null -> {
                                fail("unexpected error")
                            }
                            result != null -> {
                                assertEquals("testing stomp client", result.name)

                                completableFuture.complete(true)
                            }
                            else -> {
                                fail("unexpected payload")
                            }
                        }
                    }


                    Timer().schedule(timerTask {
                        client.sendTo("/events/add", Event("testing stomp client"))
                    }, 2000)

                }, {
                    fail("Connection failed")
                }
            )

            completableFuture.get(4L, TimeUnit.SECONDS)
        }
    }

    /**
     * Class model used for testing.
     */
    class Event(val name: String)
}