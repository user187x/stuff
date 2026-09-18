pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        flatDir {
            dirs("app/libs")
        }
    }
}

rootProject.name = "FadCam"
include(":app")

// Include patched Media3 as composite build for live streaming support
// Clone it once: git clone --depth 1 https://github.com/anonfaded/media3-patched.git /tmp/media3-patched
// Or set your own path in local.properties: media3.patched.path=/your/path
//
// ⚠️ media3 is PINNED to 1.8.0 — do NOT bump it to 1.9+/1.10+/1.11+ without
// first updating the media3-patched repo. The composite build below substitutes
// media3-muxer/common/container with the patched forks (lib-muxer/lib-common/
// lib-container), which are built against 1.8.0. Bumping media3 while the
// patch is on 1.8.0 breaks the muxer's hybrid finalization fixes (stsz/trun
// handling). To upgrade: update media3-patched to the new version first, verify
// the muxer patch still applies, then bump `media3` in gradle/libs.versions.toml.
val media3PatchedPath = if (file("local.properties").exists()) {
    val props = java.util.Properties()
    file("local.properties").inputStream().use { props.load(it) }
    props.getProperty("media3.patched.path", "/tmp/media3-patched")
} else {
    "/tmp/media3-patched"
}

if (file(media3PatchedPath).exists()) {
    includeBuild(media3PatchedPath) {
        dependencySubstitution {
            substitute(module("androidx.media3:media3-muxer")).using(project(":lib-muxer"))
            substitute(module("androidx.media3:media3-common")).using(project(":lib-common"))
            substitute(module("androidx.media3:media3-container")).using(project(":lib-container"))
        }
    }
} else {
    logger.warn("⚠️ Patched Media3 not found at: $media3PatchedPath")
    logger.warn("📥 Clone it with: git clone --depth 1 https://github.com/anonfaded/media3-patched.git $media3PatchedPath")
}
 