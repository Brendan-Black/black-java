// The black.java-standards Gradle plugin: the formatter profile, the compiler warning settings and the compile-time
// checks in this repo, enforced in a project's build (see README.md).
plugins {
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    mavenCentral()
}

// The checks module's jar, which goes into the plugin's jar whole (see processResources).
val checks by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    // The formatter runs in a worker with its own class loader (see FormatTask), so the plugin only compiles against it.
    compileOnly("org.eclipse.jdt:org.eclipse.jdt.core:3.47.0") // keep in step with JavaStandardsPlugin.JDT_VERSION
    checks(project(":checks"))
}

// The profile, the warning settings (the same files VS Code reads) and the checks jar go into the jar.
tasks.processResources {
    from(files(rootDir.resolve("java-formatter.xml"), rootDir.resolve("java-warnings.prefs"))) { into("black/javastandards") }
    from(checks) {
        into("black/javastandards")
        rename { "black-checks.jar" } // keep in step with ExtractStandardsTask.CHECKS
    }
}

gradlePlugin {
    plugins {
        create("javaStandards") {
            id = "black.java-standards"
            implementationClass = "black.javastandards.JavaStandardsPlugin"
            displayName = "Java standards"
            description = "Checks formatting (Eclipse JDT, java-formatter.xml), compiler warnings (ECJ, java-warnings.prefs) and the hard rules (compiler errors)."
        }
    }
}
