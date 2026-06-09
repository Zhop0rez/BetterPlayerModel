package com.ysm.parser;

/**
 * JNI wrapper for low-level native algorithms used by YSM.
 *
 * <p>Exposes CityHash, Zstd, XChaCha20, ModifiedChaCha, and MT19937 primitives
 * directly to Java. All methods are static and thread-safe.
 */
public class YSMNative {
    // в”Ђв”Ђ CityHash в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    public static native long cityHash64(byte[] data);
    public static native long cityHash64WithSeed(byte[] data, long seed);
    public static native long[] cityHash128(byte[] data);
    public static native long[] cityHash128WithSeed(byte[] data, long seedLow, long seedHigh);

    // в”Ђв”Ђ Zstd в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    public static native byte[] zstdDecompress(byte[] data);
    public static native byte[] zstdCompress(byte[] data, int level);

    // в”Ђв”Ђ XChaCha20 в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

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

    // в”Ђв”Ђ MT19937 (stateful) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

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
     * и§ЈеЋ‹ YSM й­”ж”№зљ„ ZSTD ж•°жЌ®гЂ‚
     * еє•е±‚дјљи‡ЄеЉЁж‰§иЎЊ wash (жґ—з™Ѕ) ж“ЌдЅњпјЊз„¶еђЋиї›иЎЊж ‡е‡† ZSTD и§ЈеЋ‹гЂ‚
     *
     * @param data еЋ‹зј©дё”иў«ж··ж·†иї‡зљ„ byte ж•°з»„
     * @return и§ЈеЋ‹еђЋзљ„еЋџе§‹ byte ж•°з»„
     * @throws RuntimeException е¦‚жћњеє•е±‚и§Јз Ѓе¤±иґҐж€–е†…е­е€†й…Ќе¤±иґҐ
     * @throws IllegalArgumentException е¦‚жћњдј е…Ґзљ„ж•°жЌ®дёє null
     */
    public static native byte[] ysmZstdDecompress(byte[] data);

    /**
     * е°†ж•°жЌ®иї›иЎЊж ‡е‡† ZSTD еЋ‹зј©пјЊе№¶ж··ж·†дёє YSM й­”ж”№ж јејЏгЂ‚
     * еє•е±‚дјље…€иї›иЎЊж ‡е‡† ZSTD еЋ‹зј©пјЊз„¶еђЋи‡ЄеЉЁж‰§иЎЊ obfuscate (еј„и„Џ) ж“ЌдЅњгЂ‚
     *
     * @param data йњЂи¦ЃеЋ‹зј©зљ„еЋџе§‹ byte ж•°з»„
     * @param level ZSTD еЋ‹зј©з­‰зє§ (йЂљеёёжЋЁиЌђ 3пјЊжњЂе¤§йЂљеёёж”ЇжЊЃе€° 22)
     * @return еЋ‹зј©дё”ж··ж·†еђЋзљ„ byte ж•°з»„
     * @throws RuntimeException е¦‚жћњеє•е±‚еЋ‹зј©е¤±иґҐ
     * @throws IllegalArgumentException е¦‚жћњдј е…Ґзљ„ж•°жЌ®дёє null
     */
    public static native byte[] ysmZstdCompress(byte[] data, int level);
}
