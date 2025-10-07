import java.text.SimpleDateFormat
import java.util.*

plugins {
  id("java-library")
  id("maven-publish")
  id("signing")
  id("com.github.ben-manes.versions") version "0.53.0"
  id("org.sonatype.gradle.plugins.scan") version "3.1.4"
  id("io.freefair.maven-central.validate-poms") version "9.0.0"
  id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

repositories {
//  mavenLocal()
//  maven {
//    name = "githubPackages"
//    url = uri("https://maven.pkg.github.com/gesellix/okhttp")
//    // username and password (a personal Github access token) should be specified as
//    // `githubPackagesUsername` and `githubPackagesPassword` Gradle properties or alternatively
//    // as `ORG_GRADLE_PROJECT_githubPackagesUsername` and `ORG_GRADLE_PROJECT_githubPackagesPassword`
//    // environment variables
//    credentials(PasswordCredentials::class)
////    credentials {
////      username = System.getenv("GITHUB_ACTOR")
////      password = System.getenv("GITHUB_TOKEN")
////    }
//  }
  mavenCentral()
}

dependencies {
  constraints {
    implementation("org.slf4j:slf4j-api") {
      version {
        strictly(libs.versions.slf4jVersionrange.get())
        prefer(libs.versions.slf4j.get())
      }
    }
    listOf(
      libs.bundles.okio,
    ).forEach {
      implementation(it) {
        version {
          strictly(libs.versions.okioVersionrange.get())
          prefer(libs.versions.okio.get())
        }
      }
    }
    api(libs.okhttp) {
      version {
        strictly(libs.versions.okhttpVersionrange.get())
        prefer(libs.versions.okhttp.get())
      }
    }
    listOf(
      libs.bundles.jna
    ).forEach {
      implementation(it) {
        version {
          strictly(libs.versions.jnaVersionRange.get())
          prefer(libs.versions.jna.get())
        }
      }
    }
    listOf(
      libs.bundles.junixsocket
    ).forEach {
      implementation(it) {
        version {
          strictly(libs.versions.junixsocketVersionrange.get())
          prefer(libs.versions.junixsocket.get())
        }
      }
    }
    listOf(
      libs.bundles.kotlin
    ).forEach {
      implementation(it) {
        version {
          strictly(libs.versions.kotlinVersionrange.get())
          prefer(libs.versions.kotlin.get())
        }
      }
    }
  }
  implementation(libs.slf4j)
  testRuntimeOnly("org.slf4j:jul-to-slf4j:${libs.versions.slf4j.get()}")
  testRuntimeOnly("ch.qos.logback:logback-classic:${libs.versions.logbackVersionrange.get()}!!${libs.versions.logback.get()}")

  api(libs.okhttp)
  implementation(libs.okio)

  implementation("com.kohlschutter.junixsocket:junixsocket-core:${libs.versions.junixsocket.get()}@pom") {
    isTransitive = true
  }
  implementation(libs.junixsocketCommon)
  implementation(libs.bundles.jna)

  testImplementation(libs.okhttpMockwebserver)
  testImplementation(libs.okhttpMockwebserverJunit5)
  testImplementation(libs.okhttpLoggingInterceptor)
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
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
    // https://docs.gradle.org/current/userguide/toolchains.html#comparison_table_for_setting_project_toolchains
//    options.release = 8
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

ossIndexAudit {
  username = System.getenv("SONATYPE_INDEX_USERNAME") ?: findProperty("sonatype.index.username")
  password = System.getenv("SONATYPE_INDEX_PASSWORD") ?: findProperty("sonatype.index.password")
}

nexusPublishing {
  repositories {
    if (!isSnapshot) {
      sonatype {
        // 'sonatype' is pre-configured for Sonatype Nexus (OSSRH) which is used for The Central Repository
        stagingProfileId.set(System.getenv("SONATYPE_STAGING_PROFILE_ID") ?: findProperty("sonatype.staging.profile.id")) //can reduce execution time by even 10 seconds
        username.set(System.getenv("SONATYPE_USERNAME") ?: findProperty("sonatype.username"))
        password.set(System.getenv("SONATYPE_PASSWORD") ?: findProperty("sonatype.password"))
        nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
        snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
      }
    }
  }
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
  languageVersion = JavaLanguageVersion.of(21)
  vendor = JvmVendorSpec.AMAZON
}
