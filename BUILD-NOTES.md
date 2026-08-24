# Fixed client build

Open this folder itself in IntelliJ—the same folder that contains `build.gradle`, `settings.gradle`, and `gradlew.bat`. Let IntelliJ import it as a Gradle project and use Java 21.

Build from PowerShell with:

```powershell
.\gradlew.bat build
```

The repaired source set was compile-checked against the project's access-widened Minecraft 1.21.11 classes. All 278 Java inputs compiled successfully and emitted 376 class files.

## Repairs made

- Corrected `setting` to `Setting` in `ElytraSwap`.
- Corrected the `Items` import and `autoFirework` spelling in `Pitch40`.
- Corrected the `EPSILON` constant spelling in `RocketBoost`.
- Corrected `@Override` and `getValue()` in `StorageEspModule`.
- Moved `SwingModule` into the required render package.
- Restored the missing `TrailFollower` module.
- Registered `TrailFollower` in `ModuleManager`.
- Confirmed all fourteen requested modules and all four behavior-critical mixins are registered.

The stale `.gradle`, `.idea`, `build`, and `run` folders from the supplied ZIP are deliberately excluded. Gradle and IntelliJ will regenerate them cleanly.
