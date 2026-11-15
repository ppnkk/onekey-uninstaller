package com.onekey.uninstaller;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Simple streaming container, gzip-compressed, that sits inside the encrypted stream.
 * Entry: UTF name | blocks of (int n, n bytes) | int 0. End of container: empty name.
 * Pure Java so it can be tested on a PC.
 */
public final class BackupFormat {
    private BackupFormat() {}

    public static final class Writer implements AutoCloseable {
        private final DataOutputStream out;
        private boolean finished;

        public Writer(OutputStream encrypted) throws IOException {
            out = new DataOutputStream(new GZIPOutputStream(encrypted, 64 * 1024));
        }

        public long put(String name, InputStream in) throws IOException {
            out.writeUTF(name);
            byte[] b = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(b)) != -1) {
                if (n == 0) continue;
                out.writeInt(n);
                out.write(b, 0, n);
                total += n;
            }
            out.writeInt(0);
            return total;
        }

        public void put(String name, byte[] data) throws IOException {
            put(name, new java.io.ByteArrayInputStream(data));
        }

        /** Marks the container complete. Without this the backup is rejected on restore. */
        public void finish() throws IOException {
            out.writeUTF("");
            finished = true;
        }

        public boolean isFinished() { return finished; }

        @Override public void close() throws IOException { out.close(); }
    }

    public static final class Reader implements AutoCloseable {
        private final DataInputStream in;
        private int left;
        private boolean entryOpen;

        public Reader(InputStream decrypted) throws IOException {
            in = new DataInputStream(new GZIPInputStream(decrypted, 64 * 1024));
        }

        /** Returns the next entry name, or null at the end. Skips any unread rest of the current entry. */
        public String next() throws IOException {
            if (entryOpen) {
                byte[] skip = new byte[64 * 1024];
                InputStream e = stream();
                while (e.read(skip) != -1) { /* skip */ }
            }
            String name = in.readUTF();
            if (name.isEmpty()) return null;
            entryOpen = true;
            left = 0;
            return name;
        }

        /** Stream of the current entry's bytes. */
        public InputStream stream() {
            return new InputStream() {
                @Override public int read() throws IOException {
                    byte[] one = new byte[1];
                    int n = read(one, 0, 1);
                    return n == -1 ? -1 : one[0] & 0xff;
                }

                @Override public int read(byte[] b, int off, int n) throws IOException {
                    if (!entryOpen) return -1;
                    if (n == 0) return 0;
                    if (left == 0) {
                        left = in.readInt();
                        if (left < 0) throw new IOException("Corrupt backup container");
                        if (left == 0) {
                            entryOpen = false;
                            return -1;
                        }
                    }
                    int k = in.read(b, off, Math.min(n, left));
                    if (k == -1) throw new IOException("Backup container is truncated");
                    left -= k;
                    return k;
                }
            };
        }

        @Override public void close() throws IOException { in.close(); }
    }
}
