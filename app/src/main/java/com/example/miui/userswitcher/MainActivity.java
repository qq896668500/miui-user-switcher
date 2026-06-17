package com.example.miui.userswitcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    static class UserItem {
        int id;
        String name;
        boolean running;

        UserItem(int id, String name, boolean running) {
            this.id = id;
            this.name = name;
            this.running = running;
        }

        @Override
        public String toString() {
            return name + " (ID:" + id + ")" + (running ? " 运行中" : "");
        }
    }

    private final List<UserItem> users = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button refresh = findViewById(R.id.btnRefresh);
        Button createShortcuts = findViewById(R.id.btnCreateShortcuts);
        listView = findViewById(R.id.listUsers);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        listView.setAdapter(adapter);

        refresh.setOnClickListener(v -> loadUsers());

        createShortcuts.setOnClickListener(v -> {
            createAllShortcuts();
            Toast.makeText(this, "已请求创建快捷方式", Toast.LENGTH_SHORT).show();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            UserItem item = users.get(position);
            showSwitchDialog(item);
        });

        loadUsers();
        createAllShortcuts();
    }

    private void loadUsers() {
        users.clear();
        adapter.clear();

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm list users"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            Pattern pattern = Pattern.compile("UserInfo\\{(\\d+):([^:}]+):[^}]+\\}(.*)");

            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line.trim());
                if (matcher.find()) {
                    int userId = Integer.parseInt(matcher.group(1));
                    String userName = matcher.group(2);
                    String tail = matcher.group(3);
                    boolean running = tail != null && tail.contains("running");
                    UserItem item = new UserItem(userId, userName, running);
                    users.add(item);
                    adapter.add(item.toString());
                }
            }

            process.waitFor();
            adapter.notifyDataSetChanged();

            if (users.isEmpty()) {
                Toast.makeText(this, "未获取到用户列表", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            showError("读取用户失败: " + e.getMessage());
        }
    }

    private void createAllShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "系统版本太低，不支持快捷方式", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            if (shortcutManager == null) {
                Toast.makeText(this, "ShortcutManager 不可用", Toast.LENGTH_SHORT).show();
                return;
            }

            List<ShortcutInfo> shortcuts = new ArrayList<>();

            // 默认入口：打开主界面
            shortcuts.add(buildShortcut("switch_main", "用户切换", 0));

            // 已知常用分身/用户
            shortcuts.add(buildShortcut("switch_11", "切换到 11", 11));
            shortcuts.add(buildShortcut("switch_13", "切换到 13", 13));
            shortcuts.add(buildShortcut("switch_14", "切换到 14", 14));
            shortcuts.add(buildShortcut("switch_15", "切换到 15", 15));
            shortcuts.add(buildShortcut("switch_16", "切换到 16", 16));
            shortcuts.add(buildShortcut("switch_999", "切换到 999", 999));

            shortcutManager.setDynamicShortcuts(shortcuts);
        } catch (Throwable t) {
            showError("创建快捷方式失败: " + t.getMessage());
        }
    }

    private ShortcutInfo buildShortcut(String id, String shortLabel, int userId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("target_user_id", userId);

        return new ShortcutInfo.Builder(this, id)
                .setShortLabel(shortLabel)
                .setLongLabel(shortLabel)
                .setIcon(Icon.createWithResource(this, android.R.mipmap.sym_def_app_icon))
                .setIntent(intent)
                .build();
    }

    private void showSwitchDialog(UserItem item) {
        new AlertDialog.Builder(this)
                .setTitle("切换到 " + item.name)
                .setMessage("ID: " + item.id)
                .setPositiveButton("切换", (d, w) -> switchUser(item.id))
                .setNegativeButton("取消", null)
                .show();
    }

    private void switchUser(int userId) {
        try {
            Intent intent = new Intent();
            intent.setClassName(
                    "com.miui.securityspace",
                    "com.miui.securityspace.ui.activity.SwitchUserActivity"
            );
            intent.putExtra("params_target_user_id", userId);
            startActivity(intent);
        } catch (Throwable t) {
            showError("启动切换失败: " + t.getMessage());
        }
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show();
    }
}
