import com.rohanprabhu.gradle.plugins.kdjooq.*

plugins {
    kotlin("jvm") version "1.4.31"
    id("io.gitlab.arturbosch.detekt").version("1.17.1")
    id("org.flywaydb.flyway").version("7.8.2")
    id("com.rohanprabhu.kotlin-dsl-jooq") version "0.4.6"
}

val jooqDb = mapOf(
    "url" to "jdbc:h2:${project.buildDir}/generated/flyway/h2",
    "schema" to "PUBLIC",
    "user" to "sa",
    "password" to ""
)

flyway {
    url = jooqDb["url"]
    user = jooqDb["user"]
    password = jooqDb["password"]
    schemas = arrayOf(jooqDb["schema"])
    locations = arrayOf("classpath:db/migration")
}

val migrationDirs = listOf(
    "$projectDir/src/flyway/resources/db/migration"
)
tasks.flywayMigrate {
    dependsOn("flywayClasses")
    migrationDirs.forEach { inputs.dir(it) }
    outputs.dir("${project.buildDir}/generated/flyway")
    doFirst { delete(outputs.files) }
}
sourceSets {
    //add a flyway sourceSet
    val flyway by creating {
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }

    main {
        output.dir(flyway.output)
        resources.srcDir(flyway.resources)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven { url = uri("https://dl.bintray.com/arrow-kt/arrow-kt/") }
}

val ktor_version = "1.5.1"
val nee_version = "0.7.2-LOCAL"

dependencies {
    implementation("pl.setblack:nee-ctx-web-ktor:$nee_version")
    detektPlugins("pl.setblack:kure-potlin:0.5.0")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.17.1")
    implementation("io.ktor:ktor-http-jvm:$ktor_version")
    implementation("io.ktor:ktor-jackson:$ktor_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.0")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.12.0")
    implementation("com.h2database:h2:1.4.200")
    implementation("org.jooq:jooq:3.14.9")
    implementation("org.flywaydb:flyway-core:7.8.2")

    jooqGeneratorRuntime("com.h2database:h2:1.4.200")

    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.4.3")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.4.3")
    testImplementation("pl.setblack:nee-ctx-web-test:$nee_version")
}

tasks.withType<Test> {
    useJUnitPlatform()
}


val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileKotlin.kotlinOptions.apply {
    jvmTarget = "1.8"
    javaParameters = true
    allWarningsAsErrors = false
    freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
}

val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileTestKotlin.kotlinOptions.apply {
    jvmTarget = "1.8"
    javaParameters = true
    allWarningsAsErrors = false
    freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
}

detekt {
    toolVersion = "1.17.1"
    input = files("src/main/kotlin")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    this.jvmTarget = "1.8"
    this.classpath.setFrom(compileKotlin.classpath.asPath)
}

tasks {
    "build" {
        dependsOn("detektMain")
    }
}

jooqGenerator {
    jooqVersion = "3.14.9"
    configuration("primary", project.sourceSets.getByName("main")) {
        databaseSources = migrationDirs

        configuration = jooqCodegenConfiguration {
            jdbc {
                username = jooqDb["user"]
                password = jooqDb["password"]
                driver = "org.h2.Driver"
                url = jooqDb["url"]
            }

            generator {
                generate {
                    isImmutablePojos = true
                }
                target {
                    packageName = "dev.neeffect.example.todo.db"
                    directory = "${project.buildDir}/generated/jooq/primary"
                }

                database {
                    name = "org.jooq.meta.h2.H2Database"
                    inputSchema = jooqDb["schema"]
                }
            }
        }
    }
}


val `jooq-codegen-primary` by project.tasks
`jooq-codegen-primary`.dependsOn("flywayMigrate")
project.tasks["compileKotlin"].dependsOn += `jooq-codegen-primary`
