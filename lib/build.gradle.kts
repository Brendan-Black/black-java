import java.util.zip.ZipFile

// What code compiles against. It's a runtime dependency of whatever uses it, so it depends on nothing but the JDK and
// TypedString, which is its own repository so a library can implement it without pulling in the rest of lib.
plugins {
    `java-library`
    `maven-publish`
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    api("com.github.Brendan-Black:TypedString:1.0.0")
}

java {
    withSourcesJar()
    withJavadocJar()
}

// The tests are a plain main() (no test framework).
val runTests = tasks.register<JavaExec>("runTests") {
    description = "Runs src/test/java/black/LibTest."
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "black.LibTest"
}
tasks.test { enabled = false }

// lib must not carry its own copy of TypedString, and must pass TypedString on to whatever compiles against it: in the
// POM at compile scope (Maven), and in the module metadata's apiElements (Gradle).
val checkPackaging = tasks.register("checkPackaging") {
    description = "Fails if lib's jar holds TypedString, or its POM or module metadata doesn't pass it on."
    group = "verification"
    val jar = tasks.jar
    val pom = tasks.named<GenerateMavenPom>("generatePomFileForMavenPublication")
    val metadata = tasks.named<GenerateModuleMetadata>("generateMetadataFileForMavenPublication")
    dependsOn(jar, pom, metadata)
    doLast {
        val entries = ZipFile(jar.get().archiveFile.get().asFile).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        check("black/TypedString.class" !in entries) { "lib's jar holds its own copy of black/TypedString.class" }

        val pomText = pom.get().destination.readText()
        val typedString = Regex("<artifactId>TypedString</artifactId>.*?<scope>(.*?)</scope>", RegexOption.DOT_MATCHES_ALL).find(pomText)
        check(typedString?.groupValues?.get(1) == "compile") { "the POM doesn't list TypedString at compile scope" }

        val json = groovy.json.JsonSlurper().parse(metadata.get().outputFile.get().asFile) as Map<*, *>
        val apiElements = (json["variants"] as List<*>).map { it as Map<*, *> }.single { it["name"] == "apiElements" }
        val apiModules = (apiElements["dependencies"] as List<*>? ?: listOf<Any>()).map { (it as Map<*, *>)["module"] }
        check("TypedString" in apiModules) { "the module metadata's apiElements don't list TypedString: $apiModules" }
    }
}
tasks.check { dependsOn(runTests, checkPackaging) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "black-java lib"
                description = "Common types for code written to the Black Java standard."
                url = "https://github.com/Brendan-Black/black-java"
                licenses { license { name = "MIT"; url = "https://opensource.org/licenses/MIT" } }
            }
        }
    }
}
