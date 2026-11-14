// The compile-time checks: annotation processors that turn the black style guide's hard rules into compiler errors, under
// javac and ECJ alike. The black.java-standards plugin bundles this jar and puts it on every source set's processor
// path; a build without the plugin can add it as an annotationProcessor dependency.
plugins {
    java
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
                name = "black-java checks"
                description = "Annotation processors that make the Black Java standard's hard rules compiler errors."
                url = "https://github.com/Brendan-Black/black-java"
                licenses { license { name = "MIT"; url = "https://opensource.org/licenses/MIT" } }
            }
        }
    }
}
