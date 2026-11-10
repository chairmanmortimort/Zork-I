package com.thelightphone.zork.engine

import java.io.File
import java.io.IOException

/** IFF FORM writer (ported from Zplet's IFFOutputFile). */
class IFFOutputFile : IFFFile {
    @Throws(IOException::class)
    constructor(file: File) : super(file, "rw")

    @Throws(IOException::class)
    constructor(file: File, type: String) : super(file, "rw") {
        openChunk("FORM")
        write(getOSType(type), 0, 4)
    }

    @Throws(IOException::class)
    constructor(name: String) : super(name, "rw")

    @Throws(IOException::class)
    constructor(name: String, type: String) : super(name, "rw") {
        openChunk("FORM")
        write(getOSType(type), 0, 4)
    }

    private fun getOSType(s: String): ByteArray {
        val result = ByteArray(4)
        for (i in 0 until 4) result[i] = if (i < s.length) s[i].code.toByte() else 0
        return result
    }

    @Synchronized
    @Throws(IOException::class)
    fun openChunk(type: String) {
        write(getOSType(type), 0, 4)
        openchunks.push(filePointer)
        writeInt(0)
    }

    @Synchronized
    @Throws(IOException::class)
    fun closeChunk() {
        val currentlocation = filePointer
        var chunklength = getChunkPointer()
        val location = openchunks.pop()
        seek(location)
        writeInt(chunklength)
        seek(currentlocation)
        if ((chunklength and 1) == 1) writeByte(0)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        while (openchunks.isNotEmpty()) closeChunk()
        super.close()
    }
}
