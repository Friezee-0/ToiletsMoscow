package com.moscow.toilets;

/** Модель туалета на клиенте. Маппится из JSON ответа бэкенда через Gson. */
public class Toilet {
    public long id;
    public String title;
    public String address;
    public double lat;
    public double lng;
    public String type;          // FREE / PAID / TROIKA / MALL
    public Boolean accessible;
    public double rating;
    public String workingHours;
    public Double distanceMeters; // приходит из /nearby
}
