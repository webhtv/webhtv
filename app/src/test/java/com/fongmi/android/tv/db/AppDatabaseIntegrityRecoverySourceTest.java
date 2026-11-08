package com.fongmi.android.tv.db;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Source contract for the crash-loop guard: Room reports an identity mismatch through an
 * {@code IllegalStateException} while the database is opened, which no call site can catch. The
 * builder must therefore install a helper that quarantines the unreadable database and rebuilds it,
 * while still rethrowing every unrelated open failure.
 */
public class AppDatabaseIntegrityRecoverySourceTest {

    @Test
    public void databaseBuilderInstallsTheIntegrityRecoveryHelper() throws Exception {
        String source = source();
        assertTrue("The Room builder must install the recovering open helper factory",
                source.contains("new IntegrityRecoveringOpenHelperFactory(new FrameworkSQLiteOpenHelperFactory())"));
        assertTrue("The factory must be passed to Room rather than kept unused",
                source.contains(".openHelperFactory("));
    }

    @Test
    public void onlyTheIdentityFailureIsRecovered() throws Exception {
        String source = source();
        String method = between(source, "private static boolean isIdentityFailure", "static void quarantine");

        assertTrue("The recovery must be limited to Room's identity mismatch",
                method.contains("Room cannot verify the data integrity"));
        assertTrue("Unrelated open failures must be rethrown", source.contains("!isIdentityFailure(e)) throw e;"));
        assertFalse("The recovery must not swallow failures by type alone",
                between(source, "private SupportSQLiteDatabase open", "private static boolean isIdentityFailure")
                        .contains("catch (RuntimeException e)"));
    }

    @Test
    public void recoveryIsBoundedToASingleRetry() throws Exception {
        String source = source();
        String method = between(source, "private SupportSQLiteDatabase open", "private static boolean isIdentityFailure");

        assertTrue("A second failure must not retry forever", method.contains("if (recovered || !isIdentityFailure(e)) throw e;"));
        assertTrue("The retry must use a freshly created helper", method.contains("delegate = factory.create(configuration);"));
        assertTrue("The failed helper must be closed before rebuilding", method.contains("delegate.close();"));
    }

    @Test
    public void quarantinePreservesTheUnreadableDatabaseFile() throws Exception {
        String source = source();
        String method = between(source, "static void quarantine", "private static void trimQuarantine");

        assertTrue("The original database file must be renamed aside, not deleted",
                method.contains("source.renameTo(target)"));
        assertTrue("Quarantine copies must stay bounded", source.contains("QUARANTINE_KEEP_COUNT = 2"));
        assertTrue("Sidecar write-ahead log files must be cleared", source.contains("DATABASE_SUFFIXES = {\"\", \"-wal\", \"-shm\", \"-journal\"}"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue("source must contain " + start, from >= 0);
        assertTrue("source must contain " + end, to > from);
        return source.substring(from, to);
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "db", "AppDatabase.java"))), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
