@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.mahjora.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.mahjora.AppContainer
import fr.mahjora.agents.vision.DetectedHand
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/** Camera preview + capture; the photo is sent to the tile detection agent. */
@Composable
fun ScanScreen(c: AppContainer, onBack: () -> Unit, onResult: (List<String>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var available by remember { mutableStateOf(c.vision.isAvailable) }
    var detected by remember { mutableStateOf<DetectedHand?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
        c.vision.initialize()
        available = c.vision.isAvailable
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Scanner la main") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!available) {
                Text(
                    "Aucun modèle de reconnaissance n'est installé : la détection est désactivée. " +
                        "Saisissez la main à la main en attendant.",
                    Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (granted) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { view ->
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                    )
                } else {
                    Text("L'accès à l'appareil photo est nécessaire.", Modifier.padding(16.dp))
                }
            }
            error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }

            val hand = detected
            if (hand == null) {
                Button(
                    enabled = granted && available,
                    onClick = {
                        error = null
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val jpeg = image.toUprightJpeg()
                                    image.close()
                                    scope.launch {
                                        runCatching { c.vision.detect(jpeg) }
                                            .onSuccess { detected = it }
                                            .onFailure { error = it.message }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    error = exception.message
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Prendre la photo") }
            } else {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${hand.tiles.size} tuiles et ${hand.flowers.size} fleur(s) reconnues", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (hand.tiles + hand.flowers).forEach { TileView(it, width = 28.dp) }
                    }
                    if (hand.uncertain.isNotEmpty()) {
                        Text("${hand.uncertain.size} tuile(s) incertaine(s) : vérifiez-les ensuite.", style = MaterialTheme.typography.bodySmall)
                    }
                    Button({ onResult((hand.tiles + hand.flowers).map { it.code }) }, Modifier.fillMaxWidth()) { Text("Utiliser cette main") }
                    OutlinedButton({ detected = null }, Modifier.fillMaxWidth().padding(bottom = 16.dp)) { Text("Reprendre la photo") }
                }
            }
        }
    }
}

private fun ImageProxy.toUprightJpeg(): ByteArray {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bytes
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotated = android.graphics.Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true,
    )
    return ByteArrayOutputStream().also { rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
}
