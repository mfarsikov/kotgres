plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
    idea
}

repositories {
    jcenter()
}
java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.2.0")
    implementation("io.github.enjoydambience:kotlinbard:0.4.0")
    implementation(project(":kotgres-core"))

    implementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
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
                name.set("Kewt ORM")
                description.set("Kotlin ORM for Postgres")
                url.set("https://github.com/mfarsikov/kewt-ORM")
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
                    connection.set("scm:git:git://github.com/mfarsikov/kewt-orm.git")
                    developerConnection.set("scm:git:ssh://github.com/mfarsikov/kewt-orm.git")
                    url.set("https://github.com/mfarsikov/kewt-orm")
                }
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}