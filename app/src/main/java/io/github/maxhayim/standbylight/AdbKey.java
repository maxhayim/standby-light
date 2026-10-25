package io.github.maxhayim.standbylight;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * The two things adbd asks of a key: a signature over its 20-byte token, and the public key in Android's own
 * format (the one in adb_keys). Pure Java, so the JVM tests cover it.
 */
final class AdbKey {
    static final int BITS = 2048;
    private static final int BYTES = BITS / 8;
    private static final int WORDS = BITS / 32;

    /** DER DigestInfo for SHA-1: adbd checks the token with RSA_verify(NID_sha1, ...). */
    private static final byte[] SHA1_INFO = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private AdbKey() {
    }

    /** PKCS #1 v1.5 signature of the token, done by hand so no crypto provider can change the padding. */
    static byte[] sign(RSAPrivateKey key, byte[] token) {
        if (token.length != 20) throw new IllegalArgumentException("token must be 20 bytes, was " + token.length);
        byte[] block = new byte[BYTES];
        block[1] = 0x01;
        int info = BYTES - SHA1_INFO.length - token.length;
        Arrays.fill(block, 2, info - 1, (byte) 0xff);
        System.arraycopy(SHA1_INFO, 0, block, info, SHA1_INFO.length);
        System.arraycopy(token, 0, block, info + SHA1_INFO.length, token.length);
        BigInteger s = new BigInteger(1, block).modPow(key.getPrivateExponent(), key.getModulus());
        return fixed(s, BYTES, false);
    }

    /** "base64 name\0", base64 of {len, n0inv, n[], rr[], e} as little-endian 32-bit words. */
    static byte[] publicKey(RSAPublicKey key, String name) {
        BigInteger n = key.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(BITS).modPow(BigInteger.valueOf(2), n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + BYTES + BYTES + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(WORDS);
        b.putInt(n0inv.intValue());
        b.put(fixed(n, BYTES, true));
        b.put(fixed(rr, BYTES, true));
        b.putInt(key.getPublicExponent().intValue());
        String line = Base64.getEncoder().encodeToString(b.array()) + " " + name + "\0";
        return line.getBytes(StandardCharsets.UTF_8);
    }

    /** The number in exactly size bytes, big-endian, or little-endian when reversed. */
    static byte[] fixed(BigInteger v, int size, boolean littleEndian) {
        byte[] raw = v.toByteArray();
        int skip = raw.length > size ? raw.length - size : 0; // drop BigInteger's sign byte
        byte[] out = new byte[size];
        System.arraycopy(raw, skip, out, size - (raw.length - skip), raw.length - skip);
        if (littleEndian) {
            for (int i = 0, j = size - 1; i < j; i++, j--) {
                byte t = out[i];
                out[i] = out[j];
                out[j] = t;
            }
        }
        return out;
    }
}
