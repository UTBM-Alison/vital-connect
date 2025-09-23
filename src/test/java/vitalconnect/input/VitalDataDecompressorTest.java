package vitalconnect.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.*;

class VitalDataDecompressorTest {

    private VitalDataDecompressor decompressor;

    @BeforeEach
    void setUp() {
        decompressor = new VitalDataDecompressor();
    }

    @Test
    @DisplayName("Should decompress valid zlib compressed data")
    void testDecompressValidData() throws Exception {
        String originalData = "{\"test\":\"data\"}";
        byte[] compressed = compress(originalData.getBytes());

        byte[] result = decompressor.decompress(compressed);

        assertThat(new String(result)).isEqualTo(originalData);
    }

    @Test
    @DisplayName("Should handle Socket.IO v4 binary indicator")
    void testDecompressSocketIOData() throws Exception {
        String originalData = "{\"vrcode\":\"VR123\"}";
        byte[] compressed = compress(originalData.getBytes());

        // Add Socket.IO v4 binary indicator
        byte[] withIndicator = new byte[compressed.length + 1];
        withIndicator[0] = 0x04;
        System.arraycopy(compressed, 0, withIndicator, 1, compressed.length);

        byte[] result = decompressor.decompress(withIndicator);

        assertThat(new String(result)).isEqualTo(originalData);
    }

    @Test
    @DisplayName("Should handle ByteBuffer input")
    void testDecompressByteBuffer() throws Exception {
        String originalData = "{\"rooms\":[]}";
        byte[] compressed = compress(originalData.getBytes());
        ByteBuffer buffer = ByteBuffer.wrap(compressed);

        byte[] result = decompressor.decompress(buffer);

        assertThat(new String(result)).isEqualTo(originalData);
    }

    @Test
    @DisplayName("Should handle String input")
    void testDecompressString() {
        String data = "uncompressed string data";

        byte[] result = decompressor.decompress(data);

        assertThat(new String(result)).isEqualTo(data);
    }

    @Test
    @DisplayName("Should handle int array input")
    void testDecompressIntArray() {
        int[] intArray = {72, 101, 108, 108, 111}; // "Hello" in ASCII

        byte[] result = decompressor.decompress(intArray);

        assertThat(new String(result)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("Should return uncompressed data as-is")
    void testUncompressedData() {
        byte[] data = "not compressed".getBytes();

        byte[] result = decompressor.decompress(data);

        assertThat(result).isEqualTo(data);
    }

    @Test
    @DisplayName("Should throw exception for unsupported data type")
    void testUnsupportedDataType() {
        assertThatThrownBy(() -> decompressor.decompress(new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported data type");
    }

    @Test
    @DisplayName("Should handle corrupted compressed data")
    void testCorruptedData() {
        byte[] corrupted = {0x78, (byte) 0x9C, 0x00, 0x00, 0x00};

        assertThatThrownBy(() -> decompressor.decompress(corrupted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress");
    }

    private byte[] compress(byte[] data) throws Exception {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }

        deflater.end();
        return baos.toByteArray();
    }
}