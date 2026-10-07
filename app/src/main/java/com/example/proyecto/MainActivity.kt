package com.example.proyecto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.proyecto.data.AnalysisResult
import com.example.proyecto.ui.theme.*
import org.opencv.android.OpenCVLoader

/**
 * Rutas de navegacion (Compose Navigation).
 */
internal object Routes {
    const val PRUEBA = "prueba"
    const val CAMARA = "camara"
    const val HISTORIAL = "historial"
    const val RECOLECCION = "recoleccion"
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            application,
            (application as PlagaScanApplication).repository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preferencia de tema (claro/oscuro) guardada en el telefono
        val preferencias = getSharedPreferences("ajustes", MODE_PRIVATE)

        // OpenCV requiere inicializacion explicita antes de usar cualquier
        // funcion nativa (Mat, Imgproc, etc.) --- sin esto, la app crashea.
        System.loadLibrary("opencv_java4")
        val openCvLoaded = OpenCVLoader.initLocal()
        Log.d("MainActivity", "OpenCV cargado: $openCvLoaded")

        setContent {
            // Tema claro u oscuro, elegido por el usuario desde la barra inferior
            var oscuro by remember { mutableStateOf(preferencias.getBoolean("tema_oscuro", false)) }
            // Barras del sistema (hora, bateria) acordes al tema elegido
            DisposableEffect(oscuro) {
                val transparente = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (oscuro) SystemBarStyle.dark(transparente)
                                     else SystemBarStyle.light(transparente, transparente),
                    navigationBarStyle = if (oscuro) SystemBarStyle.dark(0xFF1A211C.toInt())
                                         else SystemBarStyle.light(
                                             android.graphics.Color.WHITE, android.graphics.Color.WHITE
                                         )
                )
                onDispose { }
            }
            ProyectoTheme(oscuro = oscuro) {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        BarraInferior(navController, oscuro) {
                            val nuevo = !oscuro
                            oscuro = nuevo
                            preferencias.edit().putBoolean("tema_oscuro", nuevo).apply()
                        }
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        BarraDesarrollo(navController)
                        NavHost(
                            navController = navController,
                            startDestination = DevTools.rutaInicio
                        ) {
                            composable(Routes.CAMARA) { CameraModeScreen(viewModel) }
                            composable(Routes.HISTORIAL) { HistoryScreen(viewModel) }
                            DevTools.registrarPantallas(this, viewModel)
                        }
                    }
                }
            }
        }
    }
}

/** Navegacion estandar de pestanas: sin duplicar pantallas y conservando su estado. */
private fun NavHostController.irA(route: String, actual: String?) {
    if (actual == route) return
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Barra de navegacion inferior (Material 3): dos pestanas del mismo tamano,
 * con icono y texto centrados. La pestana activa se resalta en verde.
 */
@Composable
private fun BarraInferior(
    navController: NavHostController,
    oscuro: Boolean,
    onCambiarTema: () -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val actual = backStackEntry?.destination?.route
    val colores = NavigationBarItemDefaults.colors(
        selectedIconColor = PlagaScanGreenText,
        selectedTextColor = PlagaScanGreenText,
        indicatorColor = PlagaScanGreenContainer,
        unselectedIconColor = PlagaScanGray,
        unselectedTextColor = PlagaScanGray
    )
    Column {
        HorizontalDivider(color = PlagaScanOutline)
        NavigationBar(containerColor = PlagaScanSurface, tonalElevation = 0.dp) {
            NavigationBarItem(
                selected = actual == Routes.CAMARA,
                onClick = { navController.irA(Routes.CAMARA, actual) },
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                label = { Text("Cámara", style = MaterialTheme.typography.labelLarge) },
                colors = colores
            )
            NavigationBarItem(
                selected = actual == Routes.HISTORIAL,
                onClick = { navController.irA(Routes.HISTORIAL, actual) },
                icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                label = { Text("Historial", style = MaterialTheme.typography.labelLarge) },
                colors = colores
            )
            // No es una pantalla: alterna entre tema claro y oscuro
            NavigationBarItem(
                selected = false,
                onClick = onCambiarTema,
                icon = {
                    Icon(
                        if (oscuro) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = null
                    )
                },
                label = {
                    Text(if (oscuro) "Claro" else "Oscuro", style = MaterialTheme.typography.labelLarge)
                },
                colors = colores
            )
        }
    }
}

/**
 * Fila de herramientas de desarrollo. Solo aparece en PlagaScan Dev (flavor
 * "recolector"); en la version normal DevTools.rutasExtra esta vacia.
 */
@Composable
private fun BarraDesarrollo(navController: NavHostController) {
    if (DevTools.rutasExtra.isEmpty()) return
    val backStackEntry by navController.currentBackStackEntryAsState()
    val actual = backStackEntry?.destination?.route
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Dev", style = MaterialTheme.typography.labelMedium, color = PlagaScanGray)
        DevTools.BotonesExtra(navigate = { navController.irA(it, actual) }, currentRoute = actual)
    }
}

/** Boton principal de la app: ancho completo, mismo alto y esquinas en todas las pantallas. */
@Composable
internal fun BotonPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PlagaScanGreen,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Pantalla de camara con manejo de permiso en tres estados (no solicitado,
 * denegado, denegado permanentemente -> abrir Configuracion).
 *
 * Flujo: camara (3 fotos) -> "Analizando" -> pantalla de resultado completa
 * (prueba 3 de U3_P1) -> "Hacer otro analisis" regresa a la camara limpia.
 */
@Composable
private fun CameraModeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted && activity != null) {
            val shouldShowRationale = ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            permanentlyDenied = !shouldShowRationale
        }
    }

    // RF08: permiso de ubicacion aproximada, solicitado por separado y de
    // forma NO bloqueante -- si se niega, la camara sigue funcionando.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* resultado ignorado a proposito: no bloquea el flujo */ }

    // RF01: elegir de 1 a 3 fotos ya tomadas con el selector de fotos de
    // Android (no requiere permiso de almacenamiento). Se analizan en cuanto
    // se eligen: 1 foto -> analisis directo; 2-3 -> voto ponderado (RNF02).
    val galeriaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            errorMsg = null
            viewModel.analyzeGalleryImages(uris)
        }
    }

    LaunchedEffect(Unit) {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            // GPS (precisa) funciona sin internet; el usuario puede elegir "aproximada"
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (!hasPermission) {
        PantallaPermiso(
            denegadoPermanente = permanentlyDenied,
            onPedir = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onAbrirConfiguracion = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }
        )
        return
    }

    when {
        uiState.isLoading -> PantallaAnalizando()
        uiState.classification != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                ResultAndHistory(uiState, history)
                Spacer(modifier = Modifier.height(16.dp))
                BotonPrincipal(
                    texto = "Hacer otro análisis",
                    onClick = {
                        errorMsg = null
                        viewModel.resetUiState()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    CameraScreen(
                        onSequenceComplete = { bitmaps: List<Bitmap> ->
                            errorMsg = null
                            viewModel.analyzeSequence(bitmaps, notes = "captura_camara_secuencia")
                        },
                        onError = { msg -> errorMsg = msg },
                        onCaptureStarted = {
                            errorMsg = null
                            viewModel.resetUiState()
                        },
                        onGalleryClick = {
                            galeriaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
                // Los avisos son cortos: se muestran abajo de la camara para
                // que el usuario repita las fotos sin cambiar de pantalla.
                val aviso = errorMsg ?: uiState.errorMessage
                if (aviso != null) {
                    AvisoMensaje(texto = aviso, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PantallaPermiso(
    denegadoPermanente: Boolean,
    onPedir: () -> Unit,
    onAbrirConfiguracion: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(PlagaScanGreenContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = PlagaScanGreenText,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permite usar la cámara",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (denegadoPermanente)
                "La cámara está desactivada para PlagaScan. Actívala en Configuración, en la sección de Permisos."
            else
                "PlagaScan necesita la cámara para tomar las fotos de tus hojas.",
            style = MaterialTheme.typography.bodyLarge,
            color = PlagaScanGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        BotonPrincipal(
            texto = if (denegadoPermanente) "Abrir Configuración" else "Permitir cámara",
            onClick = if (denegadoPermanente) onAbrirConfiguracion else onPedir
        )
    }
}

@Composable
private fun PantallaAnalizando() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = PlagaScanGreen,
            strokeWidth = 4.dp,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Analizando tus fotos…", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Esto tarda solo unos segundos.",
            style = MaterialTheme.typography.bodyLarge,
            color = PlagaScanGray
        )
    }
}

/**
 * Tarjeta de resultado + recordatorio del historial. Tambien la usa el
 * Modo prueba (PruebaScreen) de la version Dev.
 */
@Composable
internal fun ResultAndHistory(uiState: ScanUiState, history: List<AnalysisResult>) {
    ResultCard(uiState)
    if (history.isNotEmpty() && uiState.classification != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (history.size == 1) "Tienes 1 análisis guardado en Historial."
                   else "Tienes ${history.size} análisis guardados en Historial.",
            style = MaterialTheme.typography.bodySmall,
            color = PlagaScanGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
