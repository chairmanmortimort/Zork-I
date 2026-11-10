package com.thelightphone.zork.engine

/** Header used by save states (ported from Zplet's ZStateHeader). */
class ZStateHeader(memory_image: ByteArray) : ZHeader() {
    init { this.memory_image = memory_image }

    override fun file_length(): Int = 0
}
