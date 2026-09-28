package com.pomodoro.platform

import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

const val REPOSITORY_URL = "https://github.com/BharathKmalviya/Aggressive-Pomodoro"

fun openProjectPage(url: String) {
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
        (uri.path == "/BharathKmalviya/Aggressive-Pomodoro" ||
            uri.path.startsWith("/BharathKmalviya/Aggressive-Pomodoro/releases")))
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
        throw IOException("Could not open your browser. Visit $REPOSITORY_URL manually.")
    Desktop.getDesktop().browse(uri)
}

/** Called after the controller's final save, before the interactive installer is opened. */
fun backupBeforeUpdate(directory: Path, currentVersion: String, nextVersion: String): Path {
    val versionPattern = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
    require(versionPattern.matches(currentVersion) && versionPattern.matches(nextVersion))
    val source = directory.resolve("session.properties")
    if (!Files.isRegularFile(source)) throw IOException("Your session could not be backed up. Installation was not started.")
    val backups = Files.createDirectories(directory.resolve("backups"))
    val backup = Files.createTempFile(backups, "session-$currentVersion-to-$nextVersion-${System.currentTimeMillis()}-", ".properties")
    try {
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING)
        if (Files.mismatch(source, backup) != -1L) throw IOException("Session backup verification failed.")
        return backup
    } catch (error: Exception) {
        Files.deleteIfExists(backup)
        throw IOException("Your session could not be backed up. Installation was not started.", error)
    }
}

fun launchWindowsInstaller(installer: Path) {
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        throw IOException("This installer requires Windows.")
    if (!Files.isRegularFile(installer) || !installer.fileName.toString().endsWith(".msi"))
        throw IOException("The verified installer is missing. Download the update again.")
    val executable = Path.of(System.getenv("SystemRoot") ?: "C:\\Windows", "System32", "msiexec.exe")
    try {
        // Argument array preserves spaces without a shell; leave installation and UAC interactive.
        ProcessBuilder(executable.toString(), "/i", installer.toAbsolutePath().toString()).start()
    } catch (error: Exception) {
        throw IOException("Windows Installer could not start. Your app is still open; try again.", error)
    }
}
