plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.verax"
version = "3.5.3"


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

repositories {
    mavenCentral()
    
    // PaperMC Deposu
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    
    // EssentialsX
    maven {
        name = "essentialsx"
        url = uri("https://repo.essentialsx.net/releases/")
    }
    
    // LuckPerms
    maven {
        name = "luckperms"
        url = uri("https://repo.luckperms.net/")
    }

    // PlaceholderAPI
    maven {
        name = "placeholderapi"
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

dependencies {
    // Provided bağımlılıklar (compileOnly)
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.essentialsx:EssentialsX:2.20.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.zaxxer:HikariCP:5.1.0")

    // JAR içine gömülecek bağımlılıklar (implementation)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

// plugin.yml filtreleme (resource filtering)
tasks.processResources {
    val props = mapOf(
        "version" to version,
        "project" to mapOf("version" to version)
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"

    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Shadow/Shade konfigürasyonu
tasks.shadowJar {
    archiveClassifier.set("") // -all ekini kaldırıp ana jar yapar
    
    // Relocation kuralları
    relocate("okhttp3", "com.verax.veraxcore.libs.okhttp3")
    relocate("okio", "com.verax.veraxcore.libs.okio")
    relocate("kotlin", "com.verax.veraxcore.libs.kotlin")

    // Filtreler (Gereksiz dosyaları hariç tutma)
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/**/*.kotlin_module")
    exclude("META-INF/proguard/**")
    exclude("META-INF/versions/**")
    exclude("**/*.kotlin_metadata")
    exclude("**/*.kotlin_builtins")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}