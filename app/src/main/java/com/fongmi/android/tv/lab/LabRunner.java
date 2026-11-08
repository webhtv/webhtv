package com.fongmi.android.tv.lab;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LabRunner {

    public interface OutputListener {
        /**
         * 每行输出的投递在 pump 的缓冲锁内调用（其余调用点不在锁内），因此实现必须非阻塞：
         * 只允许投递（如 {@code App.post}）或空实现。
         */
        void onOutput(String text);

        void onExit(int code);
    }

    private static final Map<String, Process> RUNNING = new ConcurrentHashMap<>();
    private static final Map<String, StringBuilder> LOGS = new ConcurrentHashMap<>();
    /** key 最近一次运行用的落盘日志。{@link LabProcManager} 在进程退出/停止后摘掉映射，这里留底，清除仍能找到文件。 */
    private static final Map<String, File> LOG_FILES = new ConcurrentHashMap<>();
    private static final Map<String, java.io.OutputStream> STDIN = new ConcurrentHashMap<>();

    private LabRunner() {
    }

    /**
     * {@code Process#isAlive()} 需要 API 26；低版本用 {@code exitValue()} 探测：
     * 进程仍在运行时它抛 {@code IllegalThreadStateException}。等价语义，API 1 起可用。
     */
    static boolean isAlive(Process process) {
        if (process == null) return false;
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException alive) {
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    public static boolean isRunning(String key) {
        Process process = RUNNING.get(key);
        if (isAlive(process)) return true;
        return LabProcManager.recoveredAlive(key);
    }

    public static int runningCount() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Process> entry : RUNNING.entrySet()) {
            if (isAlive(entry.getValue())) keys.add(entry.getKey());
        }
        for (String key : LabProcManager.recoveredKeys()) {
            keys.add(key);
        }
        return keys.size();
    }

    public static java.util.List<String> runningKeys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Process> entry : RUNNING.entrySet()) {
            if (isAlive(entry.getValue())) keys.add(entry.getKey());
        }
        keys.addAll(LabProcManager.recoveredKeys());
        return new java.util.ArrayList<>(keys);
    }

    public static void stop(String key) {
        Process process = RUNNING.remove(key);
        java.io.OutputStream stdin = STDIN.remove(key);
        if (stdin != null) {
            try {
                stdin.close();
            } catch (Throwable ignored) {
            }
        }
        if (process != null) {
            try {
                process.destroy();
            } catch (Throwable ignored) {
            }
            try {
                process.destroyForcibly();
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                int pid = field.getInt(process);
                LabProcManager.killTree(pid);
            } catch (Throwable ignored) {
            }
            LabProcManager.untrack(key);
        } else {
            LabProcManager.stop(key);
        }
        LabProcManager.updateService();
    }

    public static void stopAll() {
        for (String key : new java.util.ArrayList<>(RUNNING.keySet())) stop(key);
        LabProcManager.stopAll();
        LabProcManager.updateService();
    }

    /**
     * 读取该 key 的日志：内存缓冲优先，空则回落到落盘文件。
     *
     * <p>读缓冲要进 pump 的同一把锁——否则可能读到刚被清除的旧快照，让「清除」在界面上看起来没生效。
     * 代价是调用方在持锁方执行一次 flush 期间等待：pump 每行写盘一次 flush，{@link #clearLog} 则在该锁内
     * 做一次 O_TRUNC 打开（可能落在共享存储上）。{@code onCreate} 与「日志」弹窗都在主线程调用，
     * 慢存储上会有毫秒级等待，属于换取一致性的已知开销，不要为了消除它而取消这里的同步。
     * 缓冲为空时的文件回落读（{@code Files.readAllBytes}）在锁外，不在此等待窗口内。
     */
    public static String getLog(String key) {
        StringBuilder sb = LOGS.get(key);
        if (sb != null) {
            synchronized (sb) {
                if (sb.length() > 0) return sb.toString();
            }
        }
        File file = logFileFor(key);
        if (file != null && file.exists()) {
            try {
                return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    /**
     * 清空一条命令的日志：内存缓冲原地清零（pump 线程持有同一个 {@code StringBuilder} 引用，
     * 换新实例会让之后的输出写进没人再读的旧引用），并截断落盘日志文件，重进输出页不再看到历史叠加。
     *
     * <p>截断必须与 {@code pump} 的写盘落在同一个 {@code synchronized (log)} 临界区里：否则在「清内存」
     * 与「截文件」之间的窗口读出的行会写进文件又被截掉（丢一段刚产生的输出），或只进内存不进文件，
     * 两侧静默分叉。
     *
     * <p>进程退出或停止后 {@link LabProcManager#logFor} 会摘掉映射，此时回落到本进程记录的最近一次
     * 落盘文件：否则「清除」对磁盘静默失效，而 {@code <root>/logs/<pid>_<item>.log} 在配置根指向共享存储
     * 时仍能被文件管理器看到。重新运行会生成新的 {@code <pid>_} 文件，上一轮的旧文件不在此列。
     *
     * <p>限制（无法定位即不清理）：本进程从未 {@code pump} 过该 key、从未读过或清过它（{@code LOG_FILES}
     * 无条目）、且 {@link LabProcManager} 也没有该 key 的映射时，回落取不到文件，{@link #truncate} 直接
     * 返回——此时只有内存与界面被清空，磁盘旧文件仍在。由 {@code recover} 接管、仍然存活的进程不属此类：
     * {@code recover} 自己往 {@link LabProcManager} 写过该 key 的映射，不依赖本进程的 {@code track}。
     *
     * <p>内存缓冲在同一把锁内被原地清零（{@code setLength(0)}，不换实例），因此清除后
     * {@link #getLog} 读到的缓冲为空；若能定位到文件则回落到已截空的文件，返回空串（定位不到时磁盘旧
     * 文件仍在，见上段）。「日志」入口同样走 {@link #getLog}，也不再看到清除前的历史（{@code LOGS}
     * 本身不被移除，只是内容被清空）。{@code computeIfAbsent} 的代价是：从未 pump 过的 key 会因此留下
     * 一个空缓冲，量级受配置里命令 key 的数量约束。
     */
    public static void clearLog(String key) {
        // 与 pump 用同一个 computeIfAbsent 取缓冲：get-then-null-branch 会在「track 已建立映射、
        // pump 还没建缓冲」的窗口里跑到无锁分支，把读者线程随后写的命令头单独截掉。
        StringBuilder sb = LOGS.computeIfAbsent(key, k -> new StringBuilder());
        synchronized (sb) {
            sb.setLength(0);
            truncate(logFileFor(key));
        }
    }

    /**
     * key 对应的落盘文件：{@link LabProcManager#logFor} 优先，并在拿到时记下来。
     * 这样进程被上一轮 App 的恢复路径接管、本进程从未 {@code pump} 过时（没有内存缓冲、
     * 也没有 {@code LOG_FILES} 条目），只要用户在停止前读过/清过一次，停止后仍能定位到文件。
     */
    private static File logFileFor(String key) {
        File file = LabProcManager.logFor(key);
        if (file != null) {
            LOG_FILES.put(key, file);
            return file;
        }
        return LOG_FILES.get(key);
    }

    /** 截断（不是删除）日志文件；进程仍在运行时 append 流按当前文件尾继续写。 */
    private static void truncate(File file) {
        if (file == null) {
            // 自定义命令（runShell 不 track）本来就没有落盘文件，每次清除都告警是噪音；
            // 真正值得留意的是「上一个 App 进程留下的 <pid> 文件」这一类，记 debug 便于排查。
            Log.d("LabRunner", "clearLog: 没有可定位的落盘文件（该 key 可能从未落盘，或旧文件属于上一个 App 进程）");
            return;
        }
        if (!file.exists()) {
            Log.d("LabRunner", "clearLog: 落盘日志已不存在，跳过截断 " + file);
            return;
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            // 打开即 O_TRUNC：截断不 unlink，运行中的 append 流按新的文件尾继续写。
        } catch (Exception error) {
            Log.w("LabRunner", "clearLog: 无法截断 " + file, error);
        }
    }

    public static Process getProcess(String key) {
        return RUNNING.get(key);
    }

    public static boolean writeInput(String key, String line) {
        java.io.OutputStream stdin = STDIN.get(key);
        if (stdin == null) return false;
        try {
            stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Process run(Context context, LabModels.Item item, LabModels.Command command,
                              Map<String, String> vars, OutputListener listener) {
        String key = item.name + "/" + command.id;
        String expanded = expand(context, item, command.command, vars);
        return runShellCommand(context, item, key, expanded, listener);
    }

    public static Process runCustom(Context context, LabModels.Item item, String commandText,
                                    Map<String, String> vars, String key, OutputListener listener) {
        String expanded = expand(context, item, commandText, vars);
        return runShellCommand(context, item, key, expanded, listener);
    }

    public static Process runShellCommand(Context context, LabModels.Item item, String key,
                                          String command, OutputListener listener) {
        stop(key);
        try {
            if (TextUtils.isEmpty(command)) throw new IOException("命令为空");
            File cwd = LabEnv.packageRoot(context, item);
            ProcessBuilder builder = new ProcessBuilder("/system/bin/setsid", "/system/bin/sh", "-c", command);
            builder.directory(cwd);
            builder.redirectErrorStream(true);
            applyEnv(builder.environment(), context, item);
            Process process = builder.start();
            RUNNING.put(key, process);
            try {
                STDIN.put(key, process.getOutputStream());
            } catch (Throwable ignored) {
            }
            String cmdId = key.contains("/") ? key.substring(key.indexOf('/') + 1) : key;
            LabProcManager.track(key, pidOf(process), item.name, cmdId);
            LabProcManager.updateService();
            pump(process, key, command, listener);
            return process;
        } catch (Exception e) {
            if (listener != null) {
                listener.onOutput("错误: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\n");
                listener.onExit(-1);
            }
            return null;
        }
    }

    public static Process runShell(Context context, String shell, String key, OutputListener listener) {
        stop(key);
        try {
            File cwd = LabEnv.localRoot().exists() ? LabEnv.localRoot() : context.getFilesDir();
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-c", shell);
            builder.directory(cwd);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            RUNNING.put(key, process);
            LabProcManager.updateService();
            pump(process, key, shell, listener);
            return process;
        } catch (Exception e) {
            if (listener != null) {
                listener.onOutput("错误: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\n");
                listener.onExit(-1);
            }
            return null;
        }
    }

    public static void applyEnv(Map<String, String> env, Context context, LabModels.Item item) {
        LabEnv.ensure7z(context);
        LabEnv.ensureProot(context);
        File packageDir = LabEnv.packageRoot(context, item);
        Set<String> pathSet = new LinkedHashSet<>();
        pathSet.add(LabEnv.sharedBin(context).getAbsolutePath());
        pathSet.add(LabEnv.prootRoot(context).getAbsolutePath());
        pathSet.add(new File(packageDir, "bin").getAbsolutePath());
        File base = LabEnv.baseRoot(context);
        File[] dirs = base.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (!dir.isDirectory() || "bin".equals(dir.getName()) || dir.equals(packageDir)) continue;
                File bin = new File(dir, "bin");
                if (bin.exists() && bin.isDirectory()) pathSet.add(bin.getAbsolutePath());
            }
        }
        String original = env.get("PATH");
        if (original != null) {
            for (String part : original.split(":")) {
                if (!part.contains("com.termux")) pathSet.add(part);
            }
        }
        pathSet.add("/system/bin");
        pathSet.add("/system/xbin");
        pathSet.add("/vendor/bin");
        pathSet.add("/sbin");
        if (item.var_path != null && item.var_path.containsKey("PATH")) {
            String value = item.var_path.get("PATH");
            if (!TextUtils.isEmpty(value)) {
                for (String part : value.split(":")) {
                    if (!part.contains("com.termux")) {
                        pathSet.add(part.startsWith("/") ? part : new File(packageDir, part).getAbsolutePath());
                    }
                }
            }
        }
        env.put("PATH", join(pathSet, ":"));
        String libDir = LabEnv.sharedBin(context).getAbsolutePath();
        String oldLd = env.get("LD_LIBRARY_PATH");
        if (TextUtils.isEmpty(oldLd)) env.put("LD_LIBRARY_PATH", libDir);
        else if (!oldLd.contains(libDir)) env.put("LD_LIBRARY_PATH", libDir + ":" + oldLd);
        // proot 是动态链接的，它的依赖 .so 放在 sharedBin/proot/ 下，得单独进搜索路径
        File prootDir = LabEnv.prootRoot(context);
        if (prootDir.isDirectory()) {
            String prootPath = prootDir.getAbsolutePath();
            String ld = env.get("LD_LIBRARY_PATH");
            if (!ld.contains(prootPath)) env.put("LD_LIBRARY_PATH", ld + ":" + prootPath);
            env.putAll(LabEnv.prootEnv(context));
        }
        if (item.var_path != null) {
            for (Map.Entry<String, String> e : item.var_path.entrySet()) {
                String key = e.getKey();
                if ("PATH".equals(key)) continue;
                String value = e.getValue();
                if (TextUtils.isEmpty(value)) continue;
                if (!value.startsWith("/") && !value.startsWith("$")) {
                    value = new File(packageDir, value).getAbsolutePath();
                }
                value = replacePlaceholders(context, item, value);
                if ("LD_LIBRARY_PATH".equals(key)) {
                    String old = env.get("LD_LIBRARY_PATH");
                    if (TextUtils.isEmpty(old)) env.put(key, value);
                    else env.put(key, value + ":" + old);
                } else {
                    env.put(key, value);
                }
            }
        }
        Map<String, String> userSettings = LabConfig.get().loadUserSettings(item.name);
        if (item.settings != null && !userSettings.isEmpty()) {
            for (LabModels.Setting setting : item.settings) {
                String value = userSettings.get(setting.key);
                if (value == null || value.isEmpty()) value = setting.defaultValue;
                if (value != null && !value.isEmpty()) {
                    env.put(setting.key, replacePlaceholders(context, item, value));
                }
            }
        }
        applyBinEnvVars(env, context);
        applyGlobalProxy(env, item);
        if (item.name != null && "nodejs".equalsIgnoreCase(item.name)) {
            putIfAbsent(env, "HOME", packageDir.getAbsolutePath());
            String nodeOptions = env.get("NODE_OPTIONS");
            if (nodeOptions == null || !nodeOptions.contains("--max-old-space-size")) {
                env.put("NODE_OPTIONS", nodeOptions == null ? "--max-old-space-size=512" : nodeOptions + " --max-old-space-size=512");
            }
        }
    }

    private static void applyBinEnvVars(Map<String, String> env, Context context) {
        LabModels.LabRoot root = LabConfig.get().getLabRoot();
        if (root == null || root.lists == null) return;
        for (LabModels.Item other : root.lists) {
            if (other == null || other.name == null) continue;
            if (!LabEnv.installed(context, other)) continue;
            String rel = other.binary_path;
            if (TextUtils.isEmpty(rel)) rel = other.cmd_name;
            if (TextUtils.isEmpty(rel)) continue;
            File bin = new File(LabEnv.packageRoot(context, other), rel);
            if (bin.exists()) {
                env.put(other.name.toUpperCase(Locale.ROOT) + "_BIN", bin.getAbsolutePath());
            }
        }
    }

    private static void applyGlobalProxy(Map<String, String> env, LabModels.Item item) {
        if (!LabConfig.get().getGlobalProxy()) return;
        if (item.name != null && "mihomo".equalsIgnoreCase(item.name)) return;
        int port = LabConfig.get().getGlobalProxyPort();
        if (port <= 0) return;
        String http = "http://127.0.0.1:" + port;
        String socks = "socks5://127.0.0.1:" + (port + 1);
        putIfAbsent(env, "http_proxy", http);
        putIfAbsent(env, "https_proxy", http);
        putIfAbsent(env, "HTTP_PROXY", http);
        putIfAbsent(env, "HTTPS_PROXY", http);
        putIfAbsent(env, "all_proxy", socks);
        putIfAbsent(env, "ALL_PROXY", socks);
        String noProxy = LabConfig.get().getGlobalProxyNoProxy();
        if (!TextUtils.isEmpty(noProxy)) {
            putIfAbsent(env, "no_proxy", noProxy);
            putIfAbsent(env, "NO_PROXY", noProxy);
        }
    }

    private static void putIfAbsent(Map<String, String> env, String key, String value) {
        if (!env.containsKey(key) || TextUtils.isEmpty(env.get(key))) {
            env.put(key, value);
        }
    }

    private static String join(Set<String> set, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }

    private static int pidOf(Process process) {
        try {
            java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            return field.getInt(process);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void pump(Process process, String key, String command, OutputListener listener) {
        StringBuilder log = LOGS.computeIfAbsent(key, k -> new StringBuilder());
        File logFile = LabProcManager.logFor(key);
        if (logFile != null) LOG_FILES.put(key, logFile);
        Thread out = new Thread(() -> {
            // 开流与写命令头都在读者线程的锁内：既不让调用线程（主线程）做文件 IO，
            // 也避免 clearLog 落在「开流」与「写头」之间时，头单独冒在被清空的文件里。
            java.io.FileWriter writer = null;
            synchronized (log) {
                if (logFile != null) {
                    try {
                        logFile.getParentFile().mkdirs();
                        writer = new java.io.FileWriter(logFile, true);
                        writer.write("$ " + command + "\n\n");
                        writer.flush();
                    } catch (Exception ignored) {
                    }
                }
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 写盘、写内存、投递 UI 必须同处一把锁：clearLog 在同一临界区里清缓冲并截断文件，
                    // 否则清除前已读出的行会在清除之后才落地，重进输出页又冒出来。
                    synchronized (log) {
                        if (writer != null) {
                            try {
                                writer.write(line + "\n");
                                writer.flush();
                            } catch (Exception ignored) {
                            }
                        }
                        log.append(line).append('\n');
                        if (log.length() > 200000) log.delete(0, log.length() / 2);
                        // 回调实现都是 App.post 或空实现，持锁投递不会阻塞。
                        if (listener != null) listener.onOutput(line + "\n");
                    }
                }
            } catch (Exception ignored) {
            } finally {
                // 流只能由读者线程关：out.join(500) 会超时（setsid wrapper 先退出、子进程仍在写 stdout），
                // 提前 close 会让之后每行写盘静默失败 —— 内存有、落盘没有。
                synchronized (log) {
                    try {
                        if (writer != null) writer.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        out.start();
        new Thread(() -> {
            try {
                int code = process.waitFor();
                try {
                    out.join(500);
                } catch (InterruptedException ignored) {
                }
                RUNNING.remove(key, process);
                STDIN.remove(key);
                int pid = pidOf(process);
                boolean daemon = false;
                long deadline = System.currentTimeMillis() + 2000;
                while (System.currentTimeMillis() < deadline) {
                    if (LabProcManager.trackGroup(key, pid)) {
                        daemon = true;
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                if (daemon) {
                    if (listener != null) listener.onOutput("\n[命令已转入后台运行]\n");
                    LabProcManager.updateService();
                } else {
                    LabProcManager.untrack(key);
                    LabProcManager.updateService();
                    if (listener != null) listener.onExit(code);
                }
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    public static String expand(Context context, LabModels.Item item, String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = expandNested(context, item, template, vars, 0);
        return adaptPackageLookup(replacePlaceholders(context, item, result), context);
    }

    private static String expandNested(Context context, LabModels.Item item, String text, Map<String, String> vars, int depth) {
        if (text == null || depth > 5) return text;
        Matcher matcher = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            if (matcher.start() > 0 && text.charAt(matcher.start() - 1) == '$') {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String value = vars == null ? null : vars.get(matcher.group(1));
            if (value == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacePlaceholders(context, item, value)));
            changed = true;
        }
        matcher.appendTail(sb);
        if (!changed) return sb.toString();
        return expandNested(context, item, sb.toString(), vars, depth + 1);
    }

    private static String adaptPackageLookup(String command, Context context) {
        if (command == null || !command.contains("pm list packages")) return command;
        String pkg = context.getPackageName();
        return Pattern.compile("\\$\\(\\s*pm list packages.*?\\)", Pattern.DOTALL)
                .matcher(command)
                .replaceAll(Matcher.quoteReplacement(pkg));
    }

    private static String replacePlaceholders(Context context, LabModels.Item item, String value) {
        File packageDir = LabEnv.packageRoot(context, item);
        return value
                .replace("{serverPort}", LabConfig.serverPort())
                .replace("{tvPath}", new File(LabEnv.localRoot().getParentFile(), "TV").getAbsolutePath())
                .replace("{dataPath}", new File(packageDir, "data").getAbsolutePath())
                .replace("{cachePath}", context.getCacheDir().getAbsolutePath())
                .replace("{envRootPath}", packageDir.getAbsolutePath())
                .replace("{wwwroot}", new File(LabEnv.localRoot(), "wwwroot").getAbsolutePath())
                .replace("{sdcard}", "/storage/emulated/0");
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && !inSingle) {
                escaped = true;
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }
}
