package io.github.maxhayim.standbylight;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Just enough of the ADB protocol to run one shell command on the TV's own adbd over TCP: connect, sign in with
 * this app's key, open "shell:", read until it closes. No TLS (that is only for wireless debugging's pairing).
 */
final class Adb {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;

    /** The TV refused the key and no one accepted the prompt in time. */
    static final class NotAllowed extends IOException {
        NotAllowed() {
            super("not allowed: accept \"Allow debugging?\" on the TV, with Always allow ticked");
        }
    }

    static final class Message {
        final int command, arg0, arg1;
        final byte[] data;

        Message(int command, int arg0, int arg1, byte[] data) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.data = data;
        }
    }

    private Adb() {
    }

    /** Runs cmd and returns what it printed. promptWaitMs: how long to wait for the owner to accept the key. */
    static String shell(String host, int port, RSAPrivateKey priv, RSAPublicKey pub, String name, String cmd,
                        int promptWaitMs) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(10_000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));

            boolean signed = false;
            while (true) {
                Message m = read(in);
                if (m.command == CNXN) break;
                if (m.command != AUTH || m.arg0 != AUTH_TOKEN) throw new IOException("unexpected " + name(m.command));
                if (!signed) {
                    send(out, AUTH, AUTH_SIGNATURE, 0, AdbKey.sign(priv, m.data));
                    signed = true;
                } else {
                    // Key not known yet: offer it, and the TV asks its owner once.
                    send(out, AUTH, AUTH_RSAPUBLICKEY, 0, AdbKey.publicKey(pub, name));
                    s.setSoTimeout(promptWaitMs);
                    try {
                        m = read(in);
                    } catch (java.net.SocketTimeoutException e) {
                        throw new NotAllowed();
                    }
                    if (m.command != CNXN) throw new NotAllowed();
                    s.setSoTimeout(10_000);
                    break;
                }
            }

            final int local = 1;
            send(out, OPEN, local, 0, ("shell:" + cmd + "\0").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            int remote = 0;
            while (true) {
                Message m = read(in);
                if (m.command == OKAY) {
                    remote = m.arg0;
                } else if (m.command == WRTE) {
                    text.write(m.data);
                    send(out, OKAY, local, m.arg0, new byte[0]);
                } else if (m.command == CLSE) {
                    if (remote != 0) send(out, CLSE, local, remote, new byte[0]);
                    return text.toString(StandardCharsets.UTF_8.name());
                }
            }
        }
    }

    static byte[] header(int command, int arg0, int arg1, byte[] data) {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~command)
                .array();
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] data) throws IOException {
        out.write(header(command, arg0, arg1, data));
        out.write(data);
        out.flush();
    }

    static Message read(DataInputStream in) throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int command = b.getInt(), arg0 = b.getInt(), arg1 = b.getInt(), len = b.getInt();
        b.getInt(); // checksum: adbd stopped checking it in version 0x01000001
        if (b.getInt() != ~command) throw new IOException("bad magic");
        if (len < 0 || len > MAX_DATA) throw new IOException("bad length " + len);
        byte[] data = new byte[len];
        in.readFully(data);
        return new Message(command, arg0, arg1, data);
    }

    private static String name(int command) {
        byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array();
        return new String(b, StandardCharsets.US_ASCII);
    }
}
