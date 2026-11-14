# Akou — music recognition

![Telos Akou](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-20-akou.webp)


Tap **Record and recognize** and grant microphone permission. Akou listens for ten seconds only while this screen is open, computes a Shazam-compatible audio fingerprint on the phone, then asks Shazam to match that fingerprint. No account or API key is needed. The original microphone audio is never uploaded and is cleared from memory after the attempt. Leaving the screen cancels listening or the pending request.

Open Άκου’s settings menu and enable **Listen automatically when opened** to request one sample each time the screen is entered. It is off by default. Grant microphone access once using **Record and recognize**; automatic entry does not repeatedly prompt after a denial. The setting applies on the next entry. Rotation and ordinary redraws do not request a second sample, and leaving still cancels the current work.

A successful match displays the returned title, artist and album when supplied. History is stored locally and can be cleared. **Find in YouTube Music** opens a search for that title and artist in **Telos YouTube Music** or, when installed and enabled, the official **YouTube Music** app. The official choice uses an explicit package intent, so it still works when Telos handles music links by default; a returned match does not guarantee a particular YouTube recording.

Quiet recordings, unsupported microphones, missing connectivity, rate limits and unknown songs produce an error or no match, never a sample song. Recognition needs Internet access; the unofficial third-party endpoint can change or become unavailable. There is no continuous background listening.

Fingerprinting is based on Audile and SongRec through InterTune's GPL-3.0 Kotlin port. See `THIRD_PARTY_NOTICES.md` for source revisions and test-audio attribution.

The regression suite compares all 488 peaks of an attributed Audile reference recording: counts, timestamps and frequencies match exactly. Magnitudes allow one integer quantization unit because the Kotlin and Rust FFTs order floating point operations differently (487 of 488 magnitudes match exactly in this fixture). The reference signature container separately round-trips byte for byte.
