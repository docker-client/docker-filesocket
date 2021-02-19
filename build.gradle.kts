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
    id("com.github.ben-manes.versions") version "0.33.0"
    id("com.jfrog.bintray") version "1.8.5"
    id("net.ossindex.audit") version "0.4.11"
    id("io.freefair.github.package-registry-maven-publish") version "5.2.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val dependencyVersions = listOf<String>()

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
                strictly("[1.7,1.8)")
                prefer("1.7.30")
            }
        }
        implementation("com.squareup.okio:okio") {
            version {
                strictly("[2.5,3)")
                prefer("2.8.0")
            }
        }
        api("com.squareup.okhttp3:okhttp") {
            version {
                strictly("[4,5)")
                prefer("4.9.0")
            }
        }
        listOf("com.kohlschutter.junixsocket:junixsocket-core",
                "com.kohlschutter.junixsocket:junixsocket-common").onEach {
            implementation(it) {
                version {
                    strictly("[2.3,3)")
                    prefer("2.3.2")
                }
            }
        }
        listOf("org.jetbrains.kotlin:kotlin-stdlib",
                "org.jetbrains.kotlin:kotlin-stdlib-common").onEach {
            implementation(it) {
                version {
                    strictly("[1.3,1.5)")
                    prefer("1.4.10")
                }
            }
        }
    }
    implementation("org.slf4j:slf4j-api")
    testRuntimeOnly("org.slf4j:jul-to-slf4j:[1.7,1.8)!!1.7.30")
    testRuntimeOnly("ch.qos.logback:logback-classic:[1.2,2)!!1.2.3")

    api("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okio:okio")

    implementation("com.kohlschutter.junixsocket:junixsocket-core")
    implementation("com.kohlschutter.junixsocket:junixsocket-common")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.7.0")
}

tasks {
    withType(Test::class.java) {
        useJUnitPlatform()
    }

    bintrayUpload {
        dependsOn("build")
    }

  wrapper {
    gradleVersion = "6.8.2"
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
