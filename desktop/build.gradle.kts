plugins {
    application
    java
}

group = "com.gengzi"
version = "0.1.0"

application {
    mainClass.set("com.gengzi.desktop.DesktopApp")
}

dependencies {
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("org.slf4j:slf4j-simple:2.0.12")
}
