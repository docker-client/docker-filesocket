import com.jfrog.bintray.gradle.BintrayExtension
import java.text.SimpleDateFormat
import java.util.*

rootProject.extra.set("artifactVersion", SimpleDateFormat("yyyy-MM-dd\'T\'HH-mm-ss").format(Date()))
rootProject.extra.set("bintrayDryRun", false)

buildscript {
    repositories {
        mavenLocal()
        jcenter()
        mavenCentral()
    }
}

plugins {
    java
    `maven-publish`
    id("com.github.ben-manes.versions") version "0.27.0"
    id("com.jfrog.bintray") version "1.8.4"
    id("net.ossindex.audit") version "0.4.11"
    id("io.freefair.github.package-registry-maven-publish") version "4.1.5"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val dependencyVersions = listOf(
        "com.squareup.okio:okio:2.4.1",
        "org.jetbrains.kotlin:kotlin-stdlib:1.3.60",
        "org.jetbrains.kotlin:kotlin-stdlib-common:1.3.60"
)

configurations.all {
    resolutionStrategy {
        failOnVersionConflict()
        force(dependencyVersions)
    }
}

repositories {
    mavenLocal()
    jcenter()
    mavenCentral()
}

dependencies {
    compile("org.slf4j:slf4j-api:1.7.25")
    testRuntime("org.slf4j:jul-to-slf4j:1.7.25")
    testRuntime("ch.qos.logback:logback-classic:1.2.3")

    compile("com.squareup.okio:okio:2.4.1")
    compile("com.squareup.okhttp3:okhttp:4.2.2")

    compile("com.kohlschutter.junixsocket:junixsocket-core:2.2.1")
    compile("com.kohlschutter.junixsocket:junixsocket-common:2.2.1")

    testCompile("org.junit.jupiter:junit-jupiter-api:5.4.0")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:5.4.0")
    testRuntime("org.junit.platform:junit-platform-launcher:1.4.0")
}

tasks {
    withType(Test::class.java) {
        useJUnitPlatform()
    }

    bintrayUpload {
        dependsOn("build")
    }

    wrapper {
        gradleVersion = "6.0.1"
        distributionType = Wrapper.DistributionType.ALL
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn("classes")
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

artifacts {
    add("archives", sourcesJar.get())
}

fun MavenPom.addDependencies() = withXml {
    asNode().appendNode("dependencies").let { depNode ->
        configurations.compile.get().allDependencies.forEach {
            depNode.appendNode("dependency").apply {
                appendNode("groupId", it.group)
                appendNode("artifactId", it.name)
                appendNode("version", it.version)
            }
        }
    }
}

val publicationName = "dockerFilesocket"
publishing {
    publications {
        register(publicationName, MavenPublication::class) {
            groupId = "de.gesellix"
            artifactId = "docker-filesocket"
            version = rootProject.extra["artifactVersion"] as String
            from(components["java"])
//            artifact(sourcesJar.get())
        }
    }
}

fun findProperty(s: String) = project.findProperty(s) as String?

rootProject.github {
    slug.set("${project.property("github.package-registry.owner")}/${project.property("github.package-registry.repository")}")
    username.set(System.getenv("GITHUB_ACTOR") ?: findProperty("github.package-registry.username"))
    token.set(System.getenv("GITHUB_TOKEN") ?: findProperty("github.package-registry.password"))
}

bintray {
    user = System.getenv()["BINTRAY_USER"] ?: findProperty("bintray.user")
    key = System.getenv()["BINTRAY_API_KEY"] ?: findProperty("bintray.key")
    setPublications(publicationName)
    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        repo = "docker-utils"
        name = "docker-filesocket"
        desc = "Unix Domain Sockets and Named Pipes for the JVM on Linux, macOS, and Windows"
        setLicenses("Apache-2.0")
        setLabels("docker", "unix socket", "linux", "mac", "named pipe", "windows", "engine api", "remote api", "client", "java")
        version.name = rootProject.extra["artifactVersion"] as String
        vcsUrl = "https://github.com/docker-client/docker-filesocket.git"
    })
    dryRun = rootProject.extra["bintrayDryRun"] as Boolean
}
