package io.github.yg.cxhelper;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Arrays;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 只在 com.chaoxing.mobile 进程内安装 hook。
 * 不 hook system_server，不修改其他应用，不写宿主数据。
 */
public class HookEntry implements IXposedHookLoadPackage {

    static {
        try {
            XposedBridge.log("CxHelper <clinit> ok");
        } catch (Throwable ignored) {
        }
    }

    private static final String PKG = "com.chaoxing.mobile";
    private static final String TAG = "CxHelper";
    private static final long REINJECT_MS = 1500L;
    private static final int MAX_HTML = 4 * 1024 * 1024;

    private static final Map<WebView, Boolean> RUNNING =
            Collections.synchronizedMap(new WeakHashMap<WebView, Boolean>());
    private static final Set<Class<?>> CLIENT_HOOKED =
            Collections.synchronizedSet(new HashSet<Class<?>>());

    private static volatile String JS;
    private static volatile Config CFG;
    private static volatile boolean MAIN_PROCESS;
    private static volatile boolean TOASTED;

    static Config cfg() {
        return CFG;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !PKG.equals(lpparam.packageName)) {
            return;
        }
        MAIN_PROCESS = lpparam.processName == null || PKG.equals(lpparam.processName);
        BootLog.w("handleLoadPackage pkg=" + lpparam.packageName
                + " process=" + lpparam.processName + " pid=" + android.os.Process.myPid());
        dumpApi(XposedBridge.class, "XposedBridge");
        dumpApi(XposedHelpers.class, "XposedHelpers");
        try {
            XposedBridge.log("CxHelper loaded in " + lpparam.processName);
        } catch (Throwable ignored) {
        }
        try {
            LogX.i("handleLoadPackage process=" + lpparam.processName
                    + " main=" + MAIN_PROCESS);
            CFG = Config.load();
            BootLog.w("config enabled=" + CFG.enabled + " rate=" + CFG.rate
                    + " mute=" + CFG.mute + " next=" + CFG.autoNext
                    + " cont=" + CFG.autoContinue);
            LogX.i("config enabled=" + CFG.enabled + " rate=" + CFG.rate
                    + " mute=" + CFG.mute + " next=" + CFG.autoNext);
            if (!CFG.enabled) {
                return;
            }
            JS = buildJs(CFG);
            BootLog.w("js length=" + (JS == null ? -1 : JS.length()));
            LogX.i("js length=" + (JS == null ? -1 : JS.length()));
            if (JS == null || JS.length() == 0) {
                return;
            }
            hookApp();
            BootLog.w("hookApp done");
            hookWebView();
            BootLog.w("hookWebView done");
            hookClientSetter();
            hookBaseClient();
            hookDiagnostics(lpparam.classLoader);
            BootLog.w("all hooks installed");
            try {
                Application cur = de.robv.android.xposed.AndroidAppHelper.currentApplication();
                BootLog.w("currentApplication=" + cur);
                if (cur != null) {
                    appReady(cur, "handleLoadPackage-current");
                }
            } catch (Throwable t) {
                BootLog.w("currentApplication error " + t);
            }
        } catch (Throwable t) {
            BootLog.w("handle ERROR " + android.util.Log.getStackTraceString(t));
        }
    }

    private static void hookDiagnostics(ClassLoader cl) {
        NativeHooks.install(cl);
    }

    private static void hookDeclared(Class<?> clazz, String name, Class<?>[] types,
            XC_MethodHook callback) throws Throwable {
        Method m = clazz.getDeclaredMethod(name, types);
        m.setAccessible(true);
        hookMember(m, callback);
    }

    private static void hookMember(java.lang.reflect.Member member, XC_MethodHook callback)
            throws Throwable {
        Method hm = XposedBridge.class.getDeclaredMethod(
                "hookMethod", java.lang.reflect.Member.class, XC_MethodHook.class);
        hm.setAccessible(true);
        hm.invoke(null, member, callback);
    }

    private static void dumpApi(Class<?> c, String label) {
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) continue;
                BootLog.w("API " + label + " " + m.getName()
                        + Arrays.toString(m.getParameterTypes()));
            }
        } catch (Throwable t) {
            BootLog.w("dump " + label + " failed " + t);
        }
    }

    private static void hookApp() {
        // 有些 App 的 Application.onCreate 不调用 super，onCreate hook 可能不触发；
        // Instrumentation.callApplicationOnCreate 每次进程启动都会走。
        try {
            hookDeclared(Application.class, "attach", new Class[]{Context.class},
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            appReady((Context) param.thisObject, "Application.attach");
                        }
                    });
        } catch (Throwable t) {
            LogX.i("hook Application.attach failed " + t);
        }
        try {
            hookDeclared(Instrumentation.class, "callApplicationOnCreate",
                    new Class[]{Application.class}, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            appReady((Context) param.args[0], "Instrumentation");
                        }
                    });
        } catch (Throwable t) {
            LogX.i("hook Instrumentation failed " + t);
        }
        try {
            hookDeclared(Application.class, "onCreate", new Class[]{},
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    appReady((Context) param.thisObject, "Application.onCreate");
                }
            });
        } catch (Throwable t) {
            LogX.i("hook Application failed " + t);
        }
    }

    private static void appReady(Context ctx, String from) {
        LogX.init(ctx);
        LogX.i("app ready via " + from + " pid=" + android.os.Process.myPid());
        if (TOASTED) return;
        final Context app = ctx.getApplicationContext() != null
                ? ctx.getApplicationContext() : ctx;
        try {
            Toast.makeText(app, "学习通助手已加载 pid=" + android.os.Process.myPid(),
                    Toast.LENGTH_LONG).show();
            TOASTED = true;
        } catch (Throwable ignored) {
        }
    }

    private static void hookWebView() {
        XC_MethodHook afterLoad = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof WebView) {
                    WebView wv = (WebView) param.thisObject;
                    String u = param.args.length > 0 && param.args[0] instanceof String
                            ? safeUrl((String) param.args[0]) : "?";
                    LogX.i("load " + u + " view=" + wv.getClass().getName());
                    BootLog.w("load " + u + " view=" + wv.getClass().getName());
                    startInjector(wv);
                }
            }
        };
        try {
            hookDeclared(WebView.class, "loadUrl", new Class[]{String.class}, afterLoad);
        } catch (Throwable ignored) {
        }
        try {
            hookDeclared(WebView.class, "loadUrl", new Class[]{String.class, Map.class}, afterLoad);
        } catch (Throwable ignored) {
        }
        try {
            hookDeclared(WebView.class, "loadDataWithBaseURL",
                    new Class[]{String.class, String.class, String.class, String.class, String.class},
                    afterLoad);
        } catch (Throwable ignored) {
        }
        try {
            hookDeclared(WebView.class, "destroy", new Class[]{}, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof WebView) {
                        RUNNING.remove(param.thisObject);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookClientSetter() {
        try {
            hookDeclared(WebView.class, "setWebViewClient", new Class[]{WebViewClient.class},
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args[0] instanceof WebViewClient) {
                                WebViewClient c = (WebViewClient) param.args[0];
                                LogX.i("setWebViewClient " + c.getClass().getName());
                                hookConcreteClient(c);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private static void hookBaseClient() {
        try {
            for (Method m : WebViewClient.class.getDeclaredMethods()) {
                if (!"shouldInterceptRequest".equals(m.getName())) continue;
                hookMember(m, INTERCEPT);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookConcreteClient(WebViewClient client) {
        Class<?> cls;
        try {
            cls = client.getClass();
        } catch (Throwable t) {
            return;
        }
        if (cls == WebViewClient.class || !CLIENT_HOOKED.add(cls)) {
            return;
        }
        LogX.i("hookClient " + cls.getName());
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (!"shouldInterceptRequest".equals(m.getName())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 2) continue;
                if (!WebView.class.isAssignableFrom(pt[0])) continue;
                if (pt[1] != WebResourceRequest.class && pt[1] != String.class) continue;
                try {
                    hookMember(m, INTERCEPT);
                    LogX.i("hooked " + cls.getName() + "." + m.getName());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static final XC_MethodHook INTERCEPT = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object result = param.getResult();
            if (!(result instanceof WebResourceResponse)) return;

            String url = null;
            if (param.args.length > 1) {
                Object req = param.args[1];
                if (req instanceof WebResourceRequest) {
                    try {
                        url = ((WebResourceRequest) req).getUrl().toString();
                    } catch (Throwable ignored) {
                    }
                } else if (req instanceof String) {
                    url = (String) req;
                }
            }
            if (url != null && (url.contains("mooc") || url.contains("course")
                    || url.contains("chapter") || url.contains("ananas"))) {
                LogX.i("intercept " + safeUrl(url) + " mime=" + ((WebResourceResponse) result).getMimeType()
                        + " status=" + ((WebResourceResponse) result).getStatusCode());
            }
            WebResourceResponse wrapped = injectResponse((WebResourceResponse) result, url);
            if (wrapped != null) {
                param.setResult(wrapped);
            }
        }
    };

    private static WebResourceResponse injectResponse(WebResourceResponse response, String url) {
        try {
            if (url == null || !isChaoxing(url)) return null;
            String mime = response.getMimeType();
            if (mime == null) return null;
            mime = mime.toLowerCase();
            if (!mime.contains("html")) return null;

            Map<String, String> oldHeaders = response.getResponseHeaders();
            if (oldHeaders != null) {
                for (Map.Entry<String, String> e : oldHeaders.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase("content-length")) {
                        try {
                            if (Long.parseLong(e.getValue()) > MAX_HTML) return null;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            InputStream in = response.getData();
            if (in == null) return null;
            byte[] raw = readAll(in, MAX_HTML);
            if (raw == null || raw.length == 0) return null;

            String encoding = response.getEncoding();
            if (encoding == null || encoding.length() == 0) encoding = "UTF-8";
            Charset cs;
            try {
                cs = Charset.forName(encoding);
            } catch (Throwable t) {
                cs = Charset.forName("UTF-8");
                encoding = "UTF-8";
            }

            String html = new String(raw, cs);
            if (!html.contains("__CXH_ACTIVE__")) {
                String script = "\n<script type=\"text/javascript\">" + JS + "</script>\n";
                int pos = html.toLowerCase().lastIndexOf("</body>");
                if (pos >= 0) {
                    html = html.substring(0, pos) + script + html.substring(pos);
                } else {
                    html = html + script;
                }
            }

            byte[] out = html.getBytes(cs);
            Map<String, String> headers = new HashMap<String, String>();
            Map<String, String> old = response.getResponseHeaders();
            if (old != null) {
                for (Map.Entry<String, String> e : old.entrySet()) {
                    String k = e.getKey();
                    if (k == null) continue;
                    String lk = k.toLowerCase();
                    if (lk.equals("content-length") || lk.equals("content-encoding")
                            || lk.equals("content-security-policy")
                            || lk.equals("content-security-policy-report-only")) {
                        continue;
                    }
                    headers.put(k, e.getValue());
                }
            }

            int status = response.getStatusCode();
            if (status <= 0) status = 200;
            String reason = response.getReasonPhrase();
            if (reason == null || reason.length() == 0) reason = "OK";

            return new WebResourceResponse(mime, encoding, status, reason, headers,
                    new ByteArrayInputStream(out));
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isChaoxing(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("chaoxing.com") || host.endsWith(".chaoxing.com")
                    || host.equals("chaoxing.cn") || host.endsWith(".chaoxing.cn")
                    || host.equals("xuexitong.com") || host.endsWith(".xuexitong.com");
        } catch (Throwable t) {
            return false;
        }
    }

    private static byte[] readAll(InputStream in, int max) {
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream(Math.min(64 * 1024, max));
            byte[] buf = new byte[16 * 1024];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) return null;
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
            try {
                if (bos != null) bos.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void startInjector(final WebView webView) {
        if (webView == null) return;
        synchronized (RUNNING) {
            if (RUNNING.containsKey(webView)) return;
            RUNNING.put(webView, Boolean.TRUE);
        }
        LogX.i("startInjector " + webView.getClass().getName());
        webView.post(new Runnable() {
            private boolean logged;
            @Override
            public void run() {
                if (!RUNNING.containsKey(webView)) return;
                try {
                    webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
                } catch (Throwable ignored) {
                }
                try {
                    webView.setKeepScreenOn(true);
                } catch (Throwable ignored) {
                }
                String js = JS;
                if (js != null) {
                    try {
                        webView.evaluateJavascript(js, new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                if (!logged) {
                                    logged = true;
                                    LogX.i("eval result=" + value + " url=" + safeUrl(webView.getUrl()));
                                }
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
                webView.postDelayed(this, REINJECT_MS);
            }
        });
    }

    private static String safeUrl(String url) {
        if (url == null) return "null";
        int q = url.indexOf('?');
        String u = q >= 0 ? url.substring(0, q) : url;
        if (u.length() > 180) u = u.substring(0, 180);
        return u;
    }

    private static String buildJs(Config cfg) {
        try {
            String js = Js.code();
            if (js == null || js.length() == 0) {
                InputStream in = HookEntry.class.getClassLoader()
                        .getResourceAsStream("assets/inject.js");
                if (in == null) {
                    in = HookEntry.class.getResourceAsStream("/assets/inject.js");
                }
                if (in == null) return null;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                js = new String(bos.toByteArray(), Charset.forName("UTF-8"));
            }
            return js.replace("__CXH_CONFIG__", cfg.toJson());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": load js failed: " + t);
            return null;
        }
    }
}