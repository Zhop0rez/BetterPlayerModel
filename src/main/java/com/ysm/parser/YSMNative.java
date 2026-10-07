package com.ysm.parser;

/**
 * JNI wrapper for low-level native algorithms used by YSM.
 *
 * <p>Exposes CityHash, Zstd, XChaCha20, ModifiedChaCha, and MT19937 primitives
 * directly to Java. All methods are static and thread-safe.
 */
public class YSMNative {
    // ── CityHash ──────────────────────────────────────────────────────────

    public static native long cityHash64(byte[] data);
    public static native long cityHash64WithSeed(byte[] data, long seed);
    public static native long[] cityHash128(byte[] data);
    public static native long[] cityHash128WithSeed(byte[] data, long seedLow, long seedHigh);

    // ── Zstd ──────────────────────────────────────────────────────────────

    public static native byte[] zstdDecompress(byte[] data);
    public static native byte[] zstdCompress(byte[] data, int level);

    // ── XChaCha20 ─────────────────────────────────────────────────────────

    /**
     * @param key   32-byte key
     * @param iv    24-byte nonce
     * @param rounds number of rounds (10, 20, or 30)
     */
    public static native byte[] xchacha20Encrypt(byte[] data, byte[] key, byte[] iv, int rounds);

    /**
     * Decryption is the same operation as encryption for XChaCha20.
     */
    public static native byte[] xchacha20Decrypt(byte[] data, byte[] key, byte[] iv, int rounds);

    /**
     * YSM-specific modified ChaCha decryptor used by V3 resources.
     *
     * @param key   32-byte key
     * @param iv    24-byte nonce
     * @param seed  CityHash seed controlling block updates
     */
    public static native byte[] modifiedChaChaDecrypt(byte[] data, byte[] key, byte[] iv, long seed);

    // ── MT19937 (stateful) ────────────────────────────────────────────────

    /**
     * Create a new MT19937-64 RNG instance.
     * @return opaque handle for subsequent calls
     */
    public static native long mt19937Create(long seed);

    /** Return the next 64-bit random value from the generator. */
    public static native long mt19937Next(long handle);

    /** Fill and return {@code count} random bytes from the generator. */
    public static native byte[] mt19937GenerateBytes(long handle, int count);

    /** Destroy the generator and release native resources. */
    public static native void mt19937Destroy(long handle);

    /**
     * Decompress the modified YSM ZSTD data.
     * The underlying layer automatically performs the wash step, then standard ZSTD decompression.
     *
     * @param data compressed and obfuscated byte array
     * @return the decompressed raw byte array
     * @throws RuntimeException if the underlying decode fails or memory allocation fails
     * @throws IllegalArgumentException if the given data is null
     */
    public static native byte[] ysmZstdDecompress(byte[] data);

    /**
     * Compress the data with standard ZSTD and obfuscate it into the modified YSM format.
     * The underlying layer first performs standard ZSTD compression, then automatically performs the obfuscate (dirty) step.
     *
     * @param data raw byte array to compress
     * @param level ZSTD compression level (3 is usually recommended, up to 22 is usually supported)
     * @return the compressed and obfuscated byte array
     * @throws RuntimeException if the underlying compression fails
     * @throws IllegalArgumentException if the given data is null
     */
    public static native byte[] ysmZstdCompress(byte[] data, int level);
}
