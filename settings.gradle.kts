pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
    }
}

rootProject.name = "HCPlugins-ItemFrame"

val coreBuild = file(".hcplugins/HCPlugins-Core").takeIf { it.isDirectory }
    ?: file("../HCPlugins-Core")
require(coreBuild.resolve("settings.gradle.kts").isFile) {
    "Clone HCPlugins-Core next to this repository before building."
}
includeBuild(coreBuild) {
    dependencySubstitution {
        substitute(module("fr.noltox.hcplugins:core-api")).using(project(":core-api"))
    }
}
