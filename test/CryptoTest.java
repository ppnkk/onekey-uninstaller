import com.onekey.uninstaller.BackupFormat;
import com.onekey.uninstaller.Crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

/** PC-side test of the backup encryption and container format. */
public class CryptoTest {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) failed++;
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[7919];
        int n;
        while ((n = in.read(b)) != -1) o.write(b, 0, n);
        return o.toByteArray();
    }

    static byte[] make(Crypto.Keys k, byte[][] entries, String[] names, boolean finish) throws Exception {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (BackupFormat.Writer w = new BackupFormat.Writer(Crypto.encrypt(file, k.pub, k.privBlob))) {
            for (int i = 0; i < names.length; i++) w.put(names[i], new ByteArrayInputStream(entries[i]));
            if (finish) w.finish();
        }
        return file.toByteArray();
    }

    static boolean fails(byte[] file, char[] pw) {
        try (BackupFormat.Reader r = new BackupFormat.Reader(Crypto.decrypt(new ByteArrayInputStream(file), pw))) {
            while (r.next() != null) { }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public static void main(String[] a) throws Exception {
        char[] pw = "correct horse".toCharArray();
        Crypto.Keys k = Crypto.create(pw);
        Random rnd = new Random(42);

        byte[] meta = "{\"package\":\"com.example\"}".getBytes("UTF-8");
        byte[] apk = new byte[3 * 1024 * 1024 + 17];          // incompressible, many chunks
        rnd.nextBytes(apk);
        byte[] tar = new byte[5 * 1024 * 1024];               // compressible
        for (int i = 0; i < tar.length; i++) tar[i] = (byte) (i % 97);
        byte[] empty = new byte[0];
        byte[] exact = new byte[64 * 1024];                   // exactly one chunk
        rnd.nextBytes(exact);
        String[] names = {"meta.json", "apk/base.apk", "empty", "exact", "data.tar"};
        byte[][] data = {meta, apk, empty, exact, tar};

        byte[] file = make(k, data, names, true);
        System.out.println("backup size " + file.length + " bytes");

        // 1. round trip, reading every entry fully
        boolean ok = true;
        try (BackupFormat.Reader r = new BackupFormat.Reader(Crypto.decrypt(new ByteArrayInputStream(file), pw))) {
            for (int i = 0; i < names.length; i++) {
                String n = r.next();
                ok &= names[i].equals(n) && Arrays.equals(data[i], readAll(r.stream()));
            }
            ok &= r.next() == null;
        }
        check("round trip of all entries", ok);

        // 2. skipping entries without reading them, and partial reads
        ok = true;
        try (BackupFormat.Reader r = new BackupFormat.Reader(Crypto.decrypt(new ByteArrayInputStream(file), pw))) {
            ok &= "meta.json".equals(r.next());
            ok &= "apk/base.apk".equals(r.next());
            r.stream().read(new byte[1000]);
            ok &= "empty".equals(r.next());
            ok &= "exact".equals(r.next());
            ok &= "data.tar".equals(r.next());
            ok &= Arrays.equals(tar, readAll(r.stream()));
            ok &= r.next() == null;
        }
        check("skip and partial read", ok);

        // 3. plaintext must not be visible in the file
        String s = new String(file, "ISO-8859-1");
        check("content is not readable in file", !s.contains("com.example") && !s.contains("meta.json"));

        // 4. wrong password
        boolean bad = false;
        try {
            Crypto.decrypt(new ByteArrayInputStream(file), "wrong".toCharArray());
        } catch (Crypto.BadPasswordException e) {
            bad = true;
        }
        check("wrong password rejected", bad);

        // 5. tampering anywhere in the body is detected
        ok = true;
        for (int pos : new int[]{file.length / 4, file.length / 2, file.length - 5, 1600}) {
            byte[] t = file.clone();
            t[pos] ^= 1;
            ok &= fails(t, pw);
        }
        check("modified file rejected", ok);

        // 6. truncation is detected
        check("truncated file rejected", fails(Arrays.copyOf(file, file.length - 100), pw)
                && fails(Arrays.copyOf(file, file.length / 2), pw)
                && fails(Arrays.copyOf(file, 2000), pw));

        // 7. a backup that was never finished (crash while writing) is rejected
        check("unfinished backup rejected", fails(make(k, data, names, false), pw));

        // 8. a second key set cannot read the first one's backup header keys independently
        Crypto.Keys k2 = Crypto.create("other password".toCharArray());
        byte[] file2 = make(k2, new byte[][]{meta}, new String[]{"meta.json"}, true);
        check("each backup opens only with its own password",
                fails(file2, pw) && !fails(file2, "other password".toCharArray()) && !fails(file, pw));

        // 9. two backups of the same content differ (fresh key and nonce every time)
        check("backups are randomized", !Arrays.equals(file, make(k, data, names, true)));

        System.out.println(failed == 0 ? "ALL TESTS PASSED" : failed + " TEST(S) FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
