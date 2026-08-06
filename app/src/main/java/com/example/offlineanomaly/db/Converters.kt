package com.example.offlineanomaly.db

import androidx.room.TypeConverter
import java.nio.ByteBuffer

class Converters {
    @TypeConverter
    fun floatArrayToBlob(arr: FloatArray?): ByteArray? = arr?.let {
        val bb = ByteBuffer.allocate(arr.size * 4)
        bb.asFloatBuffer().put(arr)
        bb.array()
    }

    @TypeConverter
    fun blobToFloatArray(blob: ByteArray?): FloatArray? = blob?.let {
        val bb = ByteBuffer.wrap(it)
        val fb = bb.asFloatBuffer()
        val out = FloatArray(fb.remaining())
        fb.get(out)
        out
    }
}
