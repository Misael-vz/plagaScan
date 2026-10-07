# PlagaScan

App Android para la **detección temprana de pulgón amarillo (*Aphis gossypii*) en hoja de frijol**. Analiza las fotos **dentro del teléfono, sin internet**, y responde con un semáforo de riesgo y una recomendación.

Es una **alerta, no un diagnóstico**: no sustituye la revisión de un técnico agrícola.

Proyecto de titulación — Ingeniería en Desarrollo de Software, Centro de Enseñanza Técnica Industrial (CETI), Plantel Tonalá.

## Qué hace

1. **Captura guiada de 3 fotos** (planta completa, hoja y detalle) o selección de 1 a 3 fotos de la galería. Incluye linterna.
2. **Segmentación con OpenCV**: separa la hoja del fondo y la recorta. Si no encuentra una hoja clara, pide repetir la foto.
3. **Clasificación con TensorFlow Lite** (MobileNetV2, transfer learning) en tres clases: `sano`, `plaga` y `no_frijol`.
4. **Voto ponderado** entre las fotos y **semáforo**: verde, amarillo o rojo, con umbral de confianza de 0.80.
5. **Resultado** con foto, nivel de confianza, qué buscar en la hoja, guía visual y qué hacer.
6. **Historial** en el teléfono (Room): fecha, ubicación aproximada opcional, cultivo y etiqueta escrita por el usuario.

La app **solo busca pulgón amarillo**. Por eso el resultado verde dice "Sin pulgón amarillo" y no "Planta sana".

## Alcance y limitaciones

El alcance es un solo cultivo y una sola plaga: frijol y pulgón amarillo.

- **No hay validación con pulgón vivo sobre frijol.** No existen imágenes públicas de ese caso; la clase `plaga` se entrenó con la misma especie sobre otras plantas.
- **No detecta daños pequeños ni otros problemas de la hoja** (manchas, hojas secas, otras plagas). El modelo ve la hoja reducida a 224×224 píxeles.
- **Probada en un solo modelo de teléfono.**

## Tecnología

| Capa | Tecnología |
|---|---|
| App | Kotlin, Jetpack Compose (Material 3), MVVM, Compose Navigation |
| Cámara | CameraX |
| Visión | OpenCV 4.9.0 para Android (módulo `sdk`) |
| Modelo | TensorFlow Lite, MobileNetV2 |
| Datos | Room / SQLite |
| Android | `minSdk 24` (Android 7.0), procesadores `armeabi-v7a` y `arm64-v8a` |

Las versiones exactas de las librerías están en `gradle/libs.versions.toml` y `app/build.gradle.kts`.

## Estructura

```
app/src/main/        Código y recursos comunes; el modelo está en assets/
app/src/normal/      Versión para el usuario final
app/src/recolector/  "PlagaScan Dev": además, herramientas de prueba y recolección de fotos
sdk/                 OpenCV 4.9.0 (solo lo necesario para compilar)
```

Archivos principales en `app/src/main/java/com/example/proyecto/`: `CameraScreen.kt`, `LeafSegmenter.kt`, `PlagaScanClassifier.kt`, `MainViewModel.kt`, `ResultCard.kt`, `HistoryScreen.kt`.

## Cómo compilar

Requisitos: Android Studio con el SDK de Android, **NDK y CMake** (se instalan desde el SDK Manager; los usa el módulo de OpenCV).

1. Clonar el repositorio y abrirlo en Android Studio, o crear `local.properties` con la ruta del SDK (`sdk.dir=...`).
2. Compilar la versión de prueba:

   ```bash
   ./gradlew assembleNormalDebug        # app para el usuario final
   ./gradlew assembleRecolectorDebug    # PlagaScan Dev
   ```

Si `./gradlew` falla por la versión de Java del sistema, usar el Java que trae Android Studio mediante la variable `JAVA_HOME`.

La **versión final firmada** (`assembleNormalRelease`) necesita un keystore propio y un archivo `keystore.properties`, que no forman parte del repositorio.

### Sobre OpenCV

El módulo `sdk` es el OpenCV Android SDK 4.9.0 sin las partes que la compilación no usa: `native/staticlibs`, `native/3rdparty` y las librerías `x86` / `x86_64` (para emuladores). Por eso la app no corre en emuladores x86.

## Datos y créditos

- **Hojas de frijol sanas:** Makerere iBean (licencia MIT).
- **Hojas con pulgón:** LeLePhid (CC BY 4.0) y observaciones de iNaturalist y Wikimedia con licencia Creative Commons, curadas a mano.
- **Fotos de la guía visual dentro de la app:** Alex Abair y Jorge Íñiguez, CC BY 4.0, iNaturalist (recortadas).
- **OpenCV** y **TensorFlow Lite** se distribuyen bajo licencia Apache 2.0.

Los conjuntos de datos y los scripts de entrenamiento no están en este repositorio.

## Equipo

- Brayan Misael Vázquez Mata
- Yahir Alejandro Magaña Chávez
- Carlos Alberto Sahagún Luna

Asesor metodológico: Rigoberto Ibarra Hernández · Asesor técnico: Enrique Emanuel Vega Rivera
