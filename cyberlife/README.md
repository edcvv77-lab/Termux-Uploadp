# CyberLife Prototype

A first-person Android-oriented life-simulator prototype built with Godot 4.

## Current vertical slice

- First-person movement inside a simple 3D room.
- Interactable computer.
- In-game desktop UI.
- Terminal screen with local preview commands.
- Android bridge abstraction ready for a native plugin.
- Real URL opening through the system browser as the first web milestone.
- Storage Access Framework and Termux integration hooks are defined but intentionally remain mock-only until the native Android plugin is added.

## Run

1. Install Godot 4.3+.
2. Import `cyberlife/project.godot`.
3. Run the project.
4. Walk to the computer and press **E**.

Controls: WASD, mouse, E, Escape.

## Architecture

`scripts/android_bridge.gd` is the only layer game code talks to for device functions. On desktop it runs in mock mode. On Android it will bind to a Godot Android plugin named `CyberLifeBridge`.

Planned native capabilities are permission-gated:

- Termux RUN_COMMAND integration for commands explicitly entered by the player.
- Android Storage Access Framework for user-selected folders.
- Embedded or system web browsing.
- Downloads.
- Battery/network/device status.
- Safe Android intents for user-approved device actions.

The game will not silently execute commands or request unrestricted storage access.

## Next milestone

1. Native Kotlin Android plugin.
2. Termux command result callback (stdout/stderr).
3. Storage folder picker + persisted URI permission.
4. Mobile touch controls.
5. Apartment interactions: bed, food, shower, money, time.
6. PC hardware shop and progression.
7. Separate cyber-lab mode for CTF-style training.
