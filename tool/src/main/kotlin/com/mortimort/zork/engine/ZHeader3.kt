package com.thelightphone.zork.engine

/** Version-3 header (ported from Zplet's ZHeader3). */
class ZHeader3(memory_image: ByteArray) : ZHeader() {
    init { this.memory_image = memory_image }

    fun time_game(): Boolean = (memory_image[FLAGS1].toInt() and 0x02) == 2

    fun set_status_unavailable(unavail: Boolean) {
        memory_image[FLAGS1] = if (unavail) (memory_image[FLAGS1].toInt() or 0x10).toByte()
        else (memory_image[FLAGS1].toInt() and 0xEF).toByte()
    }

    fun set_splitting_available(avail: Boolean) {
        memory_image[FLAGS1] = if (avail) (memory_image[FLAGS1].toInt() or 0x20).toByte()
        else (memory_image[FLAGS1].toInt() and 0xDF).toByte()
    }

    fun set_variable_default(variable: Boolean) {
        memory_image[FLAGS1] = if (variable) (memory_image[FLAGS1].toInt() or 0x40).toByte()
        else (memory_image[FLAGS1].toInt() and 0xBF).toByte()
    }

    override fun file_length(): Int {
        val packed_length = ((memory_image[FILE_LENGTH].toInt() and 0xFF) shl 8) or (memory_image[FILE_LENGTH + 1].toInt() and 0xFF)
        return packed_length * FILE_LENGTH_FACTOR
    }

    companion object {
        const val FILE_LENGTH_FACTOR = 2
    }
}
