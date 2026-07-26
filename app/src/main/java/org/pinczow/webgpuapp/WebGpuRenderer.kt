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
import androidx.webgpu.GPUPrimitiveState
import androidx.webgpu.GPURenderPassColorAttachment
import androidx.webgpu.GPURenderPassDescriptor
import androidx.webgpu.GPURenderPipeline
import androidx.webgpu.GPURenderPipelineDescriptor
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
import org.pinczow.webgpuapp.App.Companion.TAG
import org.pinczow.webgpuapp.shader.TextResourceReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WebGpuRenderer(val context: Context) {
    private var webGpu: WebGpu? = null
    private var renderPipeline: GPURenderPipeline? = null
    private var vertexBuffer: GPUBuffer? = null
    private var vertexByteBuffer: ByteBuffer? = null
    
    @Volatile
    private var isClosed = false

    private val rendererLock = Any()

    init {
        val vertexData = floatArrayOf(
            // x, y, z, w,  r, g, b, a
            0.0f, 0.6f, 0f, 1f,  1f, 0f, 0f, 1f,
            -0.5f, -0.6f, 0f, 1f, 0f, 1f, 0f, 1f,
            0.5f, -0.6f, 0f, 1f, 0f, 0f, 1f, 1f,
        )

        vertexByteBuffer = ByteBuffer.allocateDirect(vertexData.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder())
        vertexByteBuffer?.asFloatBuffer()?.put(vertexData)
    }

    suspend fun initialize(surface: Surface, width: Int, height: Int) {
        if (isClosed) return
        Log.d(TAG, "Initializing renderer... width=$width, height=$height")
        val gpu = createWebGpu(surface)
        webGpu = gpu
        val device = gpu.device

        initPipeline(device)

        gpu.webgpuSurface.configure(
            GPUSurfaceConfiguration(
                device = device,
                width = width,
                height = height,
                format = TextureFormat.RGBA8Unorm,
            )
        )

        val vBuffer = device.createBuffer(
            GPUBufferDescriptor(
                usage = BufferUsage.Vertex or BufferUsage.CopyDst,
                size = (3 * 8 * BYTES_PER_FLOAT).toLong(),
                label = "Vertex Buffer"
            )
        )
        vertexBuffer = vBuffer
        
        vertexByteBuffer?.let {
            it.rewind()
            device.queue.writeBuffer(vBuffer, 0, it)
        }
        
        // Wait for upload to complete
        device.queue.onSubmittedWorkDone()
        Log.d(TAG, "Renderer initialized")
    }

    private fun initPipeline(device: GPUDevice) {
        if (isClosed) return
        val shaderCode  = TextResourceReader.readTextFileFromResource(context, R.raw.shaders_fixed)

        val shaderModule = device.createShaderModule(
            GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(shaderCode))
        )

        val vertexBufferLayout = GPUVertexBufferLayout(
            arrayStride = 32L,
            stepMode = VertexStepMode.Vertex,
            attributes = arrayOf(
                GPUVertexAttribute(VertexFormat.Float32x4, 0L, 0),
                GPUVertexAttribute(VertexFormat.Float32x4, 16L, 1)
            )
        )

        val pipelineDescriptor = GPURenderPipelineDescriptor.Builder(
            GPUVertexState(
                module = shaderModule,
                entryPoint = "vs_main",
                buffers = arrayOf(vertexBufferLayout)
            )
        ).setFragment(
            GPUFragmentState(
                module = shaderModule,
                entryPoint = "fs_main",
                targets = arrayOf(GPUColorTargetState(TextureFormat.RGBA8Unorm))
            )
        ).setPrimitive(GPUPrimitiveState(PrimitiveTopology.TriangleList))
        .build()
        
        renderPipeline = device.createRenderPipeline(pipelineDescriptor)
    }

    fun render() = synchronized(rendererLock) {
        val gpu = webGpu ?: return
        val pipeline = renderPipeline ?: return
        val vBuf = vertexBuffer ?: return
        if (isClosed) return

        try {
            val surfaceTexture = gpu.webgpuSurface.getCurrentTexture()
            if (surfaceTexture.status != SurfaceGetCurrentTextureStatus.SuccessOptimal &&
                surfaceTexture.status != SurfaceGetCurrentTextureStatus.SuccessSuboptimal) {
                return
            }

            val texture = surfaceTexture.texture
            val textureView = texture.createView()

            val commandEncoder = gpu.device.createCommandEncoder()
            val renderPass = commandEncoder.beginRenderPass(
                GPURenderPassDescriptor(
                    colorAttachments = arrayOf(
                        GPURenderPassColorAttachment(
                            clearValue = GPUColor(0.0, 0.0, 0.5, 1.0),
                            view = textureView,
                            loadOp = LoadOp.Clear,
                            storeOp = StoreOp.Store,
                        )
                    )
                )
            )

            renderPass.setPipeline(pipeline)
            renderPass.setVertexBuffer(0, vBuf)
            renderPass.draw(3, 1, 0, 0)
            renderPass.end()

            val commandBundle = commandEncoder.finish()
            gpu.device.queue.submit(arrayOf(commandBundle))
            gpu.webgpuSurface.present()
            
            // Minimal cleanup, let GC handle most for now to identify crash source
            commandBundle.close()
            commandEncoder.close()
            renderPass.close()
            textureView.close()
            // NOT closing surface texture manually here
        } catch (e: Exception) {
            if (!isClosed) Log.e(TAG, "Error in render(): $e")
        }
    }

    fun cleanup() = synchronized(rendererLock) {
        if (isClosed) return
        isClosed = true
        Log.d(TAG, "Cleaning up renderer resources...")
        
        renderPipeline?.close()
        renderPipeline = null
        
        vertexBuffer?.close()
        vertexBuffer = null
        
        webGpu?.close()
        webGpu = null
    }

    companion object {
        const val POSITION_COMPONENT_COUNT = 2

        const val BYTES_PER_FLOAT = 4

        const val U_COLOR = "u_Color"

        const val A_POSITION = "a_Position"
    }
}

