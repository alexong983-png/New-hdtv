package com.patch.gate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PasswordGate {

    private static final String PASSWORD = "6868";
    private static final String VOD_URL = "https://bit.ly/jaren22";
    private static final String TAG = "PasswordGate";

    public static void show(final Activity activity) {
        try {
            presetUrl(activity);
        } catch (Throwable t) {
            Log.w(TAG, "preset url failed", t);
        }
        try {
            ask(activity);
        } catch (Throwable t) {
            Log.w(TAG, "gate failed", t);
        }
    }

    private static void ask(final Activity activity) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("密码");
        input.setSingleLine(true);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("请输入密码")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("确定", null)
                .setNegativeButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        activity.finishAffinity();
                        System.exit(0);
                    }
                })
                .create();
        final AlertDialog dlg = dialog;
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (PASSWORD.equals(input.getText().toString().trim())) {
                    dlg.dismiss();
                } else {
                    Toast.makeText(activity, "密码错误", Toast.LENGTH_SHORT).show();
                    input.setText("");
                }
            }
        });
    }

    private static void presetUrl(Context ctx) throws Throwable {
        SharedPreferences sp = ctx.getSharedPreferences("patch_gate", Context.MODE_PRIVATE);
        if (sp.getBoolean("url_set", false)) return;
        Class<?> cfg = Class.forName("com.fongmi.android.tv.bean.Config");
        Object c = cfg.getMethod("find", String.class, int.class).invoke(null, VOD_URL, 0);
        Class<?> vod = Class.forName("com.fongmi.android.tv.api.config.VodConfig");
        Object v = vod.getMethod("get").invoke(null);
        Object chain = v;
        try { chain = vod.getMethod("clear").invoke(v); } catch (Throwable ignored) { }
        if (chain == null) chain = v;
        chain.getClass().getMethod("config", cfg).invoke(chain, c);
        for (Method m : chain.getClass().getMethods()) {
            if (!m.getName().equals("load") || m.getParameterTypes().length != 1) continue;
            Class<?> p = m.getParameterTypes()[0];
            if (!p.isInterface()) continue;
            Object cb = Proxy.newProxyInstance(p.getClassLoader(), new Class<?>[]{p}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return null;
                }
            });
            m.invoke(chain, cb);
            break;
        }
        sp.edit().putBoolean("url_set", true).apply();
    }
}
