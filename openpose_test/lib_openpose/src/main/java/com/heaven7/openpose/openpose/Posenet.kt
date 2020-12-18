/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.heaven7.openpose.openpose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.heaven7.openpose.openpose.bean.Coord
import com.heaven7.openpose.openpose.bean.Human
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.util.ArrayList

enum class BodyPart {
    NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EAR,
    RIGHT_EAR,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE
}

class Position {
    var x: Int = 0
    var y: Int = 0
}

class KeyPoint {
    var bodyPart: BodyPart = BodyPart.NOSE
    var position: Position = Position()
    var score: Float = 0.0f
}

class Person {
    var keyPoints = listOf<KeyPoint>()
    var score: Float = 0.0f
}

enum class Device {
    CPU,
    NNAPI,
    GPU
}

class Posenet(
        val context: Context,
        val filename: String = "posenet_model.tflite",
        val device: Device = Device.CPU
) : AutoCloseable {
    var lastInferenceTimeNanos: Long = -1
        private set

    /** An Interpreter for the TFLite model.   */
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val NUM_LITE_THREADS = 4

    private fun getInterpreter(): Interpreter {
        if (interpreter != null) {
            return interpreter!!
        }
        val options = Interpreter.Options()
        options.setNumThreads(NUM_LITE_THREADS)
        when (device) {
            Device.CPU -> {
            }
            Device.GPU -> {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            }
            Device.NNAPI -> options.setUseNNAPI(true)
        }
        interpreter = Interpreter(loadModelFile(filename, context), options)
        return interpreter!!
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    /** Returns value within [0,1].   */
    private fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x)))
    }

    /**
     * Scale the image to a byteBuffer of [-1,1] values.
     */
    private fun initInputArray(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = 4
        val inputChannels = 3
        val batchSize = 1
        val inputBuffer = ByteBuffer.allocateDirect(
                batchSize * bytesPerChannel * bitmap.height * bitmap.width * inputChannels
        )
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val mean = 128.0f
        val std = 128.0f
        val intValues = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - mean) / std)
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - mean) / std)
            inputBuffer.putFloat(((pixelValue and 0xFF) - mean) / std)
        }
        return inputBuffer
    }

    /** Preload and memory map the model file, returning a MappedByteBuffer containing the model. */
    private fun loadModelFile(path: String, context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength
        )
    }

    /**
     * Initializes an outputMap of 1 * x * y * z FloatArrays for the model processing to populate.
     */
    private fun initOutputMap(interpreter: Interpreter): HashMap<Int, Any> {
        val outputMap = HashMap<Int, Any>()

        // 1 * 9 * 9 * 17 contains heatmaps
        val heatmapsShape = interpreter.getOutputTensor(0).shape()
        print("heatmapsShape: $heatmapsShape")
        outputMap[0] = Array(heatmapsShape[0]) {
            Array(heatmapsShape[1]) {
                Array(heatmapsShape[2]) { FloatArray(heatmapsShape[3]) }
            }
        }
        print("map0: heatmapsShape: ${outputMap[0]}") // Array<Array<Array<FloatArray>>>

        // 1 * 9 * 9 * 34 contains offsets
        val offsetsShape = interpreter.getOutputTensor(1).shape()
        outputMap[1] = Array(offsetsShape[0]) {
            Array(offsetsShape[1]) { Array(offsetsShape[2]) { FloatArray(offsetsShape[3]) } }
        }
        print("offsetsShape: $offsetsShape")
        print("map1: offsetsShape: ${outputMap[1]}") // Array<Array<Array<FloatArray>>>

        // 1 * 9 * 9 * 32 contains forward displacements
        val displacementsFwdShape = interpreter.getOutputTensor(2).shape()
        outputMap[2] = Array(offsetsShape[0]) {
            Array(displacementsFwdShape[1]) {
                Array(displacementsFwdShape[2]) { FloatArray(displacementsFwdShape[3]) }
            }
        }

        // 1 * 9 * 9 * 32 contains backward displacements
        val displacementsBwdShape = interpreter.getOutputTensor(3).shape()
        outputMap[3] = Array(displacementsBwdShape[0]) {
            Array(displacementsBwdShape[1]) {
                Array(displacementsBwdShape[2]) { FloatArray(displacementsBwdShape[3]) }
            }
        }

        return outputMap
    }

    /**
     * Estimates the pose for a single person.
     * args:
     *      bitmap: image bitmap of frame that should be processed
     * returns:
     *      person: a Person object containing data about keypoint locations and confidence scores
     */
    @Suppress("UNCHECKED_CAST")
    fun estimateSinglePose(bitmap: Bitmap): ArrayList<Classifier.Recognition> {
        val estimationStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        val inputArray = arrayOf(initInputArray(bitmap))
        Log.i(
                "posenet",
                String.format(
                        "Scaling to [-1,1] took %.2f ms",
                        1.0f * (SystemClock.elapsedRealtimeNanos() - estimationStartTimeNanos) / 1_000_000
                )
        )

        val interpreter = getInterpreter();
        val outputMap = initOutputMap(interpreter)

        val inferenceStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap)
        lastInferenceTimeNanos = SystemClock.elapsedRealtimeNanos() - inferenceStartTimeNanos
        Log.i(
                "posenet",
                String.format("Interpreter took %.2f ms", 1.0f * lastInferenceTimeNanos / 1_000_000)
        )

        val heatmaps = outputMap[0] as Array<Array<Array<FloatArray>>>
        val offsets = outputMap[1] as Array<Array<Array<FloatArray>>>

        val height = heatmaps[0].size
        val width = heatmaps[0][0].size
        val numKeypoints = heatmaps[0][0][0].size

        // Finds the (row, col) locations of where the keypoints are most likely to be.
        val keypointPositions = Array(numKeypoints) { Pair(0, 0) }
        for (keypoint in 0 until numKeypoints) {
            var maxVal = heatmaps[0][0][0][keypoint]
            var maxRow = 0
            var maxCol = 0
            for (row in 0 until height) {
                for (col in 0 until width) {
                    if (heatmaps[0][row][col][keypoint] > maxVal) {
                        maxVal = heatmaps[0][row][col][keypoint]
                        maxRow = row
                        maxCol = col
                    }
                }
            }
            keypointPositions[keypoint] = Pair(maxRow, maxCol)
        }

        // Calculating the x and y coordinates of the keypoints with offset adjustment.
        val xCoords = FloatArray(numKeypoints)
        val yCoords = FloatArray(numKeypoints)
        val confidenceScores = FloatArray(numKeypoints)
        keypointPositions.forEachIndexed { idx, position ->
            val positionY = keypointPositions[idx].first
            val positionX = keypointPositions[idx].second
            yCoords[idx] = (
                    position.first / (height - 1).toFloat() +
                            offsets[0][positionY][positionX][idx] / bitmap.height
                    )
            xCoords[idx] = (
                    position.second / (width - 1).toFloat() +
                            offsets[0][positionY][positionX][idx + numKeypoints] / bitmap.width
                    )
            confidenceScores[idx] = sigmoid(heatmaps[0][positionY][positionX][idx])
        }

        val map = java.util.HashMap<Int, Coord>();
        // val keypointList = Array(numKeypoints) { KeyPoint() }
        var totalScore = 0.0f
        enumValues<BodyPart>().forEachIndexed { idx, it ->
            map[castIdx(it)] = Coord(xCoords[idx], yCoords[idx], confidenceScores[idx], 1)
            totalScore += confidenceScores[idx]
            /* keypointList[idx].bodyPart = it
             keypointList[idx].position.x = xCoords[idx]
             keypointList[idx].position.y = yCoords[idx]
             keypointList[idx].score = confidenceScores[idx]
             totalScore += confidenceScores[idx]*/
        }
        //add neck coord
        var left_shoulder = map[Common.CocoPart.LShoulder.index]
        var right_shoulder = map[Common.CocoPart.RShoulder.index]
        if (left_shoulder == null) {
            left_shoulder = Coord(0f, 0f, 0f, 0);
        }
        if (right_shoulder == null) {
            right_shoulder = Coord(0f, 0f, 0f, 0);
        }
        map[Common.CocoPart.Neck.index] = Coord(
                (left_shoulder.x + right_shoulder.x) / 2,
                (left_shoulder.y + right_shoulder.y) / 2,
                (left_shoulder.score + right_shoulder.score) / 2,
                (left_shoulder.count + right_shoulder.count) / 2
        );

        //person.keyPoints = keypointList.toList()
        // person.score = totalScore / numKeypoints
        //build result
        val hu = Human()
        hu.parts = map
        val r0 = Classifier.Recognition("a", totalScore / numKeypoints)
        r0.humans = ArrayList<Human>()
        r0.humans.add(hu)
        val list = ArrayList<Classifier.Recognition>();
        list.add(r0)
        return list
    }

    fun castIdx(part: BodyPart): Int {
        when (part) {
            BodyPart.NOSE -> return Common.CocoPart.Nose.index
            BodyPart.LEFT_EYE -> return Common.CocoPart.LEye.index
            BodyPart.RIGHT_EYE -> return Common.CocoPart.REye.index
            BodyPart.LEFT_EAR -> return Common.CocoPart.LEar.index
            BodyPart.RIGHT_EAR -> return Common.CocoPart.REar.index
            BodyPart.LEFT_SHOULDER -> return Common.CocoPart.LShoulder.index
            BodyPart.RIGHT_SHOULDER -> return Common.CocoPart.RShoulder.index
            BodyPart.LEFT_ELBOW -> return Common.CocoPart.LElbow.index
            BodyPart.RIGHT_ELBOW -> return Common.CocoPart.RElbow.index
            BodyPart.LEFT_WRIST -> return Common.CocoPart.LWrist.index
            BodyPart.RIGHT_WRIST -> return Common.CocoPart.RWrist.index
            BodyPart.LEFT_HIP -> return Common.CocoPart.LHip.index
            BodyPart.RIGHT_HIP -> return Common.CocoPart.RHip.index
            BodyPart.LEFT_KNEE -> return Common.CocoPart.LKnee.index
            BodyPart.RIGHT_KNEE -> return Common.CocoPart.RKnee.index
            BodyPart.LEFT_ANKLE -> return Common.CocoPart.LAnkle.index
            BodyPart.RIGHT_ANKLE -> return Common.CocoPart.RAnkle.index
        }
    }
}
