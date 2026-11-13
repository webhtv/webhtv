package com.fongmi.android.tv.db;

import com.fongmi.android.tv.utils.AppBackup;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppDatabaseBackupTest {

    @Test
    public void publishesCompletedBackupByReplacingTheTarget() throws Exception {
        Path directory = Files.createTempDirectory("webhtv-backup-");
        File temporary = directory.resolve(".backup.tmp").toFile();
        File target = directory.resolve("bak-20260720-1200.zip").toFile();
        try {
            Files.writeString(temporary.toPath(), "new", StandardCharsets.UTF_8);
            Files.writeString(target.toPath(), "old", StandardCharsets.UTF_8);

            AppDatabase.publishBackup(temporary, target);

            assertEquals("new", Files.readString(target.toPath(), StandardCharsets.UTF_8));
            assertFalse(temporary.exists());
            assertFalse(AppBackup.isBackup(directory.resolve(".backup.tmp").toFile()));
        } finally {
            Files.deleteIfExists(temporary.toPath());
            Files.deleteIfExists(target.toPath());
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void autoBackupNameCarriesAutoMarkerWhileManualNameDoesNot() {
        assertTrue(AppBackup.autoFileName().startsWith(AppBackup.AUTO_PREFIX));
        assertTrue(AppBackup.isAutoBackupName(AppBackup.autoFileName()));
        assertTrue(AppBackup.isAutoBackupName(AppBackup.autoFileName(true)));
        assertFalse(AppBackup.isAutoBackupName(AppBackup.fileName()));
        assertFalse(AppBackup.isAutoBackupName(AppBackup.fileName(true)));
    }

    @Test
    public void cleanAutoOnlyKeepsEveryManualBackupAndDeletesAllAutoBackups() throws Exception {
        Path directory = Files.createTempDirectory("webhtv-backup-auto-retention-");
        File manual = directory.resolve("bak-20260720-1200.zip").toFile();
        File manualPartial = directory.resolve("bak-20260720-1201-partial.zip").toFile();
        File oldAuto = directory.resolve("bak-auto-20260720-1159.zip").toFile();
        File oldAutoPartial = directory.resolve("bak-auto-20260720-1159-partial.zip").toFile();
        File unrelated = directory.resolve("notes.txt").toFile();
        try {
            for (File file : List.of(manual, manualPartial, oldAuto, oldAutoPartial, unrelated)) {
                Files.writeString(file.toPath(), "data", StandardCharsets.UTF_8);
            }

            AppDatabase.cleanAutoOnly(directory.toFile());

            assertTrue("manual backups are never trimmed", manual.exists());
            assertTrue("manual partial backups are never trimmed", manualPartial.exists());
            assertTrue("unrelated files are not touched", unrelated.exists());
            assertFalse("previous auto backup removed", oldAuto.exists());
            assertFalse("previous partial auto backup removed", oldAutoPartial.exists());
        } finally {
            File[] files = directory.toFile().listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void autoBackupGateCoalescesPendingRequests() {
        assertTrue(AppDatabase.beginAutoBackup());
        try {
            assertFalse(AppDatabase.beginAutoBackup());
        } finally {
            AppDatabase.finishAutoBackup();
        }
        assertTrue(AppDatabase.beginAutoBackup());
        AppDatabase.finishAutoBackup();
    }
}
