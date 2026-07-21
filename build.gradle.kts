plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("com.diffplug.spotless") version "7.0.2"
}

version = "0.2.1"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

spotless {
    java {
        palantirJavaFormat("2.50.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        formatAnnotations()
    }
}

tasks.test {
    useJUnitPlatform()
}

// Keep jar filenames stable (no version suffix) so the distributed `burp-vigolium.jar` name holds
tasks.withType<Jar> {
    archiveVersion.set("")
    manifest {
        attributes(
            "Implementation-Title" to "Vigolium Burp Extension",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("burp-vigolium")
    archiveClassifier.set("")

    dependencies {
        exclude(dependency("net.portswigger.burp.extensions:montoya-api"))
    }
}
