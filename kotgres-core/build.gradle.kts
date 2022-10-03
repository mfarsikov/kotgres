import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.jfrog.bintray")
    id("org.jetbrains.dokka") version "0.10.1"
    id("com.bnorm.power.kotlin-power-assert") version "0.12.0"

    idea
}

repositories {
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.7.20")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks {
    val dokka by getting(DokkaTask::class) {
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"
    }
}

val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    this.archiveClassifier.set("javadoc")
    from(tasks.dokka)
}

publishing {
    publications {
        create<MavenPublication>("bintray") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Kotgres core")
                description.set("Kotlin repository generator for Postgresql (compile time dependencies)")
                url.set("https://github.com/mfarsikov/kotgres")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Max Farsikov")
                        email.set("farsikovmax@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/mfarsikov/kotgres.git")
                    developerConnection.set("scm:git:ssh://github.com/mfarsikov/kotgres.git")
                    url.set("https://github.com/mfarsikov/kotgres")
                }
            }
        }
    }
}

bintray {
    user = System.getenv("BINTRAY_USER")
    key = System.getenv("BINTRAY_KEY")
    setPublications("bintray")
    isPublish = true
    with(pkg) {
        repo = "Kotgres"
        name = "kotgres-core"
        userOrg = System.getenv("BINTRAY_USER")
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/mfarsikov/kotgres"
        with(version) {
            name = project.version.toString()
            desc = "Kotgres compile time dependencies"
            //released = yyyy-MM-dd'T'HH:mm:ss.SSSZZ
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

configure<com.bnorm.power.PowerAssertGradleExtension> {
    functions = listOf("kotlin.assert")
}

tasks.test {
    useJUnitPlatform()
}
