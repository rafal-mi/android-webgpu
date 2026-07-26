package org.pinczow.webgpuapp

import android.util.Log
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.pinczow.webgpuapp.App.Companion.TAG

@Composable
fun WebGpuSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidExternalSurface(
        modifier = modifier.fillMaxSize(),
    ) {
        // This block is called when the surface is created or resized.
        onSurface { surface, width, height ->
            // Create a fresh renderer for this surface session.
            val renderer = WebGpuRenderer(context)
            // Run the rendering logic on a background thread.
            withContext(Dispatchers.Default) {
                try {
                    // Initialize the renderer with the surface
                    renderer.initialize(surface, width, height)
                    // Render a loop while the scope is active.
                    while (isActive) {
                        renderer.render()
                        delay(16)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception when rendering: $e")
                } finally {
                    renderer.cleanup()
                }
            }
        }
    }
}
