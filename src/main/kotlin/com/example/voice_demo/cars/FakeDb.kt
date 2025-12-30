package com.example.voice_demo.cars

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FakeDb {
    val cars: MutableMap<UUID, Car> = ConcurrentHashMap()
}
