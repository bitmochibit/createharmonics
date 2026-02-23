// Root build script — applies shared configuration to all subprojects.
// Loader-specific logic stays in forge/ and neoforge/ build scripts.

subprojects {
    // Make the root gradle.properties visible to every subproject
    // (Gradle already propagates root properties, but this makes it explicit.)
    afterEvaluate {
        // Ensure every subproject gets UTF-8 encoding on Java compilation
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
    }
}
