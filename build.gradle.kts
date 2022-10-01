import java.text.SimpleDateFormat
import java.util.*

plugins {
  id("java-library")
  id("maven-publish")
  id("signing")
  id("com.github.ben-manes.versions") version "0.42.0"
  id("net.ossindex.audit") version "0.4.11"
  id("io.freefair.maven-central.validate-poms") version "6.5.1"
  id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

repositories {
  mavenCentral()
}

dependencies {
  constraints {
    implementation("org.slf4j:slf4j-api") {
      version {
        strictly("[1.7,1.8)")
        prefer("1.7.36")
      }
    }
    listOf(
      "com.squareup.okio:okio",
      "com.squareup.okio:okio-jvm"
    ).forEach {
      implementation(it) {
        version {
          strictly("[3,4)")
          prefer("3.2.0")
        }
      }
    }
    api("com.squareup.okhttp3:okhttp") {
      version {
        strictly("[4,5)")
        prefer("4.10.0")
      }
    }
    listOf(
      "com.kohlschutter.junixsocket:junixsocket-core",
      "com.kohlschutter.junixsocket:junixsocket-common"
    ).onEach {
      implementation(it) {
        version {
          strictly("[2.4,3)")
          prefer("2.5.1")
        }
      }
    }
    listOf(
      "org.jetbrains.kotlin:kotlin-stdlib",
      "org.jetbrains.kotlin:kotlin-stdlib-common",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk7",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
    ).onEach {
      implementation(it) {
        version {
          strictly("[1.5,1.8)")
          prefer("1.7.10")
        }
      }
    }
  }
  implementation("org.slf4j:slf4j-api:2.0.3")
  testRuntimeOnly("org.slf4j:jul-to-slf4j:2.0.3")
  testRuntimeOnly("ch.qos.logback:logback-classic:[1.2,2)!!1.2.11")

  api("com.squareup.okhttp3:okhttp:4.10.0")
  implementation("com.squareup.okio:okio-jvm:3.2.0")

  implementation("com.kohlschutter.junixsocket:junixsocket-core:2.5.1@pom") {
    isTransitive = true
  }
  implementation("com.kohlschutter.junixsocket:junixsocket-common:2.5.1")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.9.1")
}

val dependencyVersions = listOf<String>(
)

val dependencyGroupVersions = mapOf<String, String>()

configurations.all {
  resolutionStrategy {
    failOnVersionConflict()
    force(dependencyVersions)
    eachDependency {
      val forcedVersion = dependencyGroupVersions[requested.group]
      if (forcedVersion != null) {
        useVersion(forcedVersion)
      }
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
  withType(Test::class.java) {
    useJUnitPlatform()
  }
}

val javadocJar by tasks.registering(Jar::class) {
  dependsOn("classes")
  archiveClassifier.set("javadoc")
  from(tasks.javadoc)
}

val sourcesJar by tasks.registering(Jar::class) {
  dependsOn("classes")
  archiveClassifier.set("sources")
  from(sourceSets.main.get().allSource)
}

artifacts {
  add("archives", sourcesJar.get())
  add("archives", javadocJar.get())
}

fun findProperty(s: String) = project.findProperty(s) as String?

val isSnapshot = project.version == "unspecified"
val artifactVersion = if (!isSnapshot) project.version as String else SimpleDateFormat("yyyy-MM-dd\'T\'HH-mm-ss").format(Date())!!
val publicationName = "dockerFilesocket"
publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/${property("github.package-registry.owner")}/${property("github.package-registry.repository")}")
      credentials {
        username = System.getenv("GITHUB_ACTOR") ?: findProperty("github.package-registry.username")
        password = System.getenv("GITHUB_TOKEN") ?: findProperty("github.package-registry.password")
      }
    }
  }
  publications {
    register(publicationName, MavenPublication::class) {
      pom {
        name.set("docker-filesocket")
        description.set("Unix Domain Sockets and Named Pipes for the JVM on Linux, macOS, and Windows")
        url.set("https://github.com/docker-client/docker-filesocket")
        licenses {
          license {
            name.set("MIT")
            url.set("https://opensource.org/licenses/MIT")
          }
        }
        developers {
          developer {
            id.set("gesellix")
            name.set("Tobias Gesellchen")
            email.set("tobias@gesellix.de")
          }
        }
        scm {
          connection.set("scm:git:github.com/docker-client/docker-filesocket.git")
          developerConnection.set("scm:git:ssh://github.com/docker-client/docker-filesocket.git")
          url.set("https://github.com/docker-client/docker-filesocket")
        }
      }
      artifactId = "docker-filesocket"
      version = artifactVersion
      from(components["java"])
      artifact(sourcesJar.get())
      artifact(javadocJar.get())
    }
  }
}

signing {
  val signingKey: String? by project
  val signingPassword: String? by project
  useInMemoryPgpKeys(signingKey, signingPassword)
  sign(publishing.publications[publicationName])
}

nexusPublishing {
  repositories {
    if (!isSnapshot) {
      sonatype {
        // 'sonatype' is pre-configured for Sonatype Nexus (OSSRH) which is used for The Central Repository
        stagingProfileId.set(System.getenv("SONATYPE_STAGING_PROFILE_ID") ?: findProperty("sonatype.staging.profile.id")) //can reduce execution time by even 10 seconds
        username.set(System.getenv("SONATYPE_USERNAME") ?: findProperty("sonatype.username"))
        password.set(System.getenv("SONATYPE_PASSWORD") ?: findProperty("sonatype.password"))
      }
    }
  }
}
