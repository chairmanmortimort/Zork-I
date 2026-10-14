package com.mortimort.zork.engine

/**
 * Loads a Z-code story file (version 3) into memory and exposes header fields
 * and byte/word accessors.
 *
 * Z-machine memory model (v3):
 *  - [0, 64)            : header (64 bytes)
 *  - [64, dynamicEnd)   : dynamic memory (saved/restored as game state)
 *  - [dynamicEnd, ...)  : static + high memory
 *
 * Packed addresses (used for routine/string start PCs and abbreviation table
 * offsets) are word offsets. For V1-V3, multiply by 2 to get a byte address.
 * The abbreviation table address is special: it is a word offset multiplied
 * by 32 (size of one 16-entry abbreviation block) to get a byte address.
 *
 * Header field byte offsets (V1-V3):
 *  - 0x04-0x05: RAM start (byte address)
 *  - 0x06-0x07: start PC (byte address — already a byte address, not doubled)
 *  - 0x08-0x09: end of dynamic memory (byte address)
 *  - 0x0A-0x0B: object table (byte address)
 *  - 0x0C-0x0D: global variable table (byte address in V1-V3, word addr in V4+)
 *  - 0x28-0x29: abbreviation table (word offset × 32 → byte address)
 */
class ZStory(val data: ByteArray) {

    val version: Int get() = data[0].toInt() and 0xFF

    /** Initial program counter: byte address in header 0x06-0x07.
     * For all V1-V3 story files, the start PC is stored as a word offset
     * that must be doubled to get the byte address at which the initial
     * routine begins (including its local-variable-count byte). */
    val startPC: Int
        get() {
            val hi = readByte(0x06) and 0xFF
            val lo = readByte(0x07) and 0xFF
            val base = (hi shl 8) or lo
            return base * 2
        }

    /** Object table: plain byte address in header 0x0A-0x0B. */
    val objectTable: Int get() = readWord(0x0A)

    /** Dictionary address (V4+ only; unused in V3 header but kept for compatibility). */
    val dictionary: Int get() = readWord(0x08)

    /** Global variables table: byte address in header 0x0C-0x0D (V1-V3). */
    val globalTable: Int get() = readWord(0x0C)

    /** End of dynamic memory: byte address in header 0x08-0x09 (V1-V3). */
    val dynamicEnd: Int get() {
        val de = readWord(0x08)
        return if (de > 0 && de < data.size) de else readWord(0x04)
    }

    /**
     * Abbreviation table base byte address. In V1-V3, header 0x28-0x29 holds a
     * word offset; the byte address is offset × 32. If the field is 0, the
     * table follows immediately after the header at byte 64 (headerSize).
     */
    val abbrevTable: Int get() {
        val wordOff = readWord(0x28)
        val byteAddr = wordOff * 32
        return if (byteAddr == 0) headerSize else byteAddr
    }

    /**
     * Resolve abbreviation number [n] (0..95 in V3) to its z-string byte address.
     * Entry n in the table is a 2-byte word offset (packed address) at
     * `abbrevTable + n*2`; multiply by 2 to get a byte address.
     */
    fun abbrevAddress(n: Int): Int {
        val entry = abbrevTable + n * 2
        if (entry + 2 > data.size) return 0
        val packed = readWord(entry)
        return packed * 2
    }

    fun readByte(addr: Int): Int {
        require(addr in data.indices) { "readByte out of range: $addr (size=${data.size})" }
        return data[addr].toInt() and 0xFF
    }

    fun readWord(addr: Int): Int {
        return (readByte(addr) shl 8) or readByte(addr + 1)
    }

    fun writeByte(addr: Int, value: Int) {
        require(addr in data.indices) { "writeByte out of range: $addr" }
        data[addr] = (value and 0xFF).toByte()
    }

    fun writeWord(addr: Int, value: Int) {
        writeByte(addr, (value shr 8) and 0xFF)
        writeByte(addr + 1, value and 0xFF)
    }

    companion object {
        const val headerSize = 64
    }
}

object ZStoryLoader {
    fun fromBytes(bytes: ByteArray): ZStory {
        require(bytes.isNotEmpty()) { "Empty story file" }
        val version = bytes[0].toInt() and 0xFF
        require(version in 1..3) { "Unsupported Z-machine version: $version (only v1-v3 supported)" }
        return ZStory(bytes.copyOf())
    }
}
