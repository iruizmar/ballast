@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.gradle.kotlin.dsl.invoke

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.intellij")
    id("org.jetbrains.changelog")
}

var projectVersion: ProjectVersion by project.extra
val publishConfiguration: PublishConfiguration = Config.publishConfiguration(project)

configurations.all {
    resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    type.set("IC")
    version.set("2022.1.4")
    downloadSources.set(true)

    plugins.set(
        listOf(
            "org.jetbrains.kotlin",
//            "org.jetbrains.compose.intellij.platform:0.1.0",
        )
    )
}

tasks {
    buildSearchableOptions {
        // temporary workaround
        enabled = false
    }

    signPlugin {
        certificateChain.set(publishConfiguration.jetbrainsMarketplaceCertificateChain)
        privateKey.set(publishConfiguration.jetbrainsMarketplacePrivateKey)
        password.set(publishConfiguration.jetbrainsMarketplacePassphrase)
    }

    publishPlugin {
        token.set(publishConfiguration.jetbrainsMarketplaceToken)
    }

    patchPluginXml {
        sinceBuild.set("221")
        untilBuild.set("222.*")
    }

    runPluginVerifier {
        ideVersions.set(listOf("2020.3.2", "2021.1", "2022.1", "2022.1.4"))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-opt-in=kotlin.ExperimentalStdlibApi"
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        freeCompilerArgs += "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        freeCompilerArgs += "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        freeCompilerArgs += "-opt-in=androidx.compose.material.ExperimentalMaterialApi"
        freeCompilerArgs += "-opt-in=org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi"
    }
}
