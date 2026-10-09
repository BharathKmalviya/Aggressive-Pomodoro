# Alarm source inputs

The owner supplied nine MP3/WAV files here on 2026-10-09. Originals stay intact locally and are ignored by Git; the app uses adapted cues under `desktopApp/resources/sounds/alarms/`. This repository is an application, not a standalone stock-audio collection. Source filenames, creator/page/license references and original/prepared SHA-256 hashes are recorded in [catalog.json](catalog.json). Third-party recordings retain their own licenses, independent of the project's MIT code license.

Normal builds use checked-in cues. To regenerate them with Python and FFmpeg on PATH, run from the repository root:

```powershell
python scripts/prepare-alarm-sounds.py
```

Use `--ffmpeg C:\path\to\ffmpeg.exe` if needed. Conversion keeps up to the first eight seconds, produces mono 44.1 kHz signed 16-bit PCM, reduces peaks above 85% without amplifying quiet recordings, and applies 10 ms/50 ms endpoint fades. Previews and actual alarms use the same prepared cue. Runtime needs no MP3 codec or audio download.

Bundled attribution and license links are in [the app sound credits](../desktopApp/resources/sounds/README.md). Keep those credits with the application's sound resources; adding another file here does not automatically add a selectable alarm.
