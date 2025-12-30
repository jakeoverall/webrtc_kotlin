package com.example.voice_demo.service

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Service
class CallRegistry {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun register(userId: String, session: WebSocketSession) {
        sessions[userId] = session
    }

    fun get(userId: String): WebSocketSession? = sessions[userId]

    fun remove(userId: String) {
        sessions.remove(userId)
    }

    fun getAllUsers(): Map<String, WebSocketSession> = sessions.toMap()

    fun getOtherUsers(excludeUser: String): Map<String, WebSocketSession> =
        sessions.filterKeys { it != excludeUser }
}

