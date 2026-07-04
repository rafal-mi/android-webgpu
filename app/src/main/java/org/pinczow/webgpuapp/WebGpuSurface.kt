package org.pinczow.webgpuapp

import android.util.Log
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pinczow.webgpuapp.App.Companion.TAG

@Composable
fun WebGpuSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Create and remember a WebGpuRenderer instance.
    val renderer = remember { WebGpuRenderer(context) }
    AndroidExternalSurface(
        modifier = modifier.fillMaxSize(),
    ) {
        // This block is called when the surface is created or resized.
        onSurface { surface, width, height ->
            // Run the rendering logic on a background thread.
            withContext(Dispatchers.Default) {
                try {
                    // Initialize the renderer with the surface
                    renderer.init(surface, width, height)
                    // Render a frame.
                    renderer.render()
                } catch (e: Exception) {
                    Log.e(TAG, "Exception when rendering: $e")
                } finally {
                    // Clean up resources when the surface is destroyed.
                    renderer.cleanup()
                }
            }
        }
    }
}
