import java.text.SimpleDateFormat
import java.util.*

plugins {
  id("java-library")
  id("maven-publish")
  id("signing")
  id("com.github.ben-manes.versions") version "0.50.0"
  id("net.ossindex.audit") version "0.4.11"
  id("io.freefair.maven-central.validate-poms") version "8.4"
  id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

repositories {
  mavenCentral()
}

dependencies {
  constraints {
    implementation("org.slf4j:slf4j-api") {
      version {
        strictly("[1.7,3)")
        prefer("2.0.11")
      }
    }
    listOf(
      "com.squareup.okio:okio",
      "com.squareup.okio:okio-jvm",
    ).forEach {
      implementation(it) {
        version {
          strictly("[3,4)")
          prefer("3.7.0")
        }
      }
    }
    api("com.squareup.okhttp3:okhttp") {
      version {
        strictly("[4,5)")
        prefer("4.12.0")
      }
    }
    listOf(
      "com.kohlschutter.junixsocket:junixsocket-core",
      "com.kohlschutter.junixsocket:junixsocket-common"
    ).forEach {
      implementation(it) {
        version {
          strictly("[2.4,3)")
          prefer("2.8.3")
        }
      }
    }
    listOf(
      "org.jetbrains.kotlin:kotlin-stdlib",
      "org.jetbrains.kotlin:kotlin-stdlib-common",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk7",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
    ).forEach {
      implementation(it) {
        version {
          strictly("[1.6,1.10)")
          prefer("1.9.22")
        }
      }
    }
  }
  implementation("org.slf4j:slf4j-api:2.0.11")
  testRuntimeOnly("org.slf4j:jul-to-slf4j:2.0.11")
  testRuntimeOnly("ch.qos.logback:logback-classic:[1.2,2)!!1.3.14")

  api("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okio:okio:3.7.0")

  implementation("com.kohlschutter.junixsocket:junixsocket-core:2.8.3@pom") {
    isTransitive = true
  }
  implementation("com.kohlschutter.junixsocket:junixsocket-common:2.8.3")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
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
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(8))
  }
}

tasks {
  withType<JavaCompile> {
    options.encoding = "UTF-8"
  }
  withType<Test> {
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
