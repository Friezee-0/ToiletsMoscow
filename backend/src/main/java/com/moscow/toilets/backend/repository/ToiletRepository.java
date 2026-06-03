package com.moscow.toilets.backend.repository;

import com.moscow.toilets.backend.dto.ToiletDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ToiletRepository {

    @Autowired
    private JdbcTemplate jdbc;

    private final RowMapper<ToiletDto> toiletMapper = (rs, rowNum) -> ToiletDto.builder()
            .id(rs.getLong("id"))
            .title(rs.getString("title"))
            .address(rs.getString("address"))
            .lat(rs.getDouble("lat"))
            .lng(rs.getDouble("lng"))
            .type(rs.getString("type"))
            .accessible(rs.getBoolean("accessible"))
            .rating(rs.getDouble("rating"))
            .ratingCount(rs.getInt("rating_count"))
            .workingHours(rs.getString("working_hours"))
            .description(rs.getString("description"))
            .distanceMeters(rs.getDouble("distance_meters"))
            .build();

    /**
     * Finds toilets within radiusMeters of (lat, lng), ordered by distance.
     * Uses PostGIS ST_DWithin for spatial index utilization and ST_Distance for ordering.
     */
    public List<ToiletDto> findNearby(double lat, double lng, double radiusMeters) {
        // Note: ST_MakePoint(lng, lat) — PostGIS uses (x=longitude, y=latitude)
        String sql = """
                SELECT
                    id, title, address, lat, lng, type, accessible,
                    rating, rating_count, working_hours, description,
                    ROUND(ST_Distance(
                        location,
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                    )::numeric, 1) AS distance_meters
                FROM toilets
                WHERE ST_DWithin(
                    location,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?
                )
                ORDER BY distance_meters
                LIMIT 200
                """;
        return jdbc.query(sql, toiletMapper, lng, lat, lng, lat, radiusMeters);
    }

    public List<ToiletDto> findAll() {
        String sql = """
                SELECT id, title, address, lat, lng, type, accessible,
                       rating, rating_count, working_hours, description,
                       0.0 AS distance_meters
                FROM toilets
                ORDER BY rating DESC
                """;
        return jdbc.query(sql, toiletMapper);
    }

    public ToiletDto findById(long id) {
        String sql = """
                SELECT id, title, address, lat, lng, type, accessible,
                       rating, rating_count, working_hours, description,
                       0.0 AS distance_meters
                FROM toilets WHERE id = ?
                """;
        List<ToiletDto> results = jdbc.query(sql, toiletMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }
}
