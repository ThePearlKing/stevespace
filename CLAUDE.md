# Steve's Space Program (Forge 1.21.1 mod)

- Build: `./gradlew build` (jar in build/libs). Self-test: `./gradlew runServerTest` (uses run-test/).
- The user plays from `run/` via `./gradlew runClient` — never touch run/ or kill their java.
- Astronomy check: compile `world/planet`, `universe`, `tools` with javac and run `tools.UniverseCheck`; planet maps: `tools.PlanetMapTool`.
- Decompiled MC/Forge sources for API lookups: ~/.cache/stevespace-ref/src
