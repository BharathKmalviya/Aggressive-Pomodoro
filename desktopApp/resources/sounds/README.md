# Button sound

`click.wav` is Kenney **Interface Sounds 1.0**, `Audio/click_003.ogg`, converted to mono 44.1 kHz signed 16-bit PCM and attenuated to 65% of the original amplitude. The app bundles it for offline playback; a generated click is available if the resource cannot be decoded.

- Source: https://kenney.nl/assets/interface-sounds
- Archive: https://kenney.nl/media/pages/assets/interface-sounds/fa43c1dd4d-1677589452/kenney_interface-sounds.zip
- Retrieved: 2026-10-01
- License: CC0 1.0, https://creativecommons.org/publicdomain/zero/1.0/
- Original license: `Kenney-License.txt` in this directory, included in the app resources.

The default focus and break alarms are original code-generated PCM in `DesktopAlert.kt`, distributed under the repository MIT License. No external codecs or runtime sound downloads are required. The old root `alert.wav` and `click.wav` are legacy unused resources.

## Selectable alarm cues

Nine owner-supplied recordings are adapted into bounded app cues in `alarms/`: up to eight seconds, mono 44.1 kHz signed 16-bit PCM, peak-limited without amplification, with endpoint fades. Focus and break selections are independent; both break types share one choice. These third-party recordings are not relicensed under MIT or CC0. Keep this attribution file in the app resources. Source provenance and measured hashes are in `sounds/catalog.json` in the source repository; the conversion command is documented in `sounds/README.md`.

| App cue | Creator/source recording | Source page | License |
| --- | --- | --- | --- |
| Funny alarm | 3DAbrar, funny Alarm 317531 | https://pixabay.com/sound-effects/funny-alarm-317531/ | Pixabay Content License |
| Classic alarm clock | ecfike (Freesound), via freesound_community, 90867 | https://pixabay.com/sound-effects/technology-alarm-clock-90867/ | Pixabay Content License |
| Kitchen timer | nigelcoop (Freesound), via freesound_community, 33043 | https://pixabay.com/sound-effects/household-kitchen-timer-33043/ | Pixabay Content License |
| Lo-fi alarm | Lesiakower, 243766 | https://pixabay.com/sound-effects/musical-lo-fi-alarm-clock-243766/ | Pixabay Content License |
| Happy bells | Mixkit, happy-bells-notification 937 | https://mixkit.co/free-sound-effects/notification/ | Mixkit Sound Effects Free License |
| Urgent tone | Mixkit, urgent-simple-tone-loop 2976 | https://mixkit.co/free-sound-effects/notification/ | Mixkit Sound Effects Free License |
| Simple alarm | u_inx5oo5fv3, alarm 327234 | https://pixabay.com/sound-effects/film-special-effects-alarm-327234/ | Pixabay Content License |
| Digital alarm clock | Universfield, 151920 | https://pixabay.com/sound-effects/film-special-effects-digital-alarm-clock-151920/ | Pixabay Content License |
| Digital alarm clock — alternate | Universfield, 151927 | https://pixabay.com/sound-effects/digital-alarm-clock-151927/ | Pixabay Content License |

License references checked 2026-10-09: [Pixabay summary](https://pixabay.com/service/license-summary/) and [full terms](https://pixabay.com/service/terms/); [Mixkit Sound Effects Free License](https://mixkit.co/license/#sfxFree). Use these cues within the application, retain applicable third-party terms, and do not redistribute the original recordings as standalone stock assets. The source filenames identify the supplied Mixkit items; their exact titles are retained here.
