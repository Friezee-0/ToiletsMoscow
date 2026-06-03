package com.moscow.toilets.backend.service;

import com.moscow.toilets.backend.dto.ToiletDto;
import com.moscow.toilets.backend.exception.ValidationException;
import com.moscow.toilets.backend.repository.ToiletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToiletService {

    private static final double MAX_RADIUS_METERS = 50_000;
    private static final double DEFAULT_RADIUS_METERS = 1_000;

    @Autowired
    private ToiletRepository toiletRepository;

    public List<ToiletDto> findNearby(double latitude, double longitude, Double radiusMeters) {
        double radius = radiusMeters != null ? radiusMeters : DEFAULT_RADIUS_METERS;

        if (latitude < -90 || latitude > 90) {
            throw new ValidationException("latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new ValidationException("longitude must be between -180 and 180");
        }
        if (radius <= 0 || radius > MAX_RADIUS_METERS) {
            throw new ValidationException("radius must be between 1 and " + (int) MAX_RADIUS_METERS);
        }

        return toiletRepository.findNearby(latitude, longitude, radius);
    }

    public List<ToiletDto> findAll() {
        return toiletRepository.findAll();
    }

    public ToiletDto findById(long id) {
        ToiletDto dto = toiletRepository.findById(id);
        if (dto == null) {
            throw new ValidationException("Туалет с id=" + id + " не найден");
        }
        return dto;
    }
}
