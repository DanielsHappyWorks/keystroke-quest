# Keystroke Quest for IntelliJ

IntelliJ Platform plugin that began as a port of the VS Code "Ridiculous Coding" extension: blips, booms, screen shake,
synthesized sounds, and an XP / level system with fireworks.

## Run

```
gradle wrapper        # once, or just open the folder in IntelliJ and let it import
./gradlew runIde      # sandbox IDE with the plugin
./gradlew buildPlugin # zip in build/distributions
```

Requires JDK 21, IntelliJ Platform 2024.3+ (build 243+), any IDE built on the platform.

## Mapping from the VS Code extension "Ridiculous Coding"

| VS Code | IntelliJ |
|---|---|
| Decorations / webview effects | Transparent Swing overlay on the editor's content component (`EffectsOverlay`) |
| Screen shake | Jitters the editor's scroll offsets, restored each frame |
| Webview audio / native helper | Tones synthesized at runtime with `javax.sound` (`SoundEngine`), no assets or helper binary |
| `globalState` XP | App-level `PersistentStateComponent` (`ridiculousCoding.xml`) |
| Status bar item | `StatusBarWidget` (click opens the tool window) |
| Explorer panel | "Keystroke Quest" tool window |
| `ridiculousCoding.*` settings (VS Code) | Settings → Tools → Keystroke Quest |
| Commands | Tools → Keystroke Quest → Toggle Reduced Effects / Toggle Sound / Export Stats Card / Reset Progress |

## Extra features (beyond the VS Code version)

Combo streak, daily streak and stats, achievements, level titles, run/test result effects,
themes and sound packs (Neon, Retro 8-bit, Minimal), caret trail, line/block-complete sparkles,
and break reminders. All toggleable in Settings → Tools → Keystroke Quest.

## Release checklist (JetBrains Marketplace)

1. Set a real `<vendor url="..." email="...">` in `plugin.xml` and the copyright holder in `LICENSE`.
2. `./gradlew verifyPlugin` checks compatibility against recommended IDE versions (downloads IDEs).
3. Sign and publish with secrets in the environment, never in the repo:
   `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`, then
   `./gradlew signPlugin publishPlugin`.
4. Add screenshots on the Marketplace listing page, and bump `version` in `build.gradle.kts` plus
   `CHANGELOG.md` / `<change-notes>` for each release.

## Credits

Inspired by the VS Code extension "Ridiculous Coding", the Godot plugin "ridiculous_coding" by John Watson,
and Textreme2 by Ash K.
