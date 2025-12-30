package com.example.voice_demo.signaling

data class SignalMessage(
    val type: String,   // "join", "leave", "offer", "answer", "ice", "user-list"
    val from: String,
    val to: String? = null,
    val payload: String? = null,
    val roomId: String? = null
)

