package vitalconnect.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

/**
 * Handles decompression of VitalRecorder data.
 * Matches the TypeScript decompression logic for Socket.IO v4 data.
 */
public class VitalDataDecompressor {
    private static final Logger logger = LoggerFactory.getLogger(VitalDataDecompressor.class);
    private static final int BUFFER_SIZE = 4096;

    /**
     * Decompress raw Socket.IO data.
     *
     * @param rawData the raw data (can be byte[], ByteBuffer, or String)
     * @return decompressed byte array
     * @throws IllegalArgumentException if decompression fails
     */
    public byte[] decompress(Object rawData) {
        byte[] buffer = convertToByteArray(rawData);

        // Handle Socket.IO v4 type indicator byte
        if (buffer.length > 3 && buffer[0] == 4 && buffer[1] == 120) {
            // Skip the first byte (type indicator)
            buffer = skipFirstByte(buffer);
        }

        try {
            return inflateData(buffer);
        } catch (DataFormatException e) {
            // Retry without skipping first byte
            logger.debug("Retrying decompression without skipping first byte");
            try {
                return inflateData(convertToByteArray(rawData));
            } catch (DataFormatException e2) {
                throw new IllegalArgumentException("Failed to decompress data", e2);
            }
        }
    }

    /**
     * Decompress byte array data.
     */
    public byte[] decompress(byte[] data) {
        // Check for Socket.IO v4 binary indicator + zlib header
        if (data.length > 2 && data[0] == 0x04 && data[1] == 0x78) {
            logger.debug("Detected Socket.IO v4 binary event with zlib compression, skipping first byte");
            byte[] skipped = skipFirstByte(data);
            try {
                return inflateData(skipped);
            } catch (DataFormatException e) {
                throw new IllegalArgumentException("Failed to decompress Socket.IO v4 binary data", e);
            }
        }

        // Standard zlib header (0x78)
        if (data.length > 0 && (data[0] == 0x78)) {
            try {
                return inflateData(data);
            } catch (DataFormatException e) {
                logger.debug("Failed to decompress as zlib, trying with type byte skip");
                if (data.length > 1) {
                    byte[] skipped = skipFirstByte(data);
                    try {
                        return inflateData(skipped);
                    } catch (DataFormatException e2) {
                        throw new IllegalArgumentException("Failed to decompress data", e2);
                    }
                }
                throw new IllegalArgumentException("Failed to decompress data", e);
            }
        }

        // Not compressed → return as-is
        logger.debug("Data might not be compressed, returning as-is");
        return data;
    }

    private byte[] convertToByteArray(Object rawData) {
        switch (rawData) {
            case byte[] bytes -> {
                return bytes;
            }
            case ByteBuffer buffer -> {
                byte[] array = new byte[buffer.remaining()];
                buffer.get(array);
                return array;
            }
            case String s -> {
                return s.getBytes();
            }
            case int[] intArray -> {
                // Convert int array to byte array (from JavaScript typed array)
                byte[] byteArray = new byte[intArray.length];
                for (int i = 0; i < intArray.length; i++) {
                    byteArray[i] = (byte) intArray[i];
                }
                return byteArray;
            }
            default -> throw new IllegalArgumentException("Unsupported data type: " +
                    rawData.getClass());
        }
    }

    private byte[] skipFirstByte(byte[] buffer) {
        if (buffer.length <= 1) {
            return buffer;
        }
        byte[] newBuffer = new byte[buffer.length - 1];
        System.arraycopy(buffer, 1, newBuffer, 0, newBuffer.length);
        return newBuffer;
    }

    private byte[] inflateData(byte[] compressed) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressed.length * 2)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count > 0) {
                    outputStream.write(buffer, 0, count);
                } else if (!inflater.finished()) {
                    throw new DataFormatException("Inflater not finished but no bytes inflated");
                }
            }
            return outputStream.toByteArray();
        } catch (Exception e) {
            if (e instanceof DataFormatException) {
                throw (DataFormatException) e;
            }
            throw new DataFormatException("Failed to inflate data: " + e.getMessage());
        } finally {
            inflater.end();
        }
    }
}