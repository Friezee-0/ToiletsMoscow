package com.moscow.toilets.importer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToiletRecord {
    private Long id;
    private String title;
    private String address;
    private Double lat;
    private Double lng;
    private String type;
    private Boolean accessible;
    private Double rating;
    @JsonProperty("workingHours")
    private String workingHours;
    private String description;
}
