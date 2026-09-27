plugins { java }

group = "fr.noltox.hcplugins"
version = providers.gradleProperty("version").get()

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    compileOnly("fr.noltox.hcplugins:core-api")
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("fr.noltox.hcplugins:core-api")
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("paper-plugin.yml") { expand("version" to pluginVersion) }
}

tasks.jar {
    archiveFileName.set("HCItemFrame-${project.version}.jar")
}
tasks.test { useJUnitPlatform() }

val glowPack = file(".hcplugins/HCPack-CustomGlowing").takeIf { it.isDirectory }
    ?: file("../HCPack-CustomGlowing")
val verifyGlowPackPalette = tasks.register("verifyGlowPackPalette") {
    val itemFrameConfig = file("src/main/resources/config.yml")
    val packProfiles = glowPack.resolve("assets/heavencube/shaders/include/glow_profiles.glsl")
    require(packProfiles.isFile) {
        "Clone HCPack-CustomGlowing next to this repository before building."
    }
    inputs.file(itemFrameConfig)
    inputs.file(packProfiles)
    doLast {
        val configured = Regex("(?m)^  ([a-z-]+):\\r?\\n    button-name: .*\\r?\\n    color: \"#([0-9A-Fa-f]{6})\"")
            .findAll(itemFrameConfig.readText())
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
        val shader = Regex("(?m)^    if \\(hc_same_carrier\\(carrier, hc_hex\\(0x([0-9A-Fa-f]{6})\\)\\)\\) return [0-9]+; // ([a-z-]+)$")
            .findAll(packProfiles.readText())
            .associate { it.groupValues[2] to it.groupValues[1].uppercase() }
        val shaderOutput = Regex("(?m)^    if \\(profile == [0-9]+\\) return hc_hex\\(0x([0-9A-Fa-f]{6})\\); // ([a-z-]+)$")
            .findAll(packProfiles.readText())
            .associate { it.groupValues[2] to it.groupValues[1].uppercase() }
        check(configured.size == 16 && configured == shader && shader == shaderOutput) {
            "HCItemFrame dye HEX values must match the 16 HCPack-CustomGlowing shader carriers. " +
                "Configured: $configured; pack carriers: $shader; pack output: $shaderOutput"
        }
    }
}
tasks.named("check") { dependsOn(verifyGlowPackPalette) }
