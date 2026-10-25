package io.github.maxhayim.standbylight;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public class StandbyLightTest {
    private static final KeyPair KEYS;

    static {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(AdbKey.BITS);
            KEYS = g.generateKeyPair();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void signatureVerifiesAsSha1DigestInfo() throws Exception {
        byte[] token = new byte[20];
        for (int i = 0; i < token.length; i++) token[i] = (byte) (i * 13 + 7);
        byte[] sig = AdbKey.sign((RSAPrivateKey) KEYS.getPrivate(), token);
        assertEquals(256, sig.length);
        // What adbd's RSA_verify(NID_sha1, token) checks: PKCS #1 v1.5 over DigestInfo(SHA-1, token)
        byte[] info = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
        Signature v = Signature.getInstance("NONEwithRSA");
        v.initVerify(KEYS.getPublic());
        v.update(info);
        v.update(token);
        assertTrue(v.verify(sig));
    }

    @Test(expected = IllegalArgumentException.class)
    public void signatureNeedsTwentyByteToken() {
        AdbKey.sign((RSAPrivateKey) KEYS.getPrivate(), new byte[16]);
    }

    @Test
    public void publicKeyInAndroidFormat() {
        RSAPublicKey pub = (RSAPublicKey) KEYS.getPublic();
        String line = new String(AdbKey.publicKey(pub, "test@tv"), StandardCharsets.UTF_8);
        assertTrue(line.endsWith(" test@tv\0"));
        byte[] raw = Base64.getDecoder().decode(line.substring(0, line.indexOf(' ')));
        assertEquals(4 + 4 + 256 + 256 + 4, raw.length);
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(64, b.getInt());
        long n0inv = b.getInt() & 0xffffffffL;
        byte[] n = new byte[256], rr = new byte[256];
        b.get(n);
        b.get(rr);
        assertEquals(65537, b.getInt());
        BigInteger modulus = new BigInteger(1, reverse(n));
        assertEquals(pub.getModulus(), modulus);
        // n0inv * n[0] == -1 (mod 2^32)
        long n0 = modulus.mod(BigInteger.ONE.shiftLeft(32)).longValue();
        assertEquals(0xffffffffL, (n0inv * n0) & 0xffffffffL);
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(modulus), new BigInteger(1, reverse(rr)));
    }

    @Test
    public void fixedPadsAndReverses() {
        assertArrayEquals(new byte[]{0, 0, 1, 2}, AdbKey.fixed(BigInteger.valueOf(0x0102), 4, false));
        assertArrayEquals(new byte[]{2, 1, 0, 0}, AdbKey.fixed(BigInteger.valueOf(0x0102), 4, true));
        assertArrayEquals(new byte[]{(byte) 0xff}, AdbKey.fixed(BigInteger.valueOf(255), 1, false));
    }

    @Test
    public void messageRoundTrip() throws Exception {
        byte[] data = "shell:echo\0".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Adb.header(Adb.OPEN, 1, 0, data));
        out.write(data);
        Adb.Message m = Adb.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(Adb.OPEN, m.command);
        assertEquals(1, m.arg0);
        assertArrayEquals(data, m.data);
        ByteBuffer h = ByteBuffer.wrap(Adb.header(Adb.CNXN, 0, 0, new byte[]{1, 2, 3})).order(ByteOrder.LITTLE_ENDIAN);
        h.position(16);
        assertEquals(6, h.getInt());
        assertEquals(~Adb.CNXN, h.getInt());
    }

    @Test
    public void commandCoversEveryStateAndOnlyTheHal() {
        String off = Light.command(true), on = Light.command(false);
        assertEquals("for s in 0 1 2 3 4; do service call com.realtek.power.ILedManager/default 1 i32 $s i32 0; done", off);
        assertTrue(on.endsWith("i32 $s i32 1; done"));
    }

    @Test
    public void answeredNeedsOneResultPerState() {
        String one = "Result: Parcel(NULL)\n";
        assertTrue(Light.answered(one.repeat(5)));
        assertFalse(Light.answered(one.repeat(4)));
        assertFalse(Light.answered("service: not found"));
    }

    private static byte[] reverse(byte[] a) {
        byte[] r = new byte[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[a.length - 1 - i];
        return r;
    }
}
