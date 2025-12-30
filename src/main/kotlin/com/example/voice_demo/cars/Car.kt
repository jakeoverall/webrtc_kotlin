package com.example.voice_demo.cars

import java.util.UUID

data class Car(
    val id: UUID,
    val make: String,
    val model: String,
    val year: Int,
)

data class CreateCar(
    val model: String,
    val make: String,
    val year: Int,
)

data class UpdateCar(
    val model: String,
    val make: String,
    val year: Int,
)
