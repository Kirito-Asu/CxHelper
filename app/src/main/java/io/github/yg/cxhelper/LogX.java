package io.github.yg.cxhelper;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** 诊断日志：写入学习通自己的外部 files 目录，方便直接取回。 */
final class LogX {
    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> PENDING = new ArrayDeque<String>();
    private static File file;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private LogX() {
    }

    static void init(Context ctx) {
        synchronized (LOCK) {
            if (file != null || ctx == null) return;
            try {
                File dir = ctx.getExternalFilesDir(null);
                if (dir == null) dir = ctx.getFilesDir();
                file = new File(dir, "cxhelper.log");
                if (file.length() > 256 * 1024) {
                    File old = new File(dir, "cxhelper.old.log");
                    if (old.exists()) old.delete();
                    file.renameTo(old);
                }
            } catch (Throwable ignored) {
            }
            while (!PENDING.isEmpty()) write(PENDING.pollFirst());
        }
    }

    static void i(String msg) {
        Log.i("CxHelper", msg);
        synchronized (LOCK) {
            if (file == null) {
                if (PENDING.size() < 200) PENDING.addLast(msg);
                return;
            }
            write(msg);
        }
    }

    private static void write(String msg) {
        if (file == null) return;
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write((FMT.format(new Date()) + "  " + msg + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
    }
}
