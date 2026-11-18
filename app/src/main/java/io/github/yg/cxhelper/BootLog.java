package io.github.yg.cxhelper;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 不依赖 Context / Looper 的启动日志。
 * handleLoadPackage 时进程已经是学习通 UID，可直接写它自己的外部目录。
 */
final class BootLog {
    private static final String[] PATHS = {
            "/storage/emulated/0/Android/data/com.chaoxing.mobile/files/cxhelper_boot.log",
            "/sdcard/Android/data/com.chaoxing.mobile/files/cxhelper_boot.log",
            "/data/data/com.chaoxing.mobile/files/cxhelper_boot.log",
            "/data/user/0/com.chaoxing.mobile/files/cxhelper_boot.log"
    };
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private BootLog() {
    }

    static void w(String msg) {
        String line = FMT.format(new Date()) + "  " + msg + "\n";
        for (String p : PATHS) {
            try {
                File f = new File(p);
                File dir = f.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                FileOutputStream fos = new FileOutputStream(f, true);
                fos.write(line.getBytes("UTF-8"));
                fos.close();
                return;
            } catch (Throwable ignored) {
            }
        }
    }
}
