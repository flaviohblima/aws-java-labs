package br.com.flaviohblima;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class Emf {

    private Emf() {
        /* This utility class should not be instantiated */
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    static void flush(String key, int ok, int bad, long milis, boolean success) {
        try {
            Map<String, Object> emf = Map.of(
                "_aws", Map.of(
                    "Timestamp", System.currentTimeMillis(),
                    "CloudWatchMetrics", List.of(Map.of(
                        "Namespace", "Lab9/Ingestion",
                        "Dimensions", List.of(List.of("Outcome")),
                        "Metrics", List.of(
                            Map.of("Name", "RowsIngested", "Unit", "Count"),
                            Map.of("Name", "RowsSkipped", "Unit", "Count"),
                            Map.of("Name", "DurationMs", "Unit", "Miliseconds"))))),
                "Outcome", success ? "Success" : "Failure",
                "RowsIngested", ok,
                "RowsSkipped", bad,
                "DurationMs", milis,
                "ObjectKey", key
            );

            System.out.println(JSON.writeValueAsString(emf));
        } catch (Exception ex) {
            // Metrics must never break business path
        }
    }

}
