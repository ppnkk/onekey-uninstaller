package com.onekey.uninstaller;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** Runs privileged commands: as root through "su", or with shell rights through Shizuku. */
final class Shell {
    static final int NONE = 0;
    static final int ROOT = 1;
    static final int SHIZUKU = 2;

    static final class Result {
        int code = -1;
        String out = "";
        boolean ok() { return code == 0; }
    }

    private Shell() {}

    static Result su(String cmd) { return run(ROOT, cmd, null, 0); }

    static Result su(String cmd, InputStream stdin) { return run(ROOT, cmd, stdin, 0); }

    static Result run(int mode, String cmd) { return run(mode, cmd, null, 0); }

    static Result run(int mode, String cmd, InputStream stdin) { return run(mode, cmd, stdin, 0); }

    static Result run(int mode, String cmd, InputStream stdin, long timeoutSec) {
        final Result r = new Result();
        Process p = null;
        try {
            if (mode == ROOT) {
                ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
                pb.redirectErrorStream(true);
                p = pb.start();
            } else if (mode == SHIZUKU) {
                p = Shz.start("{ " + cmd + "\n} 2>&1");
            } else {
                r.out = "no privileged mode";
                return r;
            }
            final Process fp = p;
            final StringBuilder sb = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (InputStream in = fp.getInputStream()) {
                    byte[] b = new byte[4096];
                    int n;
                    while ((n = in.read(b)) != -1) {
                        if (sb.length() < 65536) sb.append(new String(b, 0, n));
                    }
                } catch (Exception ignored) {
                }
            });
            reader.start();
            try (OutputStream o = p.getOutputStream()) {
                if (stdin != null) {
                    byte[] b = new byte[64 * 1024];
                    int n;
                    while ((n = stdin.read(b)) != -1) o.write(b, 0, n);
                }
            } catch (java.io.IOException e) {
                if (stdin != null) sb.append("\n[stdin: ").append(e.getMessage()).append("]");
            }
            if (timeoutSec > 0) {
                if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                    p.destroy();
                    r.out = "timeout";
                    return r;
                }
                r.code = p.exitValue();
            } else {
                r.code = p.waitFor();
            }
            reader.join(3000);
            r.out = sb.toString().trim();
        } catch (Throwable e) {
            r.code = -1;
            r.out = String.valueOf(e);
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
        return r;
    }

    /** True when the phone is rooted and root was granted to this app. */
    static boolean hasRoot(long timeoutSec) {
        Result r = run(ROOT, "id", null, timeoutSec);
        return r.ok() && r.out.contains("uid=0");
    }
}
