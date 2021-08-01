group = "de.l.oklab.kieznotiz"
version = "0.1-SNAPSHOT"

plugins {
    application
    kotlin("jvm") version "1.5.20"
    idea
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    jcenter()
    maven("https://repo.maven.apache.org/maven2/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20")
    implementation("com.fasterxml.jackson.core:jackson-core:2.9.9")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.9.9")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.9")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.9")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.9.9")
    implementation("com.github.victools:jsonschema-generator:4.18.0")
    implementation("com.github.jasminb:jsonapi-converter:0.11")
	testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
    implementation(kotlin("stdlib"))
}

application {
    mainClassName = "de.l.oklab.kieznotiz.KieznotizTranformMain"
}



