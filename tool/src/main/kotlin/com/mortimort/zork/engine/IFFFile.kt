package com.thelightphone.zork.engine

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Stack

/** Random-access file with an open-chunk stack (ported from Zplet's IFFFile). */
abstract class IFFFile(file: File, mode: String) : RandomAccessFile(file, mode) {
    protected var openchunks: Stack<Long> = Stack()

    constructor(name: String, mode: String) : this(File(name), mode)

    @Throws(IOException::class)
    fun chunkSeek(offset: Int) {
        seek(openchunks.peek() + 4 + offset)
    }

    @Throws(IOException::class)
    fun getChunkPointer(): Int = filePointer.toInt() - openchunks.peek().toInt() - 4
}
