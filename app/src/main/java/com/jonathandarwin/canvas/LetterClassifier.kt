package com.jonathandarwin.canvas

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks.call
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.Interpreter

class LetterClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null

    var isInitialized = false
        private set

    /** Executor to run inference task in the background */
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    private var inputImageWidth: Int = 0 // will be inferred from TF Lite model
    private var inputImageHeight: Int = 0 // will be inferred from TF Lite model
    private var modelInputSize: Int = 0 // will be inferred from TF Lite model

    fun initialize(): Task<Void> {
        return call(
                executorService,
                Callable<Void> {
                    initializeInterpreter()
                    null
                }
        )
    }

    @Throws(IOException::class)
    private fun initializeInterpreter() {
        // Load the TF Lite model
        val assetManager = context.assets
        val model = loadModelFile(assetManager)

        // Initialize TF Lite Interpreter with NNAPI enabled
        val options = Interpreter.Options()
        options.setUseNNAPI(true)
        val interpreter = Interpreter(model, options)

        // Read input shape from model file
        val inputShape = interpreter.getInputTensor(0).shape()
        inputImageWidth = inputShape[1]
        inputImageHeight = inputShape[2]
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE

        // Finish interpreter initialization
        this.interpreter = interpreter
        isInitialized = true
        Log.d(TAG, "Initialized TFLite interpreter.")
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager): ByteBuffer {
        val fileDescriptor = assetManager.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun classify(bitmap: Bitmap): String {
        if (!isInitialized) {
            throw IllegalStateException("TF Lite Interpreter is not initialized yet.")
        }

        var startTime: Long
        var elapsedTime: Long

        // Preprocessing: resize the input
        startTime = System.nanoTime()

        val resizedImage = Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedImage)
        elapsedTime = (System.nanoTime() - startTime) / 1000000
        Log.d(TAG, "Preprocessing time = " + elapsedTime + "ms")

        startTime = System.nanoTime()
        val result = Array(1) { FloatArray(OUTPUT_CLASSES_COUNT) }
        interpreter?.run(byteBuffer, result)
        elapsedTime = (System.nanoTime() - startTime) / 1000000
        Log.d(TAG, "Inference time = " + elapsedTime + "ms")

        return getOutputString(result[0])
    }

    fun classifyAsync(bitmap: Bitmap): Task<String> {
        return call(executorService, Callable<String> { classify(bitmap) })
    }

    fun close() {
        call(
                executorService,
                Callable<String> {
                    interpreter?.close()
                    Log.d(TAG, "Closed TFLite interpreter.")
                    null
                }
        )
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputImageWidth * inputImageHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            //val g = (pixelValue shr 8 and 0xFF)
            //val b = (pixelValue and 0xFF)

            // Convert RGB to grayscale and normalize pixel value to [0..1]
            //val normalizedPixelValue = (r + g + b) / 3.0f / 255.0f
            val normalizedPixelValue = (255-r) / 255.0f
            byteBuffer.putFloat(normalizedPixelValue)
        }

        return byteBuffer
    }

    private fun getOutputString(output: FloatArray): String {
        val maxIndex = output.indices.maxBy { output[it] } ?: -1
        return "%c".format(classes[maxIndex])
        //return "Prediction Result: Digit %c\nConfidence: %2f".format(classes[maxIndex], output[maxIndex])

    }

    companion object {
        private const val TAG = "LetterClassifier"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 1

        private const val MODEL_FILE = "emnist_letters.tflite"
        private const val OUTPUT_CLASSES_COUNT = 26
        private val classes = charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z');
    }
}
