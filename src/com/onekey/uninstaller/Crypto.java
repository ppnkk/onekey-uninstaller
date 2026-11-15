package com.onekey.uninstaller;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Backup encryption. Pure Java (no Android classes) so it can be tested on a PC.
 *
 * A password protects an RSA private key. Backups are encrypted with a random
 * AES-256 key that is wrapped with the RSA public key, so the phone can create
 * backups without the password, but nobody can read them without it.
 *
 * File layout: "OKB2" | int n, protected private key | int n, wrapped AES key |
 * 4 byte nonce prefix | chunks. Each chunk: byte last | int len | AES-GCM(len + 16).
 */
public final class Crypto {
    private static final byte[] MAGIC = {'O', 'K', 'B', '2'};
    private static final int CHUNK = 64 * 1024;
    private static final int PBKDF2_ROUNDS = 150000;
    private static final SecureRandom RND = new SecureRandom();

    private Crypto() {}

    public static final class Keys {
        public byte[] pub;
        public byte[] privBlob;
    }

    public static final class BadPasswordException extends GeneralSecurityException {
        public BadPasswordException() { super("Wrong password"); }
    }

    public static Keys create(char[] password) throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        RND.nextBytes(salt);
        RND.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(kp.getPrivate().getEncoded());
        Keys k = new Keys();
        k.pub = kp.getPublic().getEncoded();
        k.privBlob = new byte[28 + ct.length];
        System.arraycopy(salt, 0, k.privBlob, 0, 16);
        System.arraycopy(iv, 0, k.privBlob, 16, 12);
        System.arraycopy(ct, 0, k.privBlob, 28, ct.length);
        return k;
    }

    private static SecretKey derive(char[] password, byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] raw = f.generateSecret(new PBEKeySpec(password, salt, PBKDF2_ROUNDS, 256)).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }

    public static PrivateKey unlock(byte[] blob, char[] password) throws GeneralSecurityException {
        if (blob == null || blob.length < 44) throw new GeneralSecurityException("Corrupt key data");
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        System.arraycopy(blob, 0, salt, 0, 16);
        System.arraycopy(blob, 16, iv, 0, 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, derive(password, salt), new GCMParameterSpec(128, iv));
        byte[] pkcs8;
        try {
            pkcs8 = c.doFinal(blob, 28, blob.length - 28);
        } catch (GeneralSecurityException e) {
            throw new BadPasswordException();
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    private static Cipher rsa(int mode, java.security.Key key) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(mode, key, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT));
        return c;
    }

    /** Writes the header and returns a stream that encrypts everything written to it. */
    public static OutputStream encrypt(OutputStream raw, byte[] pub, byte[] privBlob)
            throws IOException, GeneralSecurityException {
        PublicKey pk = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pub));
        byte[] aes = new byte[32];
        byte[] prefix = new byte[4];
        RND.nextBytes(aes);
        RND.nextBytes(prefix);
        byte[] wrapped = rsa(Cipher.ENCRYPT_MODE, pk).doFinal(aes);
        DataOutputStream d = new DataOutputStream(raw);
        d.write(MAGIC);
        d.writeInt(privBlob.length);
        d.write(privBlob);
        d.writeInt(wrapped.length);
        d.write(wrapped);
        d.write(prefix);
        return new ChunkOut(d, new SecretKeySpec(aes, "AES"), prefix);
    }

    /** Reads the header, unlocks with the password and returns the decrypted stream. */
    public static InputStream decrypt(InputStream raw, char[] password)
            throws IOException, GeneralSecurityException {
        DataInputStream d = new DataInputStream(raw);
        byte[] m = new byte[4];
        d.readFully(m);
        if (!java.util.Arrays.equals(m, MAGIC)) throw new IOException("Not a OneKey backup file");
        int n = d.readInt();
        if (n < 44 || n > 16384) throw new IOException("Corrupt backup header");
        byte[] blob = new byte[n];
        d.readFully(blob);
        n = d.readInt();
        if (n < 16 || n > 4096) throw new IOException("Corrupt backup header");
        byte[] wrapped = new byte[n];
        d.readFully(wrapped);
        byte[] prefix = new byte[4];
        d.readFully(prefix);
        PrivateKey priv = unlock(blob, password);
        byte[] aes = rsa(Cipher.DECRYPT_MODE, priv).doFinal(wrapped);
        return new ChunkIn(d, new SecretKeySpec(aes, "AES"), prefix);
    }

    private static byte[] nonce(byte[] prefix, long counter) {
        byte[] iv = new byte[12];
        System.arraycopy(prefix, 0, iv, 0, 4);
        for (int i = 0; i < 8; i++) iv[11 - i] = (byte) (counter >>> (8 * i));
        return iv;
    }

    private static final class ChunkOut extends OutputStream {
        private final DataOutputStream out;
        private final SecretKey key;
        private final byte[] prefix;
        private final byte[] buf = new byte[CHUNK];
        private int len;
        private long counter;
        private boolean closed;

        ChunkOut(DataOutputStream out, SecretKey key, byte[] prefix) {
            this.out = out;
            this.key = key;
            this.prefix = prefix;
        }

        @Override public void write(int b) throws IOException {
            if (len == CHUNK) emit(false);
            buf[len++] = (byte) b;
        }

        @Override public void write(byte[] b, int off, int n) throws IOException {
            while (n > 0) {
                if (len == CHUNK) emit(false);
                int k = Math.min(n, CHUNK - len);
                System.arraycopy(b, off, buf, len, k);
                len += k;
                off += k;
                n -= k;
            }
        }

        private void emit(boolean last) throws IOException {
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce(prefix, counter++)));
                byte flag = (byte) (last ? 1 : 0);
                c.updateAAD(new byte[]{flag});
                byte[] ct = c.doFinal(buf, 0, len);
                out.writeByte(flag);
                out.writeInt(len);
                out.write(ct);
                len = 0;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        }

        @Override public void flush() throws IOException { out.flush(); }

        @Override public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                emit(true);
                out.flush();
            } finally {
                out.close();
            }
        }
    }

    private static final class ChunkIn extends InputStream {
        private final DataInputStream in;
        private final SecretKey key;
        private final byte[] prefix;
        private byte[] buf = new byte[0];
        private int pos;
        private long counter;
        private boolean last;

        ChunkIn(DataInputStream in, SecretKey key, byte[] prefix) {
            this.in = in;
            this.key = key;
            this.prefix = prefix;
        }

        private boolean fill() throws IOException {
            while (pos >= buf.length) {
                if (last) return false;
                int flag;
                int n;
                byte[] ct;
                try {
                    flag = in.readUnsignedByte();
                    n = in.readInt();
                    if (flag > 1 || n < 0 || n > CHUNK) throw new IOException("Corrupt backup data");
                    ct = new byte[n + 16];
                    in.readFully(ct);
                } catch (EOFException e) {
                    throw new IOException("Backup file is truncated");
                }
                try {
                    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                    c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce(prefix, counter++)));
                    c.updateAAD(new byte[]{(byte) flag});
                    buf = c.doFinal(ct);
                } catch (GeneralSecurityException e) {
                    throw new IOException("Backup data is damaged or was modified");
                }
                pos = 0;
                last = flag == 1;
            }
            return true;
        }

        @Override public int read() throws IOException {
            if (!fill()) return -1;
            return buf[pos++] & 0xff;
        }

        @Override public int read(byte[] b, int off, int n) throws IOException {
            if (n == 0) return 0;
            if (!fill()) return -1;
            int k = Math.min(n, buf.length - pos);
            System.arraycopy(buf, pos, b, off, k);
            pos += k;
            return k;
        }

        @Override public void close() throws IOException { in.close(); }
    }
}
