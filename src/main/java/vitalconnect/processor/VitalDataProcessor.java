package vitalconnect.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vitalconnect.domain.VitalData;

import java.nio.charset.StandardCharsets;

/**
 * Processes raw decompressed data into VitalData objects.
 * Handles JSON parsing and data cleaning similar to TypeScript implementation.
 */
public class VitalDataProcessor {
    private static final Logger logger = LoggerFactory.getLogger(VitalDataProcessor.class);
    private final ObjectMapper objectMapper;

    public VitalDataProcessor() {
        this.objectMapper = new ObjectMapper();
        // Configure mapper to be lenient with unknown properties
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
    }

    /**
     * Process decompressed byte data into VitalData object.
     *
     * @param decompressedData the decompressed byte array
     * @return parsed VitalData object
     * @throws IllegalArgumentException if parsing fails
     */
    public VitalData process(byte[] decompressedData) {
        try {
            // Convert to string
            String jsonString = new String(decompressedData, StandardCharsets.UTF_8);

            // Clean JSON string (similar to TypeScript's cleanJsonString method)
            jsonString = cleanJsonString(jsonString);

            // Parse JSON to VitalData
            VitalData vitalData = objectMapper.readValue(jsonString, VitalData.class);

            logger.debug("Data processed successfully");
            return vitalData;

        } catch (Exception e) {
            logger.error("Failed to process data", e);
            throw new IllegalArgumentException("Data processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Clean JSON string from invalid characters and format issues.
     * Matches the TypeScript implementation's cleanJsonString method.
     */
    private String cleanJsonString(String jsonString) {
        // Remove control characters (0x00-0x1F and 0x7F)
        jsonString = jsonString.replaceAll("[\\x00-\\x1F\\x7F]", "");

        // Replace NaN, Infinity (all variants with optional +/- and any casing) with null
        jsonString = jsonString.replaceAll("(?i)(?<!\\w)[+\\-]?nan(?!\\w)", "null");
        jsonString = jsonString.replaceAll("(?i)(?<!\\w)[+\\-]?(?:inf|infinity)(?!\\w)", "null");

        // Fix decimal separators in two steps:
        // 1. Inside object values like "\"key\": 123,456"
        jsonString = jsonString.replaceAll("(\":\\s*-?\\d+),(\\d+)", "$1.$2");

        // 2. Standalone numbers in arrays like [123,456]
        jsonString = jsonString.replaceAll("(\\[|,\\s*)(-?\\d+),(\\d+)(?=[,\\]])", "$1$2.$3");

        return jsonString;
    }
}