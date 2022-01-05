plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    java
}

group = "com.hyosakura"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Log
dependencies {
    implementation("org.apache.logging.log4j:log4j-api:2.17.0")
    implementation("org.apache.logging.log4j:log4j-core:2.17.0")
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.1")
}

// Mirai
dependencies {
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-native-mt")
    compileOnly("net.mamoe:mirai-core:2.9.1")
    compileOnly("net.mamoe:mirai-console:2.9.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
}

// Kotlin
dependencies {
    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.jline:jline:3.20.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    implementation("io.ktor:ktor-client-core:1.6.7")
    implementation("io.ktor:ktor-client-okhttp:1.6.7")
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.test {
    useJUnitPlatform()
}