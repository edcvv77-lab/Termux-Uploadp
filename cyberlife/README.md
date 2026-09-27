# CyberLife Alpha

CyberLife is an Android-first first-person life simulator whose in-world computer can bridge to real Android capabilities with explicit user permission.

## Current Alpha

- First-person apartment prototype with touch movement.
- Bed, food, shower and work interactions.
- Time, money, hunger, energy, hygiene and health.
- Automatic persistent save file under Godot's app storage.
- Interactive in-world PC.
- Real Termux command bridge with stdout/stderr returned to the in-game terminal.
- Real Android folder selection through Storage Access Framework.
- In-game Files tab lists real items from the user-approved phone folder and can open selected files.
- Real WebView browser inside the CyberLife app with Back / Reload / Close controls.
- Web downloads use Android DownloadManager and save to Downloads.
- Device status panel.
- External browser fallback.

## Termux setup

1. Install a compatible Termux build.
2. Add `allow-external-apps=true` to `~/.termux/termux.properties`.
3. Run `termux-reload-settings`.
4. In CyberLife PC > Terminal, tap **Grant Termux permission** and approve Android's Run commands in Termux permission.

Only commands explicitly entered by the player are forwarded.

## File access

CyberLife does not request unrestricted storage. In PC > Files, choose a folder using Android's system picker. Android grants access only to that user-approved tree and CyberLife persists the URI permission where the provider allows it.

## Browser

PC > Browser opens a real Android WebView inside the app. Downloads initiated from the WebView are sent through Android DownloadManager to the public Downloads folder.

## Development

Godot project: `cyberlife/project.godot`

Android bridge source: `cyberlife/android_plugin`

CI workflow: `.github/workflows/cyberlife-alpha.yml`

## Security model

- No hidden Termux commands.
- No unrestricted filesystem permission.
- File access uses Android's Storage Access Framework.
- Termux execution requires Android permission plus Termux's external-app opt-in.
- Offensive cyber-training content will remain isolated from real-device operations.
