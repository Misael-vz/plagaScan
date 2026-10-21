import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Carga las credenciales de firma de release desde un archivo NO
// versionado (keystore.properties, excluido en .gitignore). Si el
// archivo no existe (por ejemplo, en la maquina de otro integrante del
// equipo que no tiene el keystore), el build sigue funcionando para
// debug -- solo no podra generar un APK de release firmado.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val keystoreFileExists = keystorePropertiesFile.exists()
if (keystoreFileExists) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        if (keystoreFileExists) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    namespace = "com.example.proyecto"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.proyecto"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
           }
        // RNF05: limita las librerias nativas empaquetadas a las
        // arquitecturas de telefonos reales. x86/x86_64 son para
        // emuladores de escritorio -- incluirlas duplicaba el peso
        // de OpenCV (~130MB de 159MB del APK original) sin beneficio
        // para el usuario final (productores rurales con telefonos
        // ARM de gama de entrada).
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        }
    androidResources {
        noCompress += "tflite"
    }

    // Dos modalidades desde el mismo codigo fuente:
    // - "normal": la app real para el usuario final (RF01-RF08). No incluye
    //   Modo prueba ni Recoleccion -- ese codigo ni siquiera se compila
    //   dentro de este flavor, no solo se oculta.
    // - "recolector": todo lo anterior, mas Modo prueba y Recoleccion --
    //   herramientas de desarrollo/validacion de campo, para uso del
    //   equipo (y para mostrar el proceso de validacion a los sinodales
    //   si se solicita).
    flavorDimensions += "modalidad"
    productFlavors {
        create("normal") {
            dimension = "modalidad"
            buildConfigField("boolean", "MODO_RECOLECTOR", "false")
        }
        create("recolector") {
            dimension = "modalidad"
            applicationIdSuffix = ".recolector"
            versionNameSuffix = "-recolector"
            buildConfigField("boolean", "MODO_RECOLECTOR", "true")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // RNF05: shrink + ofuscacion de codigo activados. Antes
            // estaban desactivados, dejando codigo muerto y recursos
            // sin usar dentro del APK de release.
            // Firma con la clave de release real (plagascan-release-key.jks)
            // si keystore.properties existe en la maquina; si no, cae de
            // vuelta a la firma de debug para que el equipo pueda seguir
            // compilando localmente sin el keystore.
            signingConfig = if (keystoreFileExists) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
  
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // Iconos de Material (camara, historial, ubicacion...). R8 elimina los
    // que no se usan en el APK de release (RNF05).
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.tensorflow.lite)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation(project(":sdk"))
}
