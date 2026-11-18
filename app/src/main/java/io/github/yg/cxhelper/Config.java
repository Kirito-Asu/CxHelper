package io.github.yg.cxhelper;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 运行参数。设置页写入 SharedPreferences("cxh")，
 * 模块进程里通过 XSharedPreferences 读取（manifest 已声明 xposedsharedprefs）。
 */
public final class Config {

    public boolean enabled = true;
    public boolean mute = false;
    public float rate = 2.0f;
    public boolean autoNext = true;
    public boolean autoContinue = true;
    public boolean autoPlay = true;
    public boolean quizGuess = false;
    public boolean jumpEnd = false;
    public boolean iframeInject = true;
    public boolean autoClose = false;

    public static Config load() {
        Config c = new Config();
        try {
            XSharedPreferences p = new XSharedPreferences(Const.MODULE_PKG, Const.PREFS_NAME);
            p.reload();
            c.enabled = p.getBoolean("enabled", c.enabled);
            c.mute = p.getBoolean("mute", c.mute);
            c.rate = clamp(p.getFloat("rate", c.rate));
            c.autoNext = p.getBoolean("auto_next", c.autoNext);
            c.autoContinue = p.getBoolean("auto_continue", c.autoContinue);
            c.autoPlay = p.getBoolean("auto_play", c.autoPlay);
            c.quizGuess = p.getBoolean("quiz_guess", c.quizGuess);
            c.jumpEnd = p.getBoolean("jump_end", c.jumpEnd);
            c.iframeInject = p.getBoolean("iframe_inject", c.iframeInject);
            c.autoClose = p.getBoolean("auto_close", c.autoClose);
        } catch (Throwable ignored) {
            // 读不到设置时使用默认值，不影响宿主应用。
        }
        return c;
    }

    private static float clamp(float v) {
        if (v < 1.0f) return 1.0f;
        if (v > 8.0f) return 8.0f;
        return v;
    }

    public String toJson() {
        return "{"
                + "\"mute\":" + mute + ","
                + "\"rate\":" + rate + ","
                + "\"autoNext\":" + autoNext + ","
                + "\"autoContinue\":" + autoContinue + ","
                + "\"autoPlay\":" + autoPlay + ","
                + "\"quizGuess\":" + quizGuess + ","
                + "\"jumpEnd\":" + jumpEnd + ","
                + "\"iframeInject\":" + iframeInject + ","
                + "\"autoClose\":" + autoClose
                + "}";
    }
}