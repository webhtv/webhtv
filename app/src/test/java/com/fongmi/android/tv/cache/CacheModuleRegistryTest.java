package com.fongmi.android.tv.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CacheModuleRegistryTest {

    @Test
    public void registryDefinesEveryModuleExactlyOnce() throws Exception {
        File cache = cacheDir("registry-ids");
        List<CacheModule> modules = CacheModuleRegistry.modules(cache);

        EnumSet<CacheModuleId> ids = EnumSet.noneOf(CacheModuleId.class);
        for (CacheModule module : modules) {
            assertTrue("duplicate id " + module.id(), ids.add(module.id()));
            assertFalse("module without roots: " + module.id(), module.roots().isEmpty());
        }
        assertEquals(EnumSet.allOf(CacheModuleId.class), ids);
    }

    @Test
    public void everyRootStaysInsideCacheDir() throws Exception {
        File cache = cacheDir("registry-inside");
        String cacheKey = cache.getCanonicalPath();

        for (CacheModule module : CacheModuleRegistry.modules(cache)) {
            for (CacheRoot root : module.roots()) {
                String key = root.root().getCanonicalPath();
                assertTrue(module.id() + " escapes cache dir: " + key,
                        key.equals(cacheKey) || key.startsWith(cacheKey + File.separator));
            }
        }
    }

    @Test
    public void productionRegistryPassesValidation() throws Exception {
        File cache = cacheDir("registry-valid");
        List<CacheModule> modules = CacheModuleRegistry.modules(cache);
        assertEquals(List.of(), CacheModuleRegistry.validate(cache, modules));
    }

    @Test
    public void validationRejectsRootOutsideCacheDir() throws Exception {
        File cache = cacheDir("registry-escape");
        File outside = cacheDir("registry-outside");
        ArrayList<CacheModule> modules = new ArrayList<>(CacheModuleRegistry.modules(cache));
        modules.add(module(CacheModuleId.EXO, new File(outside, "exo")));

        List<String> problems = CacheModuleRegistry.validate(cache, modules);

        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("escapes cache dir")));
    }

    @Test
    public void validationRejectsNestedTreeRoots() throws Exception {
        File cache = cacheDir("registry-nested");
        ArrayList<CacheModule> modules = new ArrayList<>();
        modules.add(module(CacheModuleId.EXO, new File(cache, "outer")));
        modules.add(module(CacheModuleId.LYRICS, new File(cache, "outer/inner")));

        List<String> problems = CacheModuleRegistry.validate(cache, modules);

        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("nested tree root")));
    }

    @Test
    public void validationRejectsDuplicateTreeRoot() throws Exception {
        File cache = cacheDir("registry-duplicate");
        ArrayList<CacheModule> modules = new ArrayList<>();
        modules.add(module(CacheModuleId.EXO, new File(cache, "shared")));
        modules.add(module(CacheModuleId.LYRICS, new File(cache, "shared")));

        List<String> problems = CacheModuleRegistry.validate(cache, modules);

        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("overlapping tree roots")));
    }

    @Test
    public void registryIsPureFunctionOfCacheDir() throws Exception {
        File first = cacheDir("registry-pure-a");
        File second = cacheDir("registry-pure-b");

        Set<CacheModuleId> firstIds = idsOf(CacheModuleRegistry.modules(first));
        Set<CacheModuleId> secondIds = idsOf(CacheModuleRegistry.modules(second));
        assertEquals(firstIds, secondIds);

        for (CacheRoot root : rootsOf(CacheModuleRegistry.modules(second))) {
            assertTrue("root must follow the supplied cache dir: " + root.root(),
                    root.root().getCanonicalPath().startsWith(second.getCanonicalPath()));
        }
        assertEquals(CacheModuleRegistry.modules(first), CacheModuleRegistry.modules(first));
    }

    private static Set<CacheModuleId> idsOf(List<CacheModule> modules) {
        return new HashSet<>(modules.stream().map(CacheModule::id).toList());
    }

    private static List<CacheRoot> rootsOf(List<CacheModule> modules) {
        ArrayList<CacheRoot> roots = new ArrayList<>();
        for (CacheModule module : modules) roots.addAll(module.roots());
        return roots;
    }

    private static CacheModule module(CacheModuleId id, File root) {
        return new CacheModule() {
            @Override
            public CacheModuleId id() {
                return id;
            }

            @Override
            public CacheGroup group() {
                return CacheGroup.LEGACY;
            }

            @Override
            public List<CacheRoot> roots() {
                return List.of(CacheRoot.tree(root));
            }

            @Override
            public CacheCleanability cleanability() {
                return CacheCleanability.SAFE_NOW;
            }

            @Override
            public CacheEvictionPolicy evictionPolicy() {
                return CacheEvictionPolicy.AGE_BASED;
            }

            @Override
            public CacheProtection protection() {
                return new CacheProtection(true, true, false, false, 0, 0, false);
            }
        };
    }

    private static File cacheDir(String prefix) throws Exception {
        Path base = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        return Files.createTempDirectory(base, prefix).toFile();
    }
}
