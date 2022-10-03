import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.7.20-1.0.6"
    kotlin("plugin.serialization")
    id("com.bnorm.power.kotlin-power-assert") version "0.12.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
    implementation(project(":kotgres-core"))
    implementation("org.postgresql:postgresql:42.5.0")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.flywaydb:flyway-core:9.3.0")

    ksp(project(":kotgres-kapt"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("org.testcontainers:postgresql:1.17.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.7.20")
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
    }
}

ksp {
    arg("kotgres.log.level", "debug")
    arg("kotgres.db.qualifiedName", "my.pack.DB")
    arg("kotgres.spring", "false")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}
