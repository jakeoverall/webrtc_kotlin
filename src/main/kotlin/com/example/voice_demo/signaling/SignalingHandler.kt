package com.example.voice_demo.signaling

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.ConcurrentHashMap

data class RoomUser(
    val username: String,
    val session: WebSocketSession
)

@Component
class SignalingHandler : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(SignalingHandler::class.java)
    private val mapper = jacksonObjectMapper()
    private val rooms = ConcurrentHashMap<String, MutableList<RoomUser>>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val payload = mapper.readTree(message.payload)

            val type = payload["type"]?.asString()
            val roomId = payload["roomId"]?.asString()
            val from = payload["from"]?.asString()

            if (type == null || roomId == null) {
                sendError(session, "Missing required fields: type or roomId")
                return
            }

            when (type) {
                "join" -> {
                    if (from == null) {
                        sendError(session, "Missing required field: from")
                        return
                    }
                    handleJoin(session, roomId, from)
                }

                "offer", "answer", "ice" -> {
                    relay(roomId, session, payload)
                }

                else -> {
                    sendError(session, "Unknown message type: $type")
                }
            }
        } catch (ex: Exception) {
            // Never let an exception kill the socket
            logger.error("WebSocket error from session ${session.id}", ex)

            if (session.isOpen) {
                sendError(session, "Internal server error")
            }
        }
    }

    private fun sendError(session: WebSocketSession, message: String) {
        if (!session.isOpen) return

        session.sendMessage(
            TextMessage(
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "error",
                        "message" to message
                    )
                )
            )
        )
    }


    private fun handleJoin(
        session: WebSocketSession,
        roomId: String,
        username: String
    ) {
        val room = rooms.computeIfAbsent(roomId) { mutableListOf() }

        if (room.any { it.username == username }) {
            sendError(session, "User already exists")
            return
        }

        if (room.size >= 2) {
            sendError(session, "Room already exists and is Full")
            return
        }

        room.add(RoomUser(username, session))

        val role = if (room.size == 1) "caller" else "callee"

        // Notify joiner
        session.sendMessage(
            TextMessage(
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "joined",
                        "role" to role,
                        "users" to room.map { it.username }
                    )
                )
            )
        )

        // Broadcast updated user list
        broadcastRoomUsers(roomId)

        room
            .filter { it.session.id != session.id }
            .forEach {
                it.session.sendMessage(
                    TextMessage(
                        mapper.writeValueAsString(
                            mapOf(
                                "type" to "peer-joined",
                                "roomId" to roomId
                            )
                        )
                    )
                )
            }
    }

    private fun broadcastRoomUsers(roomId: String) {
        val room = rooms[roomId] ?: return
        val users = room.map { it.username }

        val msg = TextMessage(
            mapper.writeValueAsString(
                mapOf(
                    "type" to "user-list",
                    "users" to users
                )
            )
        )

        room.forEach {
            if (it.session.isOpen) {
                it.session.sendMessage(msg)
            }
        }
    }

    private fun relay(roomId: String, sender: WebSocketSession, payload: JsonNode) {
        rooms[roomId]?.forEach {
            if (
                it.session != sender &&
                it.session.isOpen
            ) {
                it.session.sendMessage(TextMessage(payload.toString()))
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        rooms.forEach { (roomId, users) ->

            val disconnectedUser = users.firstOrNull {
                it.session.id == session.id
            }

            if (disconnectedUser != null) {

                users.remove(disconnectedUser)

                // Notify remaining peers
                users.forEach { user ->
                    if (user.session.isOpen) {
                        user.session.sendMessage(
                            TextMessage(
                                mapper.writeValueAsString(
                                    mapOf(
                                        "type" to "peer-left",
                                        "roomId" to roomId
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }

        // Remove empty rooms
        rooms.entries.removeIf { it.value.isEmpty() }
    }

}
