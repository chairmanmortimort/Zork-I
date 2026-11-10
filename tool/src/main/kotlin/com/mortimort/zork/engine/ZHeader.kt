package com.thelightphone.zork.engine

/** Z-machine header access (ported from Zplet's ZHeader). */
abstract class ZHeader {
    protected lateinit var memory_image: ByteArray

    fun version(): Int = memory_image[VERSION].toInt() and 0xFF
    fun high_base(): Int = ((memory_image[HIGH_BASE].toInt() and 0xFF) shl 8) or (memory_image[HIGH_BASE + 1].toInt() and 0xFF)
    fun initial_pc(): Int = ((memory_image[INITIAL_PC].toInt() and 0xFF) shl 8) or (memory_image[INITIAL_PC + 1].toInt() and 0xFF)
    fun dictionary(): Int = ((memory_image[DICTIONARY].toInt() and 0xFF) shl 8) or (memory_image[DICTIONARY + 1].toInt() and 0xFF)
    fun object_table(): Int = ((memory_image[OBJECT_TABLE].toInt() and 0xFF) shl 8) or (memory_image[OBJECT_TABLE + 1].toInt() and 0xFF)
    fun global_table(): Int = ((memory_image[GLOBAL_TABLE].toInt() and 0xFF) shl 8) or (memory_image[GLOBAL_TABLE + 1].toInt() and 0xFF)
    fun static_base(): Int = ((memory_image[STATIC_BASE].toInt() and 0xFF) shl 8) or (memory_image[STATIC_BASE + 1].toInt() and 0xFF)
    fun transcripting(): Boolean = (memory_image[FLAGS2 + 1].toInt() and 1) == 1

    fun set_transcripting(onoff: Boolean) {
        if (onoff) memory_image[FLAGS2 + 1] = (memory_image[FLAGS2 + 1].toInt() or 1).toByte()
        else memory_image[FLAGS2 + 1] = (memory_image[FLAGS2 + 1].toInt() and 0xFE).toByte()
    }

    fun abbrev_table(): Int = ((memory_image[ABBREV_TABLE].toInt() and 0xFF) shl 8) or (memory_image[ABBREV_TABLE + 1].toInt() and 0xFF)
    fun force_fixed(): Boolean = (memory_image[FLAGS2 + 1].toInt() and 2) == 2

    fun set_revision(major: Int, minor: Int) {
        memory_image[STD_REVISION] = major.toByte()
        memory_image[STD_REVISION + 1] = minor.toByte()
    }

    fun release(): Short = (((memory_image[RELEASE].toInt() and 0xFF) shl 8) or (memory_image[RELEASE + 1].toInt() and 0xFF)).toShort()
    fun checksum(): Short = (((memory_image[FILE_CHECKSUM].toInt() and 0xFF) shl 8) or (memory_image[FILE_CHECKSUM + 1].toInt() and 0xFF)).toShort()

    abstract fun file_length(): Int

    companion object {
        protected const val VERSION = 0x00
        protected const val FLAGS1 = 0x01
        const val RELEASE = 0x02
        protected const val HIGH_BASE = 0x04
        protected const val INITIAL_PC = 0x06
        protected const val DICTIONARY = 0x08
        protected const val OBJECT_TABLE = 0x0A
        protected const val GLOBAL_TABLE = 0x0C
        protected const val STATIC_BASE = 0x0E
        const val FLAGS2 = 0x10
        const val SERIAL_NUMBER = 0x12
        protected const val ABBREV_TABLE = 0x18
        protected const val FILE_LENGTH = 0x1A
        const val FILE_CHECKSUM = 0x1C
        protected const val STD_REVISION = 0x32

        fun image_version(memory_image: ByteArray): Int = memory_image[VERSION].toInt() and 0xFF
    }
}
