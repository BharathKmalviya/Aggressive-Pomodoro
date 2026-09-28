package com.pomodoro.platform

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class UpdateInstallTest {
    @Test fun backupPreservesExactSavedBytesAndNeverReplacesEarlierBackup() {
        val directory = Files.createTempDirectory("pomodoro-update-backup")
        try {
            val source = directory.resolve("session.properties")
            Files.writeString(source, "version=3\ntask=Important work\n")
            val first = backupBeforeUpdate(directory, "0.2.0", "0.3.0")
            Files.writeString(source, "version=3\ntask=New progress\n")
            val second = backupBeforeUpdate(directory, "0.2.0", "0.3.0")
            assertNotEquals(first, second)
            assertEquals("version=3\ntask=Important work\n", Files.readString(first))
            assertEquals(Files.readString(source), Files.readString(second))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun missingSavedStateBlocksInstallPreparation() {
        val directory = Files.createTempDirectory("pomodoro-update-backup-missing")
        try { assertFailsWith<IOException> { backupBeforeUpdate(directory, "0.2.0", "0.3.0") } }
        finally { directory.toFile().deleteRecursively() }
    }
}
