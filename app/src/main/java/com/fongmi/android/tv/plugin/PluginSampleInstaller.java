package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 内置示例插件的一次性登记。
 *
 * <p><b>为什么需要它。</b>{@code assets/plugin/} 里的样本装进 APK 却从不进
 * {@link PluginSourceConfig}，用户装完看不到任何插件，只能自己去文件管理器里翻一个含
 * manifest.json 的目录手动导入——而资产目录在 APK 内部，用户根本翻不到。结果是"内置了
 * 示例"对用户完全不可见。
 *
 * <p><b>为什么不复用 {@link PluginPackageImporter}。</b>导入器的输入是一个真实目录，
 * 而样本在 assets 里；它的入口解析还会主动拒绝 {@code assets://} 前缀，因为装载期需要
 * 一个能直接读的路径。这里做的事相同但来源不同：读资产、落盘、把清单 {@code runtime.entry}
 * 改写成脚本绝对路径，登记到配置里。
 *
 * <p><b>为什么只做一次。</b>登记过就打修订号。用户删掉这个插件（管理页删除会连目录带
 * 条目一起清）之后不该在下次启动时"复活"——那不是内置推荐，是删不掉的东西。
 *
 * <p><b>为什么任何失败都不抛。</b>调用点在 {@code App.onCreate} 投递的后台任务与插件
 * 管理页的导入回调里。内置样本是锦上添花，它的失败只该意味着"这次没有样本"。
 */
public final class PluginSampleInstaller {

    /** 登记修订号。将来替换内置样本时 +1，已经种过旧样本的设备会再走一次。 */
    static final int SEED_REVISION = 1;

    /** 记账键：存的是已登记到的修订号，而不是布尔值。 */
    static final String KEY_SEEDED = "plugin_sample_seed_revision";

    private static final String MANIFEST_NAME = PluginSourceConfig.MANIFEST_NAME;
    private static final String ASSETS_PREFIX = "assets://";

    /**
     * 内置样本的资产目录。
     *
     * <p>这里只记目录：id、名字、版本、脚本文件名全部从清单里读，避免同一个事实在
     * 清单和 Java 里各写一份、改一边忘另一边。
     */
    private static final List<String> SAMPLES = List.of("plugin/douban");

    /**
     * 记账后端。
     *
     * <p>真机走 {@code Prefers}；JVM 单测注入内存实现——{@code Prefers} 依赖
     * {@code Init.context()}，单测里没有 Context。
     */
    interface Store {

        int revision();

        void mark(int revision);
    }

    /**
     * 资产读取后端。
     *
     * <p>真机走 {@link Asset#read}；单测注入内存实现，同上（{@code Init.context()} 在
     * JVM 单测里不存在）。资产缺失时约定返回空串，而不是 {@code null}。
     */
    interface Source {

        String read(String name);
    }

    private static final Store PREFS_STORE = new Store() {

        @Override
        public int revision() {
            try {
                return Prefers.getInt(KEY_SEEDED);
            } catch (Throwable ignored) {
                // 读不到就当成"从没种过"：最坏是重复一次登记，而登记本身是幂等的。
                return 0;
            }
        }

        @Override
        public void mark(int revision) {
            try {
                Prefers.put(KEY_SEEDED, revision);
            } catch (Throwable ignored) {
                // 记账失败只意味着下次启动会再走一遍登记（每遍都被 find(id) 挡在复制之前）。
            }
        }
    };

    private static final Source ASSET_SOURCE = Asset::read;

    private static volatile Store store = PREFS_STORE;
    private static volatile Source source = ASSET_SOURCE;

    private PluginSampleInstaller() {
    }

    /**
     * 把内置样本登记进配置；已经登记过就直接返回。
     *
     * @return 本次新登记了几条样本（0 表示无需处理）
     */
    public static int seedOnce() {
        Store localStore = store;
        int revision;
        try {
            revision = localStore.revision();
        } catch (Throwable ignored) {
            revision = 0;
        }
        if (revision >= SEED_REVISION) return 0;

        Source localSource = source;
        int seeded = 0;
        boolean settled = true;
        for (String assetsDir : SAMPLES) {
            int result = seed(assetsDir, localSource);
            if (result > 0) seeded += result;
            else if (result < 0) settled = false;
        }
        // 只有每条样本都"种下或已登记"才记账。空读（样本不在本包里）算已处理：
        // 资产内容随版本固定，重试永远是同一个空串。真正的失败是清单非法或落盘失败，
        // 那种情况不记账，下次启动还有机会补上。
        if (settled) {
            try {
                localStore.mark(SEED_REVISION);
            } catch (Throwable ignored) {
            }
        }
        return seeded;
    }

    /**
     * 种一条样本。
     *
     * @return {@code 1} 本次新登记；{@code 0} 无需处理（已登记或样本不在本包里）；{@code -1} 失败
     */
    private static int seed(@Nullable String assetsDir, Source source) {
        if (assetsDir == null || assetsDir.isEmpty()) return 0;
        try {
            String manifestText = source.read(assetsDir + "/" + MANIFEST_NAME);
            // 空串 = 这个版本的 assets 里没有这条样本。不是失败，没什么可重试的。
            if (manifestText == null || manifestText.isEmpty()) return 0;
            PluginManifest manifest = PluginManifest.parse(manifestText);
            if (manifest == null) return -1;
            String id = manifest.getId();
            if (!PluginSourceConfig.isSafeId(id)) return -1;

            PluginSourceConfig config = PluginSourceConfig.get();
            // 已登记就一行都不覆盖：可能是用户自己导入的同一 id，也可能上一条样本刚种下。
            if (config.find(id) != null) return 0;

            String entryName = fileNameOf(manifest.getRuntimeEntry());
            if (entryName == null) return -1;
            String script = source.read(assetPathOf(assetsDir, manifest.getRuntimeEntry()));
            if (script == null || script.isEmpty()) return -1;
            if (script.getBytes(StandardCharsets.UTF_8).length > PluginScriptSource.MAX_SCRIPT_BYTES) return -1;

            File directory = new File(PluginPackageImporter.pluginsRoot(), id);
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) return -1;
            File entryFile = new File(directory, entryName);
            if (!write(entryFile, script)) return -1;
            String rewritten = rewriteEntry(manifestText, entryFile);
            if (rewritten == null) return -1;
            if (!write(new File(directory, MANIFEST_NAME), rewritten)) return -1;

            // 用改写后的清单登记，而不是资产里那份原文：装载期看到的就是这份，
            // 两边一旦不一致，配置里的元信息会开始说谎。
            PluginManifest registered = PluginManifest.parse(rewritten);
            if (registered == null) return -1;
            config.put(new PluginSourceConfig.Entry(registered.getId(), registered.getName(),
                    registered.getVersion(), registered.getRuntimeApi(), directory.getAbsolutePath(),
                    true, System.currentTimeMillis()));
            config.save();
            return 1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * 清单入口指向的资产路径。
     *
     * <p>内置样本的入口写成 {@code assets://plugin/douban/douban.js}——正是导入器拒绝、
     * 而 {@link PluginScriptSource} 能读的形态。没写前缀时按"相对样本目录"处理。
     */
    private static String assetPathOf(String assetsDir, @Nullable String entry) {
        String raw = entry == null ? "" : entry.trim();
        if (raw.startsWith(ASSETS_PREFIX)) return raw.substring(ASSETS_PREFIX.length());
        return assetsDir + "/" + raw;
    }

    @Nullable
    private static String fileNameOf(@Nullable String entry) {
        String raw = entry == null ? "" : entry.trim().replace('\\', '/');
        int slash = raw.lastIndexOf('/');
        String name = slash < 0 ? raw : raw.substring(slash + 1);
        if (name.isEmpty() || name.contains("..")) return null;
        return name;
    }

    /**
     * 把清单的 {@code runtime.entry} 改写为脚本绝对路径。
     *
     * <p>与 {@link PluginPackageImporter} 的改写保持同一形态，装载期不需要再做任何 JSON
     * 处理。改写失败返回 {@code null}：宁可这次不登记，也不留一份指向 assets 的清单——
     * 那份清单在设备上永远读不到，用户会得到"插件装了但不工作"。
     */
    @Nullable
    private static String rewriteEntry(String manifestText, File entryFile) {
        try {
            JsonElement root = JsonParser.parseString(manifestText);
            if (root == null || !root.isJsonObject()) return null;
            JsonObject object = root.getAsJsonObject();
            JsonElement runtime = object.get("runtime");
            if (runtime == null || !runtime.isJsonObject()) return null;
            runtime.getAsJsonObject().addProperty("entry", entryFile.getAbsolutePath());
            return object.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean write(File file, String text) {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 测试注入：换记账与资产后端。传 {@code null} 恢复真机实现。包内可见，不扩大公开面。 */
    static void useBackendsForTest(@Nullable Store testStore, @Nullable Source testSource) {
        store = testStore == null ? PREFS_STORE : testStore;
        source = testSource == null ? ASSET_SOURCE : testSource;
    }
}
