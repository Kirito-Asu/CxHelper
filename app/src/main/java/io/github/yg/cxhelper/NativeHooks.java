package io.github.yg.cxhelper;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** 学习通课程视频使用 IjkMediaPlayer，这里直接控制原生播放器和原生按钮。 */
final class NativeHooks {
    private static final String[] NEXT = {"下一节", "下一章", "下一讲", "下一课", "下一个"};
    private static final String[] CONT = {"继续学习", "继续观看", "开始学习", "继续播放"};

    private static Config cfg;
    private static volatile WeakReference<Activity> activity = new WeakReference<Activity>(null);
    private static volatile WeakReference<Object> player = new WeakReference<Object>(null);
    private static volatile long endedAt;
    private static volatile boolean installed;

    private NativeHooks() {
    }

    static void install(ClassLoader cl) {
        if (installed) return;
        installed = true;
        cfg = HookEntry.cfg();
        hookActivity();
        hookIjk(cl);
    }

    private static void hookActivity() {
        try {
            XposedBridge.log("CxHelper NativeHooks activity");
            hookDeclared(Activity.class, "onResume", new Class[]{}, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity a = (Activity) param.thisObject;
                    activity = new WeakReference<Activity>(a);
                    BootLog.w("activity resume " + a.getClass().getName());
                    startLoop(a);
                }
            });
            hookDeclared(Activity.class, "onPause", new Class[]{}, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Activity a = (Activity) param.thisObject;
                    Activity cur = activity.get();
                    if (cur == a) activity = new WeakReference<Activity>(null);
                }
            });
        } catch (Throwable t) {
            BootLog.w("hook activity failed " + t);
        }
    }

    private static void hookIjk(ClassLoader cl) {
        try {
            Class<?> ijk = Class.forName("tv.danmaku.ijk.media.player.IjkMediaPlayer", false, cl);
            for (Constructor<?> c : ijk.getDeclaredConstructors()) {
                try {
                    hookMember(c, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            player = new WeakReference<Object>(param.thisObject);
                            endedAt = 0;
                            BootLog.w("IjkMediaPlayer new");
                        }
                    });
                } catch (Throwable ignored) {
                }
            }

            final Method setSpeed = findMethod(ijk, "setSpeed", float.class);
            final Method setVolume = findMethod(ijk, "setVolume", float.class, float.class);
            final Method start = findMethod(ijk, "start");
            final Method prepare = findMethod(ijk, "prepareAsync");

            if (setSpeed != null) {
                hookMember(setSpeed, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (cfg != null && cfg.rate > 1.0f) param.args[0] = cfg.rate;
                    }
                });
                BootLog.w("hooked Ijk.setSpeed");
            }
            if (setVolume != null) {
                hookMember(setVolume, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (cfg != null && cfg.mute) {
                            param.args[0] = 0f;
                            param.args[1] = 0f;
                        }
                    }
                });
                BootLog.w("hooked Ijk.setVolume");
            }
            if (start != null) {
                hookMember(start, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        player = new WeakReference<Object>(param.thisObject);
                        endedAt = 0;
                        BootLog.w("Ijk.start");
                        if (cfg != null && cfg.rate > 1.0f && setSpeed != null) {
                            try {
                                setSpeed.invoke(param.thisObject, cfg.rate);
                            } catch (Throwable ignored) {
                            }
                        }
                        if (cfg != null && cfg.mute && setVolume != null) {
                            try {
                                setVolume.invoke(param.thisObject, 0f, 0f);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
                BootLog.w("hooked Ijk.start");
            }
            if (prepare != null && start != null) {
                final Method startM = start;
                hookMember(prepare, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (cfg == null || !cfg.autoPlay) return;
                        scheduleStart(param.thisObject, startM, 3);
                    }
                });
                BootLog.w("hooked Ijk.prepareAsync");
            }
        } catch (Throwable t) {
            BootLog.w("hook Ijk failed " + t);
        }
    }

    private static void scheduleStart(final Object p, final Method start, final int tries) {
        final Activity a = activity.get();
        if (a == null || tries <= 0) return;
        View root = a.getWindow() == null ? null : a.getWindow().getDecorView();
        if (root == null) return;
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    start.invoke(p);
                    BootLog.w("Ijk auto start ok");
                } catch (Throwable t) {
                    BootLog.w("Ijk auto start retry " + t);
                    scheduleStart(p, start, tries - 1);
                }
            }
        }, 1200);
    }

    private static void startLoop(final Activity a) {
        final View root = a.getWindow() == null ? null : a.getWindow().getDecorView();
        if (root == null) return;
        root.post(new Runnable() {
            @Override
            public void run() {
                if (activity.get() != a) {
                    return;
                }
                try {
                    tick(a, root);
                } catch (Throwable t) {
                    BootLog.w("tick error " + t);
                }
                root.postDelayed(this, 1500);
            }
        });
    }

    private static void tick(Activity a, View root) {
        Object p = player.get();
        if (p != null && cfg != null) {
            long dur = number(p, "getDuration");
            long pos = number(p, "getCurrentPosition");
            if (dur > 0 && pos > 0) {
                if (pos >= dur - 1500 && endedAt == 0) {
                    endedAt = System.currentTimeMillis();
                    BootLog.w("player near end pos=" + pos + "/" + dur);
                }
            }
        }

        long now = System.currentTimeMillis();
        if (cfg != null && cfg.autoContinue) {
            if (clickText(root, CONT, 0)) {
                BootLog.w("clicked continue");
            }
        }
        if (cfg != null && cfg.autoNext && endedAt > 0 && now - endedAt < 20000) {
            if (clickText(root, NEXT, 0)) {
                BootLog.w("clicked next");
                endedAt = 0;
            }
        }
    }

    private static long number(Object o, String method) {
        try {
            Method m = findMethod(o.getClass(), method);
            if (m == null) return 0;
            Object r = m.invoke(o);
            return r instanceof Number ? ((Number) r).longValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean clickText(View v, String[] words, int depth) {
        if (v == null || depth > 24 || !v.isShown() || !v.isEnabled()) return false;
        if (v instanceof TextView) {
            String t = ((TextView) v).getText() == null ? "" : ((TextView) v).getText().toString();
            t = t.replaceAll("\\s+", "");
            for (String w : words) {
                if (t.length() <= 12 && t.contains(w)) {
                    View c = v;
                    for (int i = 0; i < 6 && c != null; i++) {
                        if (c.isClickable() && c.performClick()) return true;
                        c = c.getParent() instanceof View ? (View) c.getParent() : null;
                    }
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (clickText(g.getChildAt(i), words, depth + 1)) return true;
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... types) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        try {
            Method m = c.getMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void hookDeclared(Class<?> clazz, String name, Class<?>[] types,
            XC_MethodHook callback) throws Throwable {
        Method m = clazz.getDeclaredMethod(name, types);
        m.setAccessible(true);
        hookMember(m, callback);
    }

    private static void hookMember(Member member, XC_MethodHook callback) throws Throwable {
        Method hm = XposedBridge.class.getDeclaredMethod(
                "hookMethod", Member.class, XC_MethodHook.class);
        hm.setAccessible(true);
        hm.invoke(null, member, callback);
    }
}
