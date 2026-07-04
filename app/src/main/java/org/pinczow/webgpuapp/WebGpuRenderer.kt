package org.pinczow.webgpuapp

import android.content.Context
import android.view.Surface
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
import androidx.webgpu.GPUVertexState
import androidx.webgpu.LoadOp
import androidx.webgpu.PrimitiveTopology
import androidx.webgpu.StoreOp
import androidx.webgpu.TextureFormat
import androidx.webgpu.helper.WebGpu
import androidx.webgpu.helper.createWebGpu
import org.pinczow.webgpuapp.shader.TextResourceReader

class WebGpuRenderer(val context: Context) {
    private lateinit var webGpu: WebGpu
    private lateinit var renderPipeline: GPURenderPipeline

    suspend fun init(surface: Surface, width: Int, height: Int) {
        // 1. Create Instance & Device
        webGpu = createWebGpu(surface)
        val device = webGpu.device

        // 2. Setup Pipeline (compile shaders)
        initPipeline(device)

        // 3. Configure the Surface
        webGpu.webgpuSurface.configure(
            GPUSurfaceConfiguration(
                device,
                width,
                height,
                TextureFormat.RGBA8Unorm,
            )
        )
    }

    private fun initPipeline(device: GPUDevice) {
        val shaderCode  = TextResourceReader.readTextFileFromResource(context, R.raw.shaders)

        // Create Shader Module
        val shaderModule = device.createShaderModule(
            GPUShaderModuleDescriptor(shaderSourceWGSL = GPUShaderSourceWGSL(shaderCode))
        )

        // Create Render Pipeline
        renderPipeline = device.createRenderPipeline(
            GPURenderPipelineDescriptor(
                vertex = GPUVertexState(
                    shaderModule,
                ), fragment = GPUFragmentState(
                    shaderModule, targets = arrayOf(GPUColorTargetState(TextureFormat.RGBA8Unorm))
                ), primitive = GPUPrimitiveState(PrimitiveTopology.TriangleList)
            )
        )
    }

    fun render() {
        if (!::webGpu.isInitialized) {
            return
        }

        val gpu = webGpu

        // 1. Get the next available texture from the screen
        val surfaceTexture = gpu.webgpuSurface.getCurrentTexture()

        // 2. Create a command encoder
        val commandEncoder = gpu.device.createCommandEncoder()

        // 3. Begin a render pass (clearing the screen to blue)
        val renderPass = commandEncoder.beginRenderPass(
            GPURenderPassDescriptor(
                colorAttachments = arrayOf(
                    GPURenderPassColorAttachment(
                        GPUColor(0.0, 0.0, 0.5, 1.0),
                        surfaceTexture.texture.createView(),
                        loadOp = LoadOp.Clear,
                        storeOp = StoreOp.Store,
                    )
                )
            )
        )

        // 4. Draw
        renderPass.setPipeline(renderPipeline)
        renderPass.draw(3) // Draw 3 vertices
        renderPass.end()

        // 5. Submit and Present
        gpu.device.queue.submit(arrayOf(commandEncoder.finish()))
        gpu.webgpuSurface.present()
    }

    fun cleanup() {
        if (::webGpu.isInitialized) {
            webGpu.close()
        }
    }
}

