package com.moscow.toilets.backend.controller;

import com.moscow.toilets.backend.dto.ToiletDto;
import com.moscow.toilets.backend.service.ToiletService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/toilets")
@Validated
public class ToiletController {

    @Autowired
    private ToiletService toiletService;

    /**
     * GET /api/toilets/nearby?latitude=55.75&longitude=37.62&radius=1000
     * Returns toilets within radius metres of the given point, sorted by distance.
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<ToiletDto>> getNearby(
            @RequestParam
            @DecimalMin(value = "-90",  message = "latitude must be >= -90")
            @DecimalMax(value = "90",   message = "latitude must be <= 90")
            double latitude,

            @RequestParam
            @DecimalMin(value = "-180", message = "longitude must be >= -180")
            @DecimalMax(value = "180",  message = "longitude must be <= 180")
            double longitude,

            @RequestParam(defaultValue = "1000")
            @Min(value = 1,     message = "radius must be >= 1")
            @Max(value = 50000, message = "radius must be <= 50000")
            int radius) {

        return ResponseEntity.ok(toiletService.findNearby(latitude, longitude, (double) radius));
    }

    /** GET /api/toilets — full list, sorted by rating */
    @GetMapping
    public ResponseEntity<List<ToiletDto>> getAll() {
        return ResponseEntity.ok(toiletService.findAll());
    }

    /** GET /api/toilets/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ToiletDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(toiletService.findById(id));
    }
}
