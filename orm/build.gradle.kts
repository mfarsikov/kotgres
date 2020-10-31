plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
    idea
    application
}

repositories {
    jcenter()
}
java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.2.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation( "org.postgresql:postgresql:42.2.18")
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("io.github.enjoydambience:kotlinbard:0.4.0")

    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    implementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
    implementation("com.squareup:kotlinpoet:1.7.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    mainClassName = "postgres.json.AppKt"
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