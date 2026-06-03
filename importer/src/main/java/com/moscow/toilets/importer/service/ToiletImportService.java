package com.moscow.toilets.importer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moscow.toilets.importer.model.ToiletRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ToiletImportService {

    private static final Logger log = LoggerFactory.getLogger(ToiletImportService.class);
    private static final int BATCH_SIZE = 50;
    private static final Set<String> VALID_TYPES = Set.of("FREE", "PAID", "TROIKA", "MALL");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    public void importFromFile(String filePath) {
        log.info("Starting import from: {}", filePath);

        List<ToiletRecord> records;
        try {
            records = objectMapper.readValue(
                    new File(filePath),
                    new TypeReference<List<ToiletRecord>>() {});
        } catch (IOException e) {
            log.error("Cannot read or parse file '{}': {}", filePath, e.getMessage());
            return;
        }

        log.info("Loaded {} records from file", records.size());

        List<ToiletRecord> valid = new ArrayList<>();
        int skipped = 0;
        for (ToiletRecord r : records) {
            if (isValid(r)) {
                valid.add(r);
            } else {
                skipped++;
                log.warn("Skipping invalid record (title='{}'): lat={}, lng={}, type={}",
                        r.getTitle(), r.getLat(), r.getLng(), r.getType());
            }
        }

        log.info("{} valid, {} skipped", valid.size(), skipped);

        if (valid.isEmpty()) {
            log.warn("Nothing to import.");
            return;
        }

        // Partition into batches and insert
        List<List<ToiletRecord>> batches = partition(valid, BATCH_SIZE);
        int totalInserted = 0;
        for (int i = 0; i < batches.size(); i++) {
            try {
                int n = insertBatch(batches.get(i));
                totalInserted += n;
                log.info("Batch {}/{} inserted {} rows", i + 1, batches.size(), n);
            } catch (Exception e) {
                log.error("Batch {}/{} failed: {}", i + 1, batches.size(), e.getMessage());
            }
        }

        log.info("Import complete. Total inserted: {}", totalInserted);
    }

    private boolean isValid(ToiletRecord r) {
        if (r.getLat() == null || r.getLng() == null) return false;
        if (r.getLat() < -90.0 || r.getLat() > 90.0) return false;
        if (r.getLng() < -180.0 || r.getLng() > 180.0) return false;
        if (r.getTitle() == null || r.getTitle().isBlank()) return false;
        if (r.getType() != null && !VALID_TYPES.contains(r.getType().toUpperCase())) return false;
        return true;
    }

    @Transactional
    public int insertBatch(List<ToiletRecord> batch) {
        String sql = """
                INSERT INTO toilets
                    (title, address, lat, lng, location, type, accessible, rating, working_hours, description)
                VALUES
                    (?, ?, ?, ?,
                     ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                     ?::toilet_type, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """;

        List<Object[]> params = batch.stream().map(r -> new Object[]{
                r.getTitle(),
                r.getAddress(),
                r.getLat(),
                r.getLng(),
                r.getLng(), r.getLat(),                            // MakePoint(lng, lat)
                r.getType() != null ? r.getType().toUpperCase() : "FREE",
                r.getAccessible() != null ? r.getAccessible() : false,
                r.getRating() != null ? r.getRating() : 0.0,
                r.getWorkingHours(),
                r.getDescription()
        }).collect(Collectors.toList());

        int[] results = jdbc.batchUpdate(sql, params);
        return Arrays.stream(results).sum();
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
