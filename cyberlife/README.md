# CyberLife Alpha

CyberLife is an Android-first first-person life simulator whose in-world computer can bridge to real Android capabilities with explicit user permission.

## Alpha features

- First-person 3D apartment prototype.
- Touch movement buttons plus mouse/keyboard desktop controls.
- Bed, food, shower and work interactions.
- Time, money, hunger, energy and hygiene simulation.
- Interactive in-world PC.
- Desktop with Terminal, Browser, Files and Phone panels.
- Godot Android Plugin v2 bridge.
- Termux RUN_COMMAND integration: commands typed by the player are sent to the user's installed Termux and stdout/stderr is returned to the in-game terminal.
- Android Storage Access Framework folder picker with persisted user-approved URI access.
- Real web links via Android browser.
- Real device summary (model, Android version, battery, Termux detection).

## Termux setup on Android

1. Install a compatible Termux build.
2. In Termux, set `allow-external-apps=true` in `~/.termux/termux.properties`.
3. In CyberLife, open the in-game PC > Terminal and tap **Grant Termux permission**.
4. Android may expose this under App info > Permissions > Additional permissions > Run commands in Termux environment.
5. Only commands explicitly typed into the in-game terminal are forwarded.

## Development

Godot project: `cyberlife/project.godot`

Android bridge source: `cyberlife/android_plugin`

The GitHub Actions workflow `.github/workflows/cyberlife-alpha.yml` builds the Android bridge AAR, installs it into the Godot addon, then exports a debug Android APK.

## Security model

- No hidden Termux commands.
- No unrestricted filesystem permission.
- File access uses Android's official folder picker.
- Termux execution requires both Android permission and Termux's external-app opt-in.
- Cyber training environments will be separate from real-device operations.
