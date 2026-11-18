#!/usr/bin/env python3
"""把 assets/inject.js 转成 Js.java，避免依赖 APK 资源加载。"""
import base64, os, textwrap

root = os.path.dirname(os.path.abspath(__file__))
js_path = os.path.join(root, 'app/src/main/assets/inject.js')
out_path = os.path.join(root, 'app/src/main/java/io/github/yg/cxhelper/Js.java')

with open(js_path, 'rb') as f:
    b64 = base64.b64encode(f.read()).decode()
body = '\n'.join('            + "%s"' % line for line in textwrap.wrap(b64, 100))
src = '''package io.github.yg.cxhelper;

/** 构建时由 assets/inject.js 生成，避免依赖模块 APK 的 resource 加载。 */
final class Js {
    private static final String B64 =
            ""
%s;

    static String code() {
        try {
            byte[] raw = android.util.Base64.decode(B64, android.util.Base64.NO_WRAP);
            return new String(raw, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
''' % body
with open(out_path, 'w') as f:
    f.write(src)
print('Js.java updated')
