# Create: Harmonics - 1.1.5

### Features
- **Sable** (_Create Aeronautics_) now supported!
- Ethereal records have now a broken state, and can be repaired in various ways, available on JEI.
- Now when rotational power is supplied before assembly, and after, the sound is continuous, and the speaker will transition to the new state without interrupting.
- New attribute filters for detecting if a record has a url, or if it's broken.
- Now jukeboxes fully support all funnel configurations.
- When a record breaks, the broken record will be put in any container or create input near of it, otherwise it will be dropped on the ground. This also applies on contraptions.
### Optimizations
- Optimized audio players management and contexts are handled efficiently.
- Removed useless wrappers for registers.
- Improved audio synchronization.
### Bug fixes
- Fixed crashes with mods which shaded tukaani (like distant horizons).
- Fixed crashes with mods which have a root package as "mixin", now Create Harmonics has a properly namespaced package.
- Fixed random player kicking, and contraptions not being able to pick up loot.
- Fixed crashes with the record press base.
- Very short audio now will work normally.
- Fixed wrong ffmpeg installation (especially for Linux users) but it affected OSes without specific libraries, required by ffmpeg.
### Translations
- Swedish translation by @Torktumlaren
---

# Create: Harmonics - 1.1.4

### Features
- Now stress usage from jukeboxes is dynamic depending on its state and mode

### Optimizations
- Optimized ffmpeg parameters
- Unified url resolution

### Bug fixes
- ⚠️ (1.20.1) Fixed game crash when Valkyrien Skies was not installed
- Fixed **EventBus** errors spamming in console under certain conditions
- Fixed **ffmpeg** not extracting in Linux
- Fixed **volume** controls being completely wrong 