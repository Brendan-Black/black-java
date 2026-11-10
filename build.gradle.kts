// The black.java-standards Gradle plugin: the formatter profile and the compiler warning settings in this repo, enforced
// in a project's build (see README.md). JitPack builds each tag into com.github.Brendan-Black:black-java:<tag>.
plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.github.Brendan-Black"
version = providers.environmentVariable("VERSION").orElse("dev").get().removePrefix("v")

repositories {
    mavenCentral()
}

dependencies {
    // The formatter runs in a worker with its own class loader (see FormatTask), so the plugin only compiles against it.
    compileOnly("org.eclipse.jdt:org.eclipse.jdt.core:3.47.0") // keep in step with JavaStandardsPlugin.JDT_VERSION
}

// Gradle 9 runs on Java 17 or later, so the plugin is compiled for 17 and works whatever JDK runs the build.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.add("-Xlint:all")
}

// The profile and the warning settings, the same files VS Code reads, go into the jar.
tasks.processResources {
    from(files("java-formatter.xml", "java-warnings.prefs")) { into("black/javastandards") }
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
