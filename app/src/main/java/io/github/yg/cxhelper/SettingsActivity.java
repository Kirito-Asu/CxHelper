package io.github.yg.cxhelper;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private SharedPreferences sp;
    private Switch swEnabled;
    private Switch swMute;
    private Switch swNext;
    private Switch swContinue;
    private Switch swQuiz;
    private Switch swJump;
    private Switch swIframe;
    private Switch swClose;
    private SeekBar sbRate;
    private TextView tvRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE);
        setContentView(buildUi());
        load();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("学习通助手");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("LSPosed 模块 · 仅作用于 com.chaoxing.mobile\n"
                + "不修改系统设置，不写入学习通数据，不影响其他应用。");
        sub.setTextSize(13);
        sub.setTextColor(Color.rgb(100, 100, 100));
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        TextView env = new TextView(this);
        env.setText(envSummary());
        env.setTextSize(12);
        env.setTextColor(Color.rgb(80, 90, 110));
        env.setBackgroundColor(Color.rgb(238, 242, 248));
        env.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(env);

        swEnabled = addSwitch(root, "启用模块", "关闭后重启学习通即停止注入");
        swMute = addSwitch(root, "静音自动播放", "进入页面后自动播放视频并静音");
        swNext = addSwitch(root, "自动下一节", "当前视频结束后点击下一节");
        swContinue = addSwitch(root, "自动继续学习", "自动点击“继续学习 / 继续观看”");
        swIframe = addSwitch(root, "注入子框架", "课程播放器在 iframe 内时也能控制");
        swQuiz = addSwitch(root, "自动猜题", "弹题出现时选第一个选项并提交，可能答错");
        swJump = addSwitch(root, "跳到视频结尾", "可能被平台判定为未完成，默认关闭");
        swClose = addSwitch(root, "自动关提示框", "自动点“知道了 / 关闭”，默认关闭");

        TextView rateTitle = new TextView(this);
        rateTitle.setText("播放速度");
        rateTitle.setTextSize(16);
        rateTitle.setTextColor(Color.rgb(30, 30, 30));
        rateTitle.setPadding(0, dp(18), 0, dp(4));
        root.addView(rateTitle);

        tvRate = new TextView(this);
        tvRate.setTextSize(14);
        tvRate.setTextColor(Color.rgb(90, 90, 90));
        root.addView(tvRate);

        sbRate = new SeekBar(this);
        sbRate.setMax(70);
        root.addView(sbRate, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sbRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvRate.setText(String.format(java.util.Locale.US, "当前：%.1fx", 1.0f + progress / 10.0f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        Button save = new Button(this);
        save.setText("保存设置");
        save.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(26);
        root.addView(save, lp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                Toast.makeText(SettingsActivity.this,
                        "已保存。请强行停止并重新打开学习通。", Toast.LENGTH_LONG).show();
            }
        });

        Button reset = new Button(this);
        reset.setText("恢复默认");
        reset.setAllCaps(false);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp2.topMargin = dp(8);
        root.addView(reset, lp2);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().clear().apply();
                load();
            }
        });

        TextView tip = new TextView(this);
        tip.setText("使用前：LSPosed 里启用本模块，作用域只勾选“学习通”。"
                + "如果平台提示倍速异常，把速度调回 1.0x。");
        tip.setTextSize(12);
        tip.setTextColor(Color.rgb(120, 100, 60));
        tip.setPadding(0, dp(20), 0, 0);
        root.addView(tip);

        return scroll;
    }

    private String envSummary() {
        String lsp;
        String cx;
        try {
            getPackageManager().getPackageInfo("org.lsposed.manager", 0);
            lsp = "LSPosed 管理器：已安装";
        } catch (Throwable t) {
            lsp = "LSPosed 管理器：未检测到";
        }
        try {
            PackageInfo pi = getPackageManager().getPackageInfo("com.chaoxing.mobile", 0);
            cx = "学习通：已安装 " + (pi.versionName == null ? "" : pi.versionName);
        } catch (Throwable t) {
            cx = "学习通：未安装";
        }
        return lsp + "\n" + cx + "\n模块：v2.2 原生版";
    }

    private Switch addSwitch(LinearLayout root, String label, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(Color.rgb(25, 25, 25));
        text.addView(tv);
        TextView tv2 = new TextView(this);
        tv2.setText(hint);
        tv2.setTextSize(12);
        tv2.setTextColor(Color.rgb(130, 130, 130));
        text.addView(tv2);

        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        row.addView(sw);
        root.addView(row);
        return sw;
    }

    private void load() {
        swEnabled.setChecked(sp.getBoolean("enabled", true));
        swMute.setChecked(sp.getBoolean("mute", false));
        swNext.setChecked(sp.getBoolean("auto_next", true));
        swContinue.setChecked(sp.getBoolean("auto_continue", true));
        swIframe.setChecked(sp.getBoolean("iframe_inject", true));
        swQuiz.setChecked(sp.getBoolean("quiz_guess", false));
        swJump.setChecked(sp.getBoolean("jump_end", false));
        swClose.setChecked(sp.getBoolean("auto_close", false));
        float rate = sp.getFloat("rate", 2.0f);
        if (rate < 1.0f) rate = 1.0f;
        if (rate > 8.0f) rate = 8.0f;
        int progress = Math.round((rate - 1.0f) * 10);
        sbRate.setProgress(progress);
        tvRate.setText(String.format(java.util.Locale.US, "当前：%.1fx", rate));
    }

    private void save() {
        float rate = 1.0f + sbRate.getProgress() / 10.0f;
        sp.edit()
                .putBoolean("enabled", swEnabled.isChecked())
                .putBoolean("mute", swMute.isChecked())
                .putBoolean("auto_next", swNext.isChecked())
                .putBoolean("auto_continue", swContinue.isChecked())
                .putBoolean("iframe_inject", swIframe.isChecked())
                .putBoolean("quiz_guess", swQuiz.isChecked())
                .putBoolean("jump_end", swJump.isChecked())
                .putBoolean("auto_close", swClose.isChecked())
                .putFloat("rate", rate)
                .apply();
    }
}