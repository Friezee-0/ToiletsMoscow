package com.moscow.toilets.importer;

import com.moscow.toilets.importer.service.ToiletImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ImporterApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ImporterApplication.class);

    @Autowired
    private ToiletImportService importService;

    public static void main(String[] args) {
        SpringApplication.run(ImporterApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String filePath = args.length > 0 ? args[0] : "src/main/resources/toilets.json";
        log.info("Toilet Importer started. File: {}", filePath);
        importService.importFromFile(filePath);
        log.info("Toilet Importer finished.");
    }
}
