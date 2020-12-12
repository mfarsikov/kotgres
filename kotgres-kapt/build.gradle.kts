plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.jfrog.bintray")
    idea
}

repositories {
    jcenter()
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.2.0")
    implementation("io.github.enjoydambience:kotlinbard:0.4.0")
    implementation(project(":kotgres-core"))

    implementation("com.squareup:kotlinpoet:1.7.2")
}

publishing {
    publications {
        create<MavenPublication>("bintray") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("Kotgres kapt")
                description.set("Kotlin repository generator for Postgresql")
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
        name = "kotgres-kapt"
        userOrg = System.getenv("BINTRAY_USER")
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/mfarsikov/kotgres"
        with(version) {
            name = project.version.toString()
            desc = "Kotgres Kotlin annotation processor"
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
