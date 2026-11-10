// The black.java-standards Gradle plugin: the formatter profile and the compiler warning settings in this repo, enforced
// in a project's build (see README.md).
plugins {
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    // The formatter runs in a worker with its own class loader (see FormatTask), so the plugin only compiles against it.
    compileOnly("org.eclipse.jdt:org.eclipse.jdt.core:3.47.0") // keep in step with JavaStandardsPlugin.JDT_VERSION
}

// The profile and the warning settings, the same files VS Code reads, go into the jar.
tasks.processResources {
    from(files(rootDir.resolve("java-formatter.xml"), rootDir.resolve("java-warnings.prefs"))) { into("black/javastandards") }
}

gradlePlugin {
    plugins {
        create("javaStandards") {
            id = "black.java-standards"
            implementationClass = "black.javastandards.JavaStandardsPlugin"
            displayName = "Java standards"
            description = "Checks formatting (Eclipse JDT, java-formatter.xml) and compiler warnings (ECJ, java-warnings.prefs)."
        }
    }
}
