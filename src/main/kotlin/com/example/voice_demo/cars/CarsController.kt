package com.example.voice_demo.cars

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/cars")
class CarController {

    // GET /api/cars
    @GetMapping
    fun getAll(): List<Car> =
        FakeDb.cars.values.toList()

    // GET /api/cars/{id}
    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<Car> {
        val car = FakeDb.cars[id]
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(car)
    }

    // POST /api/cars
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: CreateCar): Car {
        val car = Car(
            id = UUID.randomUUID(),
            make = req.make,
            model = req.model,
            year = req.year,
        )

        FakeDb.cars[car.id] = car
        return car
    }

    // PUT /api/cars/{id}
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody req: UpdateCar
    ): ResponseEntity<Car> {

        val existing = FakeDb.cars[id]
            ?: return ResponseEntity.notFound().build()

        val updated = existing.copy(
            make = req.make,
            model = req.model
        )

        FakeDb.cars[id] = updated
        return ResponseEntity.ok(updated)
    }

    // DELETE /api/cars/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        FakeDb.cars.remove(id)
    }
}
