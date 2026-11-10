// Two modules, which JitPack builds from each tag:
//   lib     com.github.Brendan-Black.black-java:lib:<tag>     what code compiles against, such as black.TypedString
//   plugin  com.github.Brendan-Black.black-java:plugin:<tag>  the black.java-standards Gradle plugin (see README.md)
// The root publishes nothing: on JitPack, com.github.Brendan-Black:black-java:<tag> means all the modules together.
subprojects {
    group = "com.github.Brendan-Black.black-java"
    version = providers.environmentVariable("VERSION").orElse("dev").get().removePrefix("v")

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // Gradle 9 runs on Java 17 or later, so everything here is compiled for 17 and works whatever JDK uses it.
        options.release = 17
        options.compilerArgs.add("-Xlint:all")
    }
}
