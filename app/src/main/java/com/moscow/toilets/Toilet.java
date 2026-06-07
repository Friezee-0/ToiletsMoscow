package com.moscow.toilets;

import java.io.Serializable;

/** Модель туалета. Serializable для передачи через Intent. */
public class Toilet implements Serializable {
    public long id;
    public String title;
    public String address;
    public double lat;
    public double lng;
    public String type;          // FREE / PAID
    public boolean mobile;       // true = передвижной модуль, false = стационарный
    public Boolean accessible;
    public double rating;
    public String workingHours;
    public Double distanceMeters;
}
