package com.thelightphone.zork.engine

import java.io.File
import java.io.IOException
import java.util.Stack

/** IFF FORM reader (ported from Zplet's IFFInputFile). */
class IFFInputFile : IFFFile {
    private var openchunkends: Stack<Long> = Stack()

    @Throws(IOException::class)
    constructor(file: File) : super(file, "r")

    @Throws(IOException::class)
    constructor(name: String) : super(name, "r")

    @Synchronized
    @Throws(IOException::class)
    fun readChunkInfo(): IFFChunkInfo {
        val result = IFFChunkInfo()
        val chunktype = ByteArray(4)
        read(chunktype, 0, 4)
        val chunkbegin = filePointer
        result.chunktype = String(chunktype, 0, 4)
        result.chunklength = readInt()
        openchunks.push(chunkbegin)
        openchunkends.push(filePointer + result.chunklength)
        return result
    }

    @Synchronized
    @Throws(IOException::class, IFFChunkNotFoundException::class)
    fun skipToChunk(type: String): IFFChunkInfo {
        if (filePointer >= openchunkends.peek())
            throw IFFChunkNotFoundException("Chunk $type not found at current level")
        var chunkinfo = readChunkInfo()
        while (chunkinfo.chunktype != type) {
            closeChunk()
            if (filePointer >= openchunkends.peek())
                throw IFFChunkNotFoundException("Chunk $type not found at current level")
            chunkinfo = readChunkInfo()
        }
        return chunkinfo
    }

    @Synchronized
    @Throws(IOException::class)
    fun readFORM(): String {
        val formchunkinfo = readChunkInfo()
        val subtype = ByteArray(4)
        if (formchunkinfo.chunktype == "FORM") {
            read(subtype, 0, 4)
        }
        return String(subtype, 0, 4)
    }

    @Synchronized
    @Throws(IOException::class)
    fun closeChunk() {
        val chunkend = (openchunkends.pop() + 1) and 1L.inv()
        openchunks.pop()
        seek(chunkend)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        while (openchunks.isNotEmpty()) {
            try {
                closeChunk()
            } catch (ioexcpt: IOException) {
            }
        }
        super.close()
    }
}
