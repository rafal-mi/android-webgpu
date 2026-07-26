package org.pinczow.webgpuapp

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.webgpu.BufferUsage
import androidx.webgpu.GPUBuffer
import androidx.webgpu.GPUBufferDescriptor
import androidx.webgpu.GPUColor
import androidx.webgpu.GPUColorTargetState
import androidx.webgpu.GPUDevice
import androidx.webgpu.GPUFragmentState
import androidx.webgpu.GPUPipelineLayout
import androidx.webgpu.GPUPipelineLayoutDescriptor
import androidx.webgpu.GPUPrimitiveState
import androidx.webgpu.GPURenderPassColorAttachment
import androidx.webgpu.GPURenderPassDescriptor
import androidx.webgpu.GPURenderPipeline
import androidx.webgpu.GPURenderPipelineDescriptor
import androidx.webgpu.GPUShaderModule
import androidx.webgpu.GPUShaderModuleDescriptor
import androidx.webgpu.GPUShaderSourceWGSL
import androidx.webgpu.GPUSurfaceConfiguration
import androidx.webgpu.GPUVertexAttribute
import androidx.webgpu.GPUVertexBufferLayout
import androidx.webgpu.GPUVertexState
import androidx.webgpu.LoadOp
import androidx.webgpu.PrimitiveTopology
import androidx.webgpu.StoreOp
import androidx.webgpu.SurfaceGetCurrentTextureStatus
import androidx.webgpu.TextureFormat
import androidx.webgpu.VertexFormat
import androidx.webgpu.VertexStepMode
import androidx.webgpu.helper.WebGpu
import androidx.webgpu.helper.createWebGpu
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.pinczow.webgpuapp.App.Companion.TAG
import org.pinczow.webgpuapp.shader.TextResourceReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WebGpuRenderer(val context: Context) {
    private var webGpu: WebGpu? = null
    private var renderPipeline: GPURenderPipeline? = null
    private var vertexBuffer: GPUBuffer? = null
    private var shaderModule: GPUShaderModule? = null
    private var pipelineLayout: GPUPipelineLayout? = null
    
    @Volatile
    private var isClosed = false

    private val rendererMutex = Mutex()

    suspend fun initialize(surface: Surface, width: Int, height: Int) = rendererMutex.withLock {
        if (isClosed) return@withLock
        Log.d(TAG, "Initializing renderer... width=$width, height=$height")
        
        val gpu = createWebGpu(surface)
        webGpu = gpu
        val device = gpu.device

        val shaderCode  = TextResourceReader.readTextFileFromResource(context, R.raw.shaders_fixed)
        val module = device.createShaderModule(
            GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(shaderCode))
        )
        shaderModule = module

        val vertexBufferLayout = GPUVertexBufferLayout(
            arrayStride = 32L,
            stepMode = VertexStepMode.Vertex,
            attributes = arrayOf(
                GPUVertexAttribute(VertexFormat.Float32x4, 0L, 0),
                GPUVertexAttribute(VertexFormat.Float32x4, 16L, 1)
            )
        )

        val layout = device.createPipelineLayout(GPUPipelineLayoutDescriptor(bindGroupLayouts = arrayOf()))
        pipelineLayout = layout

        renderPipeline = device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                vertex = GPUVertexState(module, "vs_main", buffers = arrayOf(vertexBufferLayout)),
                fragment = GPUFragmentState(module, "fs_main", targets = arrayOf(GPUColorTargetState(TextureFormat.RGBA8Unorm))),
                primitive = GPUPrimitiveState(PrimitiveTopology.TriangleList),
                layout = layout
            )
        )

        gpu.webgpuSurface.configure(
            GPUSurfaceConfiguration(device, width, height, TextureFormat.RGBA8Unorm)
        )

        val vertexData = floatArrayOf(
            0.0f, 0.6f, 0f, 1f,  1f, 0f, 0f, 1f,
            -0.5f, -0.6f, 0f, 1f, 0f, 1f, 0f, 1f,
            0.5f, -0.6f, 0f, 1f, 0f, 0f, 1f, 1f,
        )
        val vByteBuffer = ByteBuffer.allocateDirect(vertexData.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder())
        vByteBuffer.asFloatBuffer().put(vertexData)
        vByteBuffer.rewind()

        vertexBuffer = device.createBuffer(
            GPUBufferDescriptor(BufferUsage.Vertex or BufferUsage.CopyDst, (vertexData.size * BYTES_PER_FLOAT).toLong())
        )
        device.queue.writeBuffer(vertexBuffer!!, 0, vByteBuffer)
        
        Log.d(TAG, "Renderer initialized")
    }

    suspend fun render() = rendererMutex.withLock {
        val gpu = webGpu ?: return@withLock
        val pipeline = renderPipeline ?: return@withLock
        val vBuf = vertexBuffer ?: return@withLock
        if (isClosed) return@withLock

        try {
            val surfaceTexture = gpu.webgpuSurface.getCurrentTexture()
            if (surfaceTexture.status != SurfaceGetCurrentTextureStatus.SuccessOptimal &&
                surfaceTexture.status != SurfaceGetCurrentTextureStatus.SuccessSuboptimal) {
                return@withLock
            }

            gpu.device.createCommandEncoder().use { commandEncoder ->
                surfaceTexture.texture.createView().use { textureView ->
                    commandEncoder.beginRenderPass(
                        GPURenderPassDescriptor(
                            colorAttachments = arrayOf(
                                GPURenderPassColorAttachment(
                                    clearValue = GPUColor(0.2, 0.0, 0.2, 1.0),
                                    view = textureView,
                                    loadOp = LoadOp.Clear,
                                    storeOp = StoreOp.Store,
                                )
                            )
                        )
                    ).use { renderPass ->
                        renderPass.setPipeline(pipeline)
                        renderPass.setVertexBuffer(0, vBuf)
                        renderPass.draw(3)
                        renderPass.end()
                    }

                    commandEncoder.finish().use { commandBuffer ->
                        gpu.device.queue.submit(arrayOf(commandBuffer))
                    }
                }
            }
            gpu.webgpuSurface.present()
            
            // Critical: Wait for GPU done before NEXT frame or surface changes
            gpu.device.queue.onSubmittedWorkDone()
        } catch (e: Exception) {
            if (!isClosed) Log.e(TAG, "Error in render(): $e")
        }
    }

    suspend fun cleanup() = rendererMutex.withLock {
        if (isClosed) return@withLock
        isClosed = true
        Log.d(TAG, "Cleaning up renderer resources...")
        
        renderPipeline?.close()
        renderPipeline = null
        
        pipelineLayout?.close()
        pipelineLayout = null
        
        shaderModule?.close()
        shaderModule = null
        
        vertexBuffer?.close()
        vertexBuffer = null
        
        // webGpu?.close() // Current library version crashes on close
        webGpu = null
    }

    companion object {
        const val BYTES_PER_FLOAT = 4
    }
}
