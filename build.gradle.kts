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
    id("java-library")
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.28.0"
    id("com.jfrog.bintray") version "1.8.4"
    id("net.ossindex.audit") version "0.4.11"
    id("io.freefair.github.package-registry-maven-publish") version "4.1.6"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val dependencyVersions = listOf<String>(
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
    constraints {
        implementation("org.slf4j:slf4j-api") {
            version {
                strictly("1.7.30")
            }
        }
        implementation("com.squareup.okio:okio") {
            version {
                strictly("2.5.0")
            }
        }
        listOf("org.jetbrains.kotlin:kotlin-stdlib",
                "org.jetbrains.kotlin:kotlin-stdlib-common").onEach {
            implementation(it) {
                version {
                    strictly("1.3.71")
                }
            }
        }
    }
    implementation("org.slf4j:slf4j-api")
    testRuntimeOnly("org.slf4j:jul-to-slf4j:1.7.30")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.3")

    implementation("com.squareup.okio:okio")
    implementation("com.squareup.okhttp3:okhttp:4.4.1")

    implementation("com.kohlschutter.junixsocket:junixsocket-core:2.3.2")
    implementation("com.kohlschutter.junixsocket:junixsocket-common:2.3.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.6.1")
}

tasks {
    withType(Test::class.java) {
        useJUnitPlatform()
    }

    bintrayUpload {
        dependsOn("build")
    }

    wrapper {
        gradleVersion = "6.3"
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

val publicationName = "dockerFilesocket"
publishing {
    publications {
        register(publicationName, MavenPublication::class) {
            groupId = "de.gesellix"
            artifactId = "docker-filesocket"
            version = rootProject.extra["artifactVersion"] as String
            from(components["java"])
            artifact(sourcesJar.get())
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
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
