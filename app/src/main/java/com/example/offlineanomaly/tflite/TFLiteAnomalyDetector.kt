package com.example.offlineanomaly.tflite

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteAnomalyDetector(context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseNNAPI(false)
            }
            interpreter = Interpreter(loadModelFile(context), options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("timeloss.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Runs the model and returns the Mean Squared Error (MSE).
     * @param inputFlat The 70-element float array (7 days x 10 features).
     */
    fun run(inputFlat: FloatArray): Float {
        val interp = interpreter ?: return 0f
        if (inputFlat.size != 70) return 0f

        val seqLen = 7
        val featureDim = 10

        val inputBuffer = ByteBuffer.allocateDirect(1 * seqLen * featureDim * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputFlat.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        val output = Array(1) { Array(seqLen) { FloatArray(featureDim) } }

        interp.run(inputBuffer, output)

        var sumSq = 0f

        for (i in 0 until seqLen) {
            for (j in 0 until featureDim) {
                val original = inputFlat[i * featureDim + j]
                val reconstructed = output[0][i][j]
                val diff = original - reconstructed
                sumSq += diff * diff
            }
        }

        return sumSq / (seqLen * featureDim)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}