package io.github.maxhayim.standbylight;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.DateFormat;
import java.util.Date;

/**
 * The standby light, through Realtek's LED HAL. Only the ADB shell may call it, so this app signs in to the TV's
 * own adbd with a key of its own and runs one fixed command. Nothing else can be sent: no command comes from
 * outside the app.
 */
final class Light {
    /** LedState: 0 TV on, 1 panel off, 2 standby (STR), 3 low-power standby, 4 blinking. All of them. */
    static final int[] STATES = {0, 1, 2, 3, 4};
    private static final String HAL = "com.realtek.power.ILedManager/default";

    private Light() {
    }

    /** setPowerLedConfiguration(state, setting) is call 1; setting 0 is off, 1 the normal light. */
    static String command(boolean off) {
        StringBuilder b = new StringBuilder("for s in");
        for (int s : STATES) b.append(' ').append(s);
        return b.append("; do service call ").append(HAL).append(" 1 i32 $s i32 ").append(off ? 0 : 1)
                .append("; done").toString();
    }

    /** True when every call answered: one "Result: Parcel(" per state. */
    static boolean answered(String output) {
        int n = 0;
        for (int i = output.indexOf("Parcel("); i >= 0; i = output.indexOf("Parcel(", i + 1)) n++;
        return n == STATES.length;
    }

    /** Sends the command, retrying while adbd isn't up yet (just after boot). Saves and returns the outcome. */
    static boolean apply(Context c, boolean off, int tries, int promptWaitMs) {
        String result;
        boolean ok = false;
        try {
            KeyPair keys = keys(c);
            String out = null;
            IOException last = null;
            for (int i = 0; i < tries && out == null; i++) {
                try {
                    out = Adb.shell("127.0.0.1", 5555, (RSAPrivateKey) keys.getPrivate(),
                            (RSAPublicKey) keys.getPublic(), "standby-light@" + Build.MODEL, command(off),
                            promptWaitMs);
                } catch (Adb.NotAllowed e) {
                    throw e;
                } catch (IOException e) {
                    last = e;
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (out == null) throw last != null ? last : new IOException("no answer");
            ok = answered(out);
            result = ok ? (off ? "Light switched off" : "Light back to normal")
                    : "The TV answered, but not as expected: " + out.trim();
        } catch (IOException | GeneralSecurityException e) {
            result = "Couldn't reach the TV's ADB on port 5555: " + e.getMessage();
        }
        prefs(c).edit().putString("last", result + " (" + DateFormat.getDateTimeInstance(DateFormat.SHORT,
                DateFormat.SHORT).format(new Date()) + ")").apply();
        return ok;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("light", Context.MODE_PRIVATE);
    }

    /** Whether to switch it off again after each boot. */
    static boolean keepOff(Context c) {
        return prefs(c).getBoolean("keep_off", false);
    }

    static void setKeepOff(Context c, boolean on) {
        prefs(c).edit().putBoolean("keep_off", on).apply();
    }

    static String last(Context c) {
        return prefs(c).getString("last", "Not run yet");
    }

    /** This app's ADB key, made once and kept in its private storage. */
    private static synchronized KeyPair keys(Context c) throws IOException, GeneralSecurityException {
        File priv = new File(c.getFilesDir(), "adbkey"), pub = new File(c.getFilesDir(), "adbkey.pub");
        KeyFactory f = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            return new KeyPair(f.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                    f.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(AdbKey.BITS);
        KeyPair k = g.generateKeyPair();
        Files.write(priv.toPath(), k.getPrivate().getEncoded());
        Files.write(pub.toPath(), k.getPublic().getEncoded());
        return k;
    }
}
