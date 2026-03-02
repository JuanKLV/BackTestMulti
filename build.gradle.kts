val exposed_version: String by project
val h2_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val mysql_connector_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "com.onoff"
version = "0.0.1"


kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("org.ktorm:ktorm-core:3.6.0")
    implementation("com.mysql:mysql-connector-j:$mysql_connector_version")
    implementation("org.postgresql:postgresql:42.7.2")

    implementation("io.ktor:ktor-server-cors:$ktor_version")

    // WebSocket support
    implementation("io.ktor:ktor-server-websockets:$ktor_version")

    // Coroutines for async/await
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
