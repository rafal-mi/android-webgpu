package org.pinczow.webgpuapp.shader

import android.content.Context
import android.content.res.Resources
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.RuntimeException

class TextResourceReader {

    companion object {
        fun readTextFileFromResource(context: Context, resouceId: Int): String {
            val body = StringBuilder()

            try {
                val inputStream = context.resources.openRawResource(resouceId)
                val inputStreamReader = InputStreamReader(inputStream)
                val bufferedReader = BufferedReader(inputStreamReader)

                bufferedReader.use { r ->
                    r.lineSequence().forEach {
                        body.append(it)
                        body.append('\n')
                    }
                }

            } catch (e: IOException) {
                throw RuntimeException("Could not open resource: $resouceId", e)
            } catch (e: Resources.NotFoundException) {
                throw RuntimeException("Resource not found: $resouceId", e)
            }

            return body.toString()
        }
    }
}
