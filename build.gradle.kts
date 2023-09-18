plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.13.2"
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("org.jetbrains.changelog") version "2.0.0"
    id("org.jetbrains.qodana") version "0.1.13"
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
}

dependencies {
    implementation("dev.gitlive:kotlin-diff-utils:5.0.7")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1") {
        exclude("org.slf4j")
    }
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("io.github.kezhenxu94:cache-lite:0.2.0")
}


group = "com.smallcloud"
version = getVersionString("1.1.53")

repositories {
    mavenCentral()
}


// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2022.2.1")
    type.set("PC") // Target IDE Platform

    plugins.set(listOf("Git4Idea"))
}

val javaCompilerVersion = "17"

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = javaCompilerVersion
        targetCompatibility = javaCompilerVersion
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = javaCompilerVersion
    }

    patchPluginXml {
        sinceBuild.set("222")
        untilBuild.set("232.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

fun String.runCommand(
    workingDir: File = File("."),
    timeoutAmount: Long = 10,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String = ProcessBuilder(split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex()))
    .directory(workingDir)
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .start()
    .apply { waitFor(timeoutAmount, timeoutUnit) }
    .run {
        val error = errorStream.bufferedReader().readText().trim()
        if (error.isNotEmpty()) {
            throw Exception(error)
        }
        inputStream.bufferedReader().readText().trim()
    }

fun getVersionString(baseVersion: String): String {
    val tag = "git tag -l --points-at HEAD".runCommand(workingDir = rootDir)
    if (tag.isNotEmpty() && tag.contains(baseVersion)) return baseVersion

    val branch = "git rev-parse --abbrev-ref HEAD".runCommand(workingDir = rootDir)
    val commitId = "git rev-parse --short=8 HEAD".runCommand(workingDir = rootDir)
    val numberOfCommits = if (branch == "main") {
        val lastTag = "git describe --tags --abbrev=0 @^".runCommand(workingDir = rootDir)
        "git rev-list ${lastTag}..HEAD --count".runCommand(workingDir = rootDir)
    } else {
        "git rev-list --count HEAD ^origin/main".runCommand(workingDir = rootDir)
    }
    return "$baseVersion-$branch-$numberOfCommits-$commitId"
}
