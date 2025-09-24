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

    @Test
    @DisplayName("Should handle empty byte array")
    void testEmptyByteArray() {
        byte[] empty = new byte[0];

        byte[] result = decompressor.decompress(empty);

        assertThat(result).isEqualTo(empty);
    }

    @Test
    @DisplayName("Should handle single byte array")
    void testSingleByteArray() {
        byte[] single = new byte[]{0x41}; // 'A'

        byte[] result = decompressor.decompress(single);

        assertThat(result).isEqualTo(single);
    }

    @Test
    @DisplayName("Should handle zlib header with insufficient data")
    void testZlibHeaderInsufficientData() {
        byte[] data = new byte[]{0x78}; // Just zlib header start, no actual data

        assertThatThrownBy(() -> decompressor.decompress(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress");
    }

    @Test
    @DisplayName("Should handle Socket.IO v4 header with single byte")
    void testSocketIOHeaderSingleByte() {
        byte[] data = new byte[]{0x04}; // Just Socket.IO indicator, no zlib

        byte[] result = decompressor.decompress(data);

        // Should return as-is since it's not compressed
        assertThat(result).isEqualTo(data);
    }

    @Test
    @DisplayName("Should handle ByteBuffer with position not at start")
    void testByteBufferWithPosition() {
        byte[] data = "Hello World".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(6); // Position at "World"

        byte[] result = decompressor.decompress(buffer);

        assertThat(new String(result)).isEqualTo("World");
    }

    @Test
    @DisplayName("Should handle decompress with object parameter")
    void testDecompressObjectParameter() {
        String testString = "test data";

        byte[] result = decompressor.decompress((Object) testString);

        assertThat(new String(result)).isEqualTo(testString);
    }

    @Test
    @DisplayName("Should handle decompress with byte array object")
    void testDecompressObjectByteArray() {
        byte[] data = "test".getBytes();

        byte[] result = decompressor.decompress((Object) data);

        assertThat(result).isEqualTo(data);
    }

    @Test
    @DisplayName("Should handle failed decompression after skipping first byte")
    void testFailedDecompressionAfterSkip() {
        // Data that looks like it might be compressed but isn't valid after skipping
        byte[] data = new byte[]{0x78, 0x00, 0x00}; // Invalid zlib data

        assertThatThrownBy(() -> decompressor.decompress(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress");
    }

    @Test
    @DisplayName("Should handle compression with type byte that fails both attempts")
    void testCompressionFailsBothAttempts() {
        // Data with 0x78 that's not valid zlib
        byte[] data = new byte[]{0x78, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        assertThatThrownBy(() -> decompressor.decompress(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress");
    }

    @Test
    @DisplayName("Should handle very large uncompressed data")
    void testLargeUncompressedData() {
        byte[] largeData = new byte[100000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        byte[] result = decompressor.decompress(largeData);

        assertThat(result).isEqualTo(largeData);
    }

    @Test
    @DisplayName("Should handle Socket.IO v4 with two bytes but no valid compression")
    void testSocketIOTwoBytesNoCompression() {
        byte[] data = new byte[]{0x04, 0x41}; // Socket.IO indicator + 'A'

        byte[] result = decompressor.decompress(data);

        // Should return as-is since second byte is not 0x78
        assertThat(result).isEqualTo(data);
    }

    @Test
    @DisplayName("Should handle ByteBuffer object through decompress(Object)")
    void testDecompressObjectByteBuffer() throws Exception {
        String originalData = "{\"test\":\"buffer\"}";
        byte[] compressed = compress(originalData.getBytes());
        ByteBuffer buffer = ByteBuffer.wrap(compressed);

        byte[] result = decompressor.decompress((Object) buffer);

        assertThat(new String(result)).isEqualTo(originalData);
    }

    @Test
    @DisplayName("Should handle int array object through decompress(Object)")
    void testDecompressObjectIntArray() {
        int[] intArray = {84, 101, 115, 116}; // "Test" in ASCII

        byte[] result = decompressor.decompress((Object) intArray);

        assertThat(new String(result)).isEqualTo("Test");
    }

    @Test
    @DisplayName("Should handle Socket.IO v4 with invalid compressed data - covers line 42-43")
    void testSocketIOV4InvalidCompressedData() {
        // Create data with Socket.IO v4 indicator + zlib header but invalid compression
        byte[] invalidData = new byte[]{0x04, 0x78, (byte) 0x9C, 0x00, 0x00}; // Invalid zlib data

        assertThatThrownBy(() -> decompressor.decompress(invalidData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress Socket.IO v4 binary data")
                .hasCauseInstanceOf(java.util.zip.DataFormatException.class);
    }

    @Test
    @DisplayName("Should handle zlib data with fallback to skip first byte - covers line 56")
    void testZlibFallbackToSkipFirstByte() throws Exception {
        // Create data that fails first decompression but succeeds after skipping first byte
        String originalData = "test data for fallback";
        byte[] compressed = compress(originalData.getBytes());

        // Add a non-zlib byte at the beginning to force fallback
        byte[] dataWithPrefix = new byte[compressed.length + 1];
        dataWithPrefix[0] = 0x78; // zlib header to trigger first attempt
        System.arraycopy(compressed, 0, dataWithPrefix, 1, compressed.length);

        byte[] result = decompressor.decompress(dataWithPrefix);

        assertThat(new String(result)).isEqualTo(originalData);
    }

    @Test
    @DisplayName("Should handle Socket.IO v4 with exactly 2 bytes - covers edge case for line 37")
    void testSocketIOV4ExactlyTwoBytes() {
        // This tests the boundary condition for the Socket.IO v4 check
        byte[] twoBytes = new byte[]{0x04, 0x78}; // Exactly 2 bytes (data.length > 2 is false)

        // Should not be treated as Socket.IO v4 compressed data
        byte[] result = decompressor.decompress(twoBytes);
        assertThat(result).isEqualTo(twoBytes);
    }

    @Test
    @DisplayName("Should handle single byte buffer in skipFirstByte via zlib fallback - covers line 98")
    void testSingleByteBufferInSkipFirstByte() {
        // Create data with header zlib but which fails, then fallback with 1 byte remaining
        byte[] dataForFallback = new byte[]{0x78, 0x01}; // Header zlib + 1 byte invalid

        // This should trigger skipFirstByte with a 1-byte array, covering line 98
        assertThatThrownBy(() -> decompressor.decompress(dataForFallback))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress data");
    }

    @Test
    @DisplayName("Should handle inflater not finishing with zero bytes inflated - covers line 115")
    void testInflaterNotFinishingWithZeroBytes() {
        // Create incomplete zlib data that causes inflater to not finish properly
        byte[] incompleteZlibData = new byte[]{
                0x78, (byte) 0x9C, // zlib header
                // Incomplete compressed data
                0x01, 0x02, 0x03, 0x04
        };

        assertThatThrownBy(() -> decompressor.decompress(incompleteZlibData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress");
    }

    @Test
    @DisplayName("Should handle generic exception in inflateData - covers line 124")
    void testGenericExceptionInInflateData() {
        // Create data that causes a non-DataFormatException in the try block
        byte[] problematicData = new byte[]{
                0x78, (byte) 0xDA, // Alternative zlib header
                // Data that might cause unexpected behavior
                (byte) 0xED, (byte) 0xC0, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                (byte) 0x80, (byte) 0xA0, 0x3D, 0x00, 0x00, 0x00, 0x01
        };

        assertThatThrownBy(() -> decompressor.decompress(problematicData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decompress");
    }

    @Test
    @DisplayName("Should handle empty array return from skipFirstByte - covers line 98 indirectly")
    void testEmptyArrayReturnFromSkipFirstByte() {
        // Test with data that becomes empty after skipFirstByte
        byte[] singleByte = new byte[]{0x78}; // Only one byte

        // This will trigger the zlib path, fail decompression, then skipFirstByte returns empty array
        assertThatThrownBy(() -> decompressor.decompress(singleByte))
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