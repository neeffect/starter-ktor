plugins {
    kotlin("jvm") version "1.5.31"
    id("io.gitlab.arturbosch.detekt").version("1.18.1")
}


repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven { url = uri("https://dl.bintray.com/arrow-kt/arrow-kt/") }
}

val ktor_version = "1.6.5"
val nee_version = "0.7.4"

dependencies {
    implementation("pl.setblack:nee-ctx-web-ktor:$nee_version")
    detektPlugins("pl.setblack:kure-potlin:0.6.0")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.16.0")
    implementation("io.ktor:ktor-http-jvm:$ktor_version")
    implementation("io.ktor:ktor-jackson:$ktor_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.13.0")

    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.6.3")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.6.3")
    testImplementation("pl.setblack:nee-ctx-web-test:$nee_version")
}

tasks.withType<Test> {
    useJUnitPlatform()
}


val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileKotlin.kotlinOptions.apply {
    jvmTarget = "1.8"
    javaParameters = true
    allWarningsAsErrors = true
    freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
}

val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileTestKotlin.kotlinOptions.apply {
    jvmTarget = "1.8"
    javaParameters = true
    allWarningsAsErrors = true
    freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    this.jvmTarget = "1.8"
    this.classpath.setFrom(compileTestKotlin.classpath.asPath)
}

tasks {
    "build" {
        dependsOn("detektMain")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
