import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = getVersionString(providers.gradleProperty("pluginVersion").get())

val javaCompilerVersion = "17"
kotlin {
    jvmToolchain(javaCompilerVersion.toInt())
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("dev.gitlive:kotlin-diff-utils:5.0.7")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1") {
        exclude("org.slf4j")
    }
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("io.github.kezhenxu94:cache-lite:0.2.0")

    // test libraries
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:1.68")

    
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        instrumentationTools()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.environmentVariable("PUBLISH_CHANNEL").map { listOf(it) }
    }

    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
        )
        ides {
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = javaCompilerVersion
        targetCompatibility = javaCompilerVersion
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = javaCompilerVersion
    }
}

fun runCommand(cmd: String): String {
    return providers.exec {
        commandLine(cmd.split(" "))
    }.standardOutput.asText.get().trim()
}

fun getVersionString(baseVersion: String): String {
    val tag = runCommand("git tag -l --points-at HEAD")

    if (System.getenv("PUBLISH_EAP") != "1" &&
        tag.isNotEmpty() && tag.contains(baseVersion)) return baseVersion

    val branch = runCommand("git rev-parse --abbrev-ref HEAD").replace("/", "-")
    val numberOfCommits = if (branch == "main") {
        val lastTag = runCommand("git describe --tags --abbrev=0 @^")
        runCommand("git rev-list ${lastTag}..HEAD --count")
    } else {
        runCommand("git rev-list --count HEAD ^origin/main")
    }
    val commitId = runCommand("git rev-parse --short=8 HEAD")
    return if (System.getenv("PUBLISH_EAP") == "1") {
        "$baseVersion.$numberOfCommits-eap-$commitId"
    } else {
        "$baseVersion-$branch-$numberOfCommits-$commitId"
    }
}
