package rip.ysm.legacy;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class DeflateUtil {
    private static final int MAX_DECOMPRESSED_BYTES = 512 * 1024 * 1024;

    public static byte[] compressBytes(final byte[] input) {
        if (input.length == 0) {
            return new byte[]{};
        }
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        final byte[] buf = new byte[1024];
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();
        while (!deflater.finished()) {
            final int compressedDataLength = deflater.deflate(buf);
            stream.write(buf, 0, compressedDataLength);
        }
        deflater.end();
        return stream.toByteArray();
    }

    public static byte[] decompressBytes(final byte[] input) throws DataFormatException {
        if (input.length == 0) {
            return new byte[]{};
        }
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        final byte[] buf = new byte[1024];
        final Inflater inflater = new Inflater();
        inflater.setInput(input, 0, input.length);
        while (!inflater.finished()) {
            final int resultLength = inflater.inflate(buf);
            if (resultLength == 0 && inflater.needsInput()) {
                throw new DataFormatException("Unexpected end of deflate stream");
            }
            if ((long) stream.size() + (long) resultLength > MAX_DECOMPRESSED_BYTES) {
                throw new DataFormatException("Deflate output exceeds limit");
            }
            stream.write(buf, 0, resultLength);
        }
        inflater.end();
        return stream.toByteArray();
    }
}
