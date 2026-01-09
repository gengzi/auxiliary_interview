plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.5"
    java
}

group = "com.gengzi"
version = "0.1.0"

extra["springAiVersion"] = "1.0.0-M5"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

dependencies {
    implementation ("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")


    testImplementation("org.springframework.boot:spring-boot-starter-test")
}



tasks.test {
    useJUnitPlatform()
}
