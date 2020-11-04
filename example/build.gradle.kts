import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("com.bnorm.power.kotlin-power-assert") version "0.5.3"
    idea
    application
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

group = "com.example"
java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(project(":orm"))
    implementation("org.postgresql:postgresql:42.2.18")
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("org.flywaydb:flyway-core:7.1.1")

    kapt(project(":orm"))

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
application {
    mainClassName = "my.pack.MainKt"
}