import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

/**
 * Session manager for WebRTC signaling with enhanced logging and bug fixes
 */
object SessionManager {
    private val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    // Structure to store information about rooms
    data class Room(
        val clientSessions: MutableMap<UUID, DefaultWebSocketServerSession> = mutableMapOf(),
        var sessionState: WebRTCSessionState = WebRTCSessionState.Impossible
    )

    // Store ALL client sessions regardless of room
    private val allSessions = mutableMapOf<UUID, DefaultWebSocketServerSession>()

    // Map of connection codes to room information
    private val rooms = mutableMapOf<String, Room>()

    // Track which room each client belongs to
    private val clientRooms = mutableMapOf<UUID, String>()

    // Generate a random 6-character connection code
    fun generateConnectionCode(): String {
        val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed similar-looking characters
        var code: String
        do {
            code = (1..6)
                .map { allowedChars.random() }
                .joinToString("")
        } while (rooms.containsKey(code))

        // Create a new empty room
        rooms[code] = Room()
        println("🔑 Generated new connection code: $code")
        return code
    }

    fun onSessionStarted(sessionId: UUID, session: DefaultWebSocketServerSession) {
        println("🟢 New session started: $sessionId")
        sessionManagerScope.launch {
            mutex.withLock {
                // Store the session immediately
                allSessions[sessionId] = session
                println("💾 Stored session $sessionId in global sessions map")

                // Wait for the client to send CONNECTION message with room code
                session.send("WAITING_FOR_CONNECTION_CODE")
                println("⏳ Waiting for connection code from session: $sessionId")
            }
        }
    }

    fun onConnectionRequest(sessionId: UUID, session: DefaultWebSocketServerSession, connectionCode: String) {
        println("🔌 Connection request received: sessionId=$sessionId, code=$connectionCode")
        sessionManagerScope.launch {
            mutex.withLock {
                // Check if room exists
                if (!rooms.containsKey(connectionCode)) {
                    // Create the room if it doesn't exist
                    println("🏠 Creating new room for code: $connectionCode")
                    rooms[connectionCode] = Room()
                } else {
                    println("🏠 Room already exists for code: $connectionCode with ${rooms[connectionCode]?.clientSessions?.size ?: 0} clients")
                }

                val room = rooms[connectionCode]!!

                // If room is full (already has 2 clients)
                if (room.clientSessions.size >= 2) {
                    println("❌ Room $connectionCode is full, rejecting client $sessionId")
                    sessionManagerScope.launch(NonCancellable) {
                        session.send("${MessageType.CONNECTION_RESPONSE} ROOM_FULL")
                        session.send(Frame.Close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Room is full")))
                    }
                    return@launch
                }

                // Add client to room
                room.clientSessions[sessionId] = session
                clientRooms[sessionId] = connectionCode

                println("✅ Client $sessionId added to room $connectionCode. Room now has ${room.clientSessions.size} clients")

                // Send confirmation to client
                session.send("${MessageType.CONNECTION_RESPONSE} CONNECTED $connectionCode")
                println("📩 Sent connection confirmation to client $sessionId")

                // Update room state if we now have 2 clients
                if (room.clientSessions.size > 1) {
                    room.sessionState = WebRTCSessionState.Ready
                    println("🟩 Room $connectionCode is now READY with ${room.clientSessions.size} clients")
                } else {
                    println("⌛ Room $connectionCode is waiting for more clients (currently ${room.clientSessions.size})")
                }

                // Notify clients about state update
                notifyRoomAboutStateUpdate(connectionCode)
                println("📢 Notified all clients in room $connectionCode about state update to ${room.sessionState}")

                // Debug: List all clients in the room
                println("👥 Clients in room $connectionCode:")
                room.clientSessions.keys.forEachIndexed { index, uuid ->
                    println("  Client ${index+1}: $uuid")
                }
            }
        }
    }

    fun onMessage(sessionId: UUID, message: String) {
        println("📥 Message received from $sessionId: $message")

        when {
            message.startsWith(MessageType.STATE.toString(), true) -> {
                println("⚡ STATE request from client $sessionId")
                handleState(sessionId)
            }
            message.startsWith(MessageType.CONNECTION.toString(), true) -> {
                println("⚡ CONNECTION request from client $sessionId")
                handleConnection(sessionId, message)
            }
            message.startsWith(MessageType.START_CALL.toString(), true) -> {
                println("⚡ START_CALL request from client $sessionId")
                handleStartCall(sessionId)
            }
            message.startsWith(MessageType.OFFER.toString(), true) -> {
                println("⚡ OFFER from client $sessionId")
                handleOffer(sessionId, message)
            }
            message.startsWith(MessageType.ANSWER.toString(), true) -> {
                println("⚡ ANSWER from client $sessionId")
                handleAnswer(sessionId, message)
            }
            message.startsWith(MessageType.ICE.toString(), true) -> {
                println("⚡ ICE candidate from client $sessionId")
                handleIce(sessionId, message)
            }
            else -> {
                println("❓ Unknown message type from client $sessionId: $message")
            }
        }
    }

    private fun handleConnection(sessionId: UUID, message: String) {
        val parts = message.split(" ", limit = 2)
        if (parts.size < 2) {
            println("❌ Invalid CONNECTION message format from $sessionId: $message")
            return
        }

        val connectionCode = parts[1].trim()
        println("🔍 Parsed connection code from client $sessionId: $connectionCode")

        // Get session from allSessions instead of looking it up through clientRooms
        val session = allSessions[sessionId]
        if (session == null) {
            println("❌ Could not find session for client $sessionId in allSessions map")
            return
        }

        onConnectionRequest(sessionId, session, connectionCode)
    }

    private fun handleStartCall(sessionId: UUID) {
        val roomCode = clientRooms[sessionId]
        if (roomCode == null) {
            println("❌ Client $sessionId sent START_CALL but is not in any room")
            return
        }

        val room = rooms[roomCode]
        if (room == null) {
            println("❌ Client $sessionId sent START_CALL but room $roomCode doesn't exist")
            return
        }

        println("📣 Forwarding START_CALL from client $sessionId to other clients in room $roomCode")

        // Make sure room state is Active
        if (room.sessionState != WebRTCSessionState.Active) {
            room.sessionState = WebRTCSessionState.Active
            println("🔄 Setting room $roomCode state to ACTIVE from START_CALL")
            notifyRoomAboutStateUpdate(roomCode)
        }

        // Forward START_CALL to all other clients in the room
        room.clientSessions.forEach { (clientId, session) ->
            if (clientId != sessionId) {  // Skip the sender
                println("📤 Forwarding START_CALL to client $clientId")
                session.send("${MessageType.START_CALL}")
            }
        }
    }

    private fun handleState(sessionId: UUID) {
        sessionManagerScope.launch {
            val roomCode = clientRooms[sessionId]
            if (roomCode == null) {
                println("ℹ️ Client $sessionId requested state but is not in any room")

                // Send "Impossible" state if client is not in a room yet
                val session = allSessions[sessionId]
                if (session != null) {
                    println("📤 Sending default IMPOSSIBLE state to client not in a room yet")
                    session.send("${MessageType.STATE} ${WebRTCSessionState.Impossible}")
                } else {
                    println("❌ Client $sessionId not found in allSessions map")
                }
                return@launch
            }

            val room = rooms[roomCode]
            if (room == null) {
                println("❌ Client $sessionId requested state but room $roomCode doesn't exist")
                return@launch
            }

            val session = room.clientSessions[sessionId]
            if (session == null) {
                println("❌ Client $sessionId requested state but session not found in room $roomCode")
                return@launch
            }

            println("📤 Sending state ${room.sessionState} to client $sessionId in room $roomCode")
            session.send("${MessageType.STATE} ${room.sessionState}")
        }
    }

    private fun handleOffer(sessionId: UUID, message: String) {
        val roomCode = clientRooms[sessionId]
        if (roomCode == null) {
            println("❌ Client $sessionId sent offer but is not in any room")
            return
        }

        val room = rooms[roomCode]
        if (room == null) {
            println("❌ Client $sessionId sent offer but room $roomCode doesn't exist")
            return
        }

        if (room.sessionState != WebRTCSessionState.Ready) {
            println("❌ Client $sessionId sent offer but room state is ${room.sessionState}, expected READY")
            return
        }

        room.sessionState = WebRTCSessionState.Creating
        println("🔄 Room $roomCode state changed to CREATING from offer by client $sessionId")

        notifyRoomAboutStateUpdate(roomCode)

        // Send offer to the other client in the room
        val otherClients = room.clientSessions.filterKeys { it != sessionId }
        if (otherClients.isEmpty()) {
            println("❌ No other clients in room $roomCode to send offer to")
            return
        }

        val otherClient = otherClients.values.first()
        println("📤 Forwarding offer from client $sessionId to other client in room $roomCode")
        otherClient.send(message)
    }

    private fun handleAnswer(sessionId: UUID, message: String) {
        val roomCode = clientRooms[sessionId]
        if (roomCode == null) {
            println("❌ Client $sessionId sent answer but is not in any room")
            return
        }

        val room = rooms[roomCode]
        if (room == null) {
            println("❌ Client $sessionId sent answer but room $roomCode doesn't exist")
            return
        }

        if (room.sessionState != WebRTCSessionState.Creating) {
            println("❌ Client $sessionId sent answer but room state is ${room.sessionState}, expected CREATING")
            return
        }

        println("📤 Forwarding answer from client $sessionId to other client in room $roomCode")

        // Send answer to the other client in the room
        val otherClients = room.clientSessions.filterKeys { it != sessionId }
        if (otherClients.isEmpty()) {
            println("❌ No other clients in room $roomCode to send answer to")
            return
        }

        val otherClient = otherClients.values.first()
        otherClient.send(message)

        room.sessionState = WebRTCSessionState.Active
        println("✅ Room $roomCode state changed to ACTIVE after answer from client $sessionId")

        notifyRoomAboutStateUpdate(roomCode)
    }

    private fun handleIce(sessionId: UUID, message: String) {
        val roomCode = clientRooms[sessionId]
        if (roomCode == null) {
            println("❌ Client $sessionId sent ICE candidate but is not in any room")
            return
        }

        val room = rooms[roomCode]
        if (room == null) {
            println("❌ Client $sessionId sent ICE candidate but room $roomCode doesn't exist")
            return
        }

        println("📤 Forwarding ICE candidate from client $sessionId to other client in room $roomCode")

        // Send ICE to the other client in the room
        val otherClients = room.clientSessions.filterKeys { it != sessionId }
        if (otherClients.isEmpty()) {
            println("❌ No other clients in room $roomCode to send ICE candidate to")
            return
        }

        val otherClient = otherClients.values.first()
        otherClient.send(message)
    }

    fun onSessionClose(sessionId: UUID) {
        println("🔴 Session closed: $sessionId")
        sessionManagerScope.launch {
            mutex.withLock {
                // Remove from allSessions first
                allSessions.remove(sessionId)
                println("🔄 Removed client $sessionId from global sessions")

                val roomCode = clientRooms[sessionId]
                if (roomCode == null) {
                    println("ℹ️ Client $sessionId closed but was not in any room")
                    return@launch
                }

                println("🏠 Client $sessionId was in room $roomCode, updating room state")

                // Remove client from room
                val room = rooms[roomCode] ?: return@launch
                room.clientSessions.remove(sessionId)
                clientRooms.remove(sessionId)

                println("👥 Room $roomCode now has ${room.clientSessions.size} clients after removal of $sessionId")

                // Update room state
                room.sessionState = WebRTCSessionState.Impossible
                println("🔄 Room $roomCode state changed to IMPOSSIBLE after client $sessionId left")

                // Notify other clients
                notifyRoomAboutStateUpdate(roomCode)
                println("📢 Notified remaining clients in room $roomCode about state update")

                // Clean up empty rooms after some time
                if (room.clientSessions.isEmpty()) {
                    println("⏳ Room $roomCode is empty, scheduling cleanup in 60 seconds")
                    delay(60000) // Wait for 1 minute before removing empty room
                    if (rooms[roomCode]?.clientSessions?.isEmpty() == true) {
                        rooms.remove(roomCode)
                        println("🧹 Removed empty room: $roomCode after timeout")
                    } else {
                        println("ℹ️ Room $roomCode is no longer empty, cleanup cancelled")
                    }
                }
            }
        }
    }

    enum class WebRTCSessionState {
        Active, // Offer and Answer messages has been sent
        Creating, // Creating session, offer has been sent
        Ready, // Both clients available and ready to initiate session
        Impossible // We have less than two clients
    }

    enum class MessageType {
        STATE,
        CONNECTION,
        CONNECTION_RESPONSE,
        START_CALL,
        OFFER,
        ANSWER,
        ICE
    }

    private fun notifyRoomAboutStateUpdate(roomCode: String) {
        val room = rooms[roomCode] ?: return
        println("📢 Notifying all clients in room $roomCode about state: ${room.sessionState}")

        room.clientSessions.forEach { (clientId, session) ->
            println("📤 Sending state update ${room.sessionState} to client $clientId")
            session.send("${MessageType.STATE} ${room.sessionState}")
        }
    }

    private fun DefaultWebSocketServerSession.send(message: String) {
        sessionManagerScope.launch {
            try {
                println("📤 Sending message: $message")
                this@send.send(Frame.Text(message))
            } catch (e: Exception) {
                println("❌ Error sending message: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}