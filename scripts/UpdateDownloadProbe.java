import com.pomodoro.domain.AppRelease;
import com.pomodoro.platform.GitHubUpdateService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import kotlin.Unit;

/** Network-only release gate. Never starts the UI, installs an MSI or touches the user profile. */
public final class UpdateDownloadProbe {
    public static void main(String[] args) throws Exception {
        for (String notice : new String[]{"/licenses/NOTICE.txt", "/licenses/Apache-2.0.txt"}) {
            try (var resource = UpdateDownloadProbe.class.getResourceAsStream(notice)) {
                if (resource == null || resource.readAllBytes().length == 0)
                    throw new IllegalStateException("Packaged dependency notice missing: " + notice);
            }
        }
        GitHubUpdateService service = new GitHubUpdateService();
        AppRelease release = service.check("0.0.0").getUpdate();
        if (release == null) throw new IllegalStateException("No stable Windows installer to verify");
        Path installer = service.download(release, Path.of(args[0]), (received, total) -> {
            if (received.equals(total)) System.out.println("Received " + received + " installer bytes");
            return Unit.INSTANCE;
        }, () -> false, stage -> {
            System.out.println("Stage: " + stage);
            return Unit.INSTANCE;
        });
        service.verifyBeforeInstall(installer, release);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(installer)) {
            byte[] buffer = new byte[32768];
            for (int count; (count = stream.read(buffer)) >= 0;) digest.update(buffer, 0, count);
        }
        System.out.println("Verified updater download: v" + release.getVersion() + "; bytes=" + Files.size(installer)
            + "; sha256=" + HexFormat.of().formatHex(digest.digest()));
    }
}
