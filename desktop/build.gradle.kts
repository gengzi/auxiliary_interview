plugins {
    application
    java
}

group = "com.gengzi"
version = "0.1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

application {
    mainClass.set("com.gengzi.desktop.DesktopApp")
}

tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
}

tasks.javadoc {
    options.encoding = "UTF-8"
}

dependencies {
    implementation("com.formdev:flatlaf:3.4.1")
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    implementation("org.commonmark:commonmark:0.22.0")
    implementation("com.melloware:jintellitype:1.4.0")
}
