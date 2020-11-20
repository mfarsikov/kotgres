import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("com.bnorm.power.kotlin-power-assert") version "0.5.3"
    idea
    application
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

application {
    mainClassName = "my.pack.MainKt"
}

group = "com.example"
java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
    implementation(project(":kotgres-core"))
    implementation("org.postgresql:postgresql:42.2.18")
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("org.flywaydb:flyway-core:7.1.1")

    kapt(project(":kotgres-kapt"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
    testImplementation("org.testcontainers:postgresql:1.15.0-rc2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.4.10")
}

configure<com.bnorm.power.PowerAssertGradleExtension> {
    functions = listOf("kotlin.assert")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
        useIR = true
    }
}

kapt {
    arguments {
        arg("kotgres.log.level", "debug")
        arg("kotgres.db.qualifiedName", "my.pack.DB")
        arg("kotgres.spring", "false")
    }
}
