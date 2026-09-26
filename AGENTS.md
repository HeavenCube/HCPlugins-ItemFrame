# HCPlugins-ItemFrame

- Java 25, Paper 26.2, Gradle Kotlin DSL.
- HCCore owns `/hcplugins`; contribute `itemframe` through the Core API.
- PacketEvents is a required server dependency and must remain compileOnly.
- Preserve PDC frame identity, canonical drops, native interactions, ItemDisplay proxy cleanup and RGB/profile rendering.
- Keep the local profile carriers aligned with the HeavenCube resource pack catalogue.
- Run `./gradlew build` before finalizing Java or Gradle changes.
- Do not commit, push, reset, rebase, stash, or change branches without explicit user authorization.
