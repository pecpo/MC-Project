import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    println("Starting WebRTC signaling server with connection code support")

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            println("📄 Received request to root endpoint")
            call.respond("Hello from WebRTC signaling server with connection code support")
        }

        // Generate new connection code
        get("/generate-code") {
            println("🎲 Received request to generate a new connection code")
            val code = SessionManager.generateConnectionCode()
            println("🔑 Generated code: $code")
            call.respond(code)
        }

        webSocket("/rtc") {
            val sessionID = UUID.randomUUID()
            println("🔌 New WebSocket connection: $sessionID")

            try {
                println("Initializing session: $sessionID")
                SessionManager.onSessionStarted(sessionID, this)

                println("Starting to listen for messages from: $sessionID")
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            println("Received text message from $sessionID: ${text.take(50)}${if (text.length > 50) "..." else ""}")
                            SessionManager.onMessage(sessionID, text)
                        }
                        is Frame.Binary -> {
                            println("Received binary message from $sessionID (${frame.data.size} bytes)")
                        }
                        is Frame.Close -> {
                            println("Received close frame from $sessionID with reason: ${frame.readReason()}")
                        }
                        is Frame.Ping -> {
                            println("Received ping from $sessionID")
                        }
                        is Frame.Pong -> {
                            println("Received pong from $sessionID")
                        }
                        else -> {
                            println("Received unknown frame type from $sessionID: ${frame::class.simpleName}")
                        }
                    }
                }
                println("🔄 Exiting incoming loop, closing session: $sessionID")
                SessionManager.onSessionClose(sessionID)
            } catch (e: ClosedReceiveChannelException) {
                println("WebSocket closed for session $sessionID: ${e.message}")
                SessionManager.onSessionClose(sessionID)
            } catch (e: Throwable) {
                println("Error in WebSocket for session $sessionID: ${e.message}")
                e.printStackTrace()
                SessionManager.onSessionClose(sessionID)
            } finally {
                println("Cleanup for session $sessionID completed")
            }
        }
    }

    println("✅ WebRTC signaling server configuration complete")
}