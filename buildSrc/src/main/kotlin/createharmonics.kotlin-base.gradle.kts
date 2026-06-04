import buildsrc.chVersions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories { mavenCentral() }

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

val v = chVersions

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${v.kotlin}")
    compileOnly(kotlin("reflect"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:${v.kotlinCoroutines}")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:${v.kotlinSerialization}")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-XXLanguage:+WhenGuards")
    }
}

tasks.named<JavaCompile>("compileTestJava") { enabled = false }
tasks.named<KotlinCompile>("compileTestKotlin") { enabled = false }
