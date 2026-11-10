// What code compiles against. It's a runtime dependency of whatever uses it, so it depends on nothing but the JDK.
plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

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
