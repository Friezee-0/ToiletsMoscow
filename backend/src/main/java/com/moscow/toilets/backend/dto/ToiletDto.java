package com.moscow.toilets.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToiletDto {
    private Long id;
    private String title;
    private String address;
    private Double lat;
    private Double lng;
    private String type;
    private Boolean accessible;
    private Double rating;
    private Integer ratingCount;
    private String workingHours;
    private String description;
    private Double distanceMeters;
}
