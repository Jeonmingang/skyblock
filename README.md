# SamSkyBridge-WhiteBarrier
- Java 8, Spigot/Paper 1.16.5
- Depends on BentoBox (BSkyBlock). Set `${bentobox.version}` in `pom.xml` to match your server.
- Features:
  - Island size upgrade (protection radius + visual white "barrier" using white REDSTONE particles)
  - Shows border when player enters island; clears when leaves
  - Auto-upgrade on IslandLevelEvent based on `upgrade.yml` thresholds
- Commands: `/ssb reload`

## Build
mvn -DskipTests clean package

## Configure
See `upgrade.yml` for radius per tier, `settings.yml` for particle density/period.


## New Features
- `/upgrades` GUI: Island Size & Team Size upgrades (config in `upgrade.yml`).
- Team size upgrades use BentoBox per-island override (`setMaxMembers` for MEMBER_RANK).
- `/rank top|me|recalc`: Ranking by island block value, including Pixelmon blocks if exposed via namespaced keys.
Commit changes
