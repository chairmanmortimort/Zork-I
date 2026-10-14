package com.mortimort.zork.engine

/**
 * Z-machine text decoding (v1-v3, standard 3-row alphabet + abbreviations).
 *
 * The Z-machine default alphabet has three rows of 26 entries each.
 * A z-char value of 6..31 indexes the *current* row (entry index = z - 6).
 *
 *   Row A0 (index 0): a b c d e f g h i j k l m n o p q r s t u v w x y z   (lowercase)
 *   Row A1 (index 1): A B C D E F G H I J K L M N O P Q R S T U V W X Y Z   (uppercase)
 *   Row A2 (index 2): [space] 0 1 2 3 4 5 6 7 8 9 . , ! ? _ # ' " / \ - : ( )  (punctuation)
 *
 * Z-char control values and shifts (v1-v3):
 *   0        → space
 *   1        → shift: NEXT z-char comes from A1 (one-shot)
 *   2        → shift: NEXT z-char comes from A2 (one-shot)
 *   3        → abbreviation: the following TWO z-chars (z1, z2) form the
 *                abbreviation number n = (z1-1)*16 + (z2-1) (0..95 in V3); the
 *                abbreviation's text is looked up in the abbreviation table
 *                (header 0x28, word offset × 32 → byte address; default byte 64)
 *                and recursively decoded.
 *   4        → lock to A2 (stays until another lock or end of string)
 *   5        → lock to A1 (stays until another lock or end of string)
 *   6..31    → glyph from current row at index (z - 6)
 *
 * Z-code v3 packs THREE z-chars per 16-bit word (5 bits each). The first
 * z-char (z1) occupies the MOST significant 5 bits (bits 15-11); z2 is bits
 * 10-6; z3 is bits 5-1. The Z-machine stores words in big-endian order: the
 * byte at the lower address is the high byte. The end-of-string flag is bit 7
 * of the first byte (bit 15 of the word).
 */
object ZText {

    private val ROWS = arrayOf(
        "abcdefghijklmnopqrstuvwxyz",                       // A0 lowercase
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",                       // A1 uppercase
        " 0123456789.,!?_#'\"/\\\\-:()"                        // A2 punctuation (26 chars)
    )

    /** How many z-chars per packed word for each Z-machine version. */
    private val ZCHARS_PER_WORD = mapOf(1 to 2, 2 to 3, 3 to 3)

    /**
     * Decode a zero-terminated z-string beginning at [textByteAddr].
     * Returns the decoded text, or an error marker if the address is invalid.
     */
    fun decode(story: ZStory, textByteAddr: Int): String {
        return decodeInternal(story, textByteAddr, 0)
    }

    private fun decodeInternal(story: ZStory, textByteAddr: Int, depth: Int): String {
        if (depth > 8) return ""
        if (textByteAddr < 0 || textByteAddr >= story.data.size) {
            return "[decode-out-of-range: addr=$textByteAddr]"
        }

        val sb = StringBuilder()
        var addr = textByteAddr
        var lockRow = 0          // 0 = no lock; 1 = lock A1; 2 = lock A2
        var shiftRow = 0         // 0 = none; 1 = shift A1 next; 2 = shift A2 next
        var pendingAbbr = false
        val abbrZ = mutableListOf<Int>()
        val zpc = ZCHARS_PER_WORD[story.version] ?: 3

        while (addr + (if (zpc >= 3) 2 else 1) <= story.data.size) {
            val byte0 = story.readByte(addr)
            var byte1 = 0
            if (zpc >= 3) {
                byte1 = story.readByte(addr + 1)
            }
            addr += if (zpc >= 3) 2 else 1

            val word = (byte0 shl 8) or byte1
            // Z-chars are packed MSB-first: z1 = bits 15-11, z2 = bits 10-6,
            // z3 = bits 5-1 (bit 0 is unused). Bit 7 of byte0 (bit 15 of the
            // word) is the end-of-string flag AND doubles as z1's top bit.
            // So on the final (EOS) word, z1 uses only bits 14-11 (4 bits).
            val eos = (byte0 and 0x80) != 0
            val z1Mask = if (eos) 0x0F else 0x1F
            for (zi in 0 until zpc) {
                val z = (word shr (11 - zi * 5)) and (if (zi == 0) z1Mask else 0x1F)

                if (pendingAbbr) {
                    // Collecting the two z-chars that encode the abbreviation number.
                    abbrZ.add(z)
                    if (abbrZ.size == 2) {
                        // V1-V3: each block has 16 entries. Entry number =
                        //   (z1 - 1) * 16 + (z2 - 1)   [0-based, range 0..95]
                        val n = (abbrZ[0] - 1) * 16 + (abbrZ[1] - 1)
                        val abbrByteAddr = story.abbrevAddress(n)
                        if (abbrByteAddr in story.data.indices) {
                            sb.append(decodeInternal(story, abbrByteAddr, depth + 1))
                        }
                        pendingAbbr = false
                        abbrZ.clear()
                        shiftRow = 0
                    }
                    continue
                }

                when (z) {
                    0 -> sb.append(' ')
                    1 -> { shiftRow = 1; lockRow = 0 }
                    2 -> { shiftRow = 2; lockRow = 0 }
                    3 -> {
                        pendingAbbr = true
                        abbrZ.clear()
                        shiftRow = 0; lockRow = 0
                    }
                    4 -> { lockRow = 2; shiftRow = 0 }
                    5 -> { lockRow = 1; shiftRow = 0 }
                    else -> {
                        val row = when {
                            lockRow != 0 -> lockRow
                            shiftRow != 0 -> shiftRow
                            else -> 0
                        }
                        val idx = z - 6
                        val table = ROWS[row]
                        sb.append(if (idx in table.indices) table[idx] else '?')
                        shiftRow = 0
                    }
                }
            }

            // End-of-string: bit 7 of the first byte (bit 15 of the word).
            if ((byte0 and 0x80) != 0) break
        }

        return sb.toString()
    }

    /**
     * Encode a single input character into a z-char value (used for parsing
     * player input). Returns -1 if the rune is not encodable.
     */
    fun encodeRune(r: Char): Int {
        ROWS.forEach { table ->
            val idx = table.indexOf(r)
            if (idx >= 0) return idx + 6
        }
        return -1
    }

    /** Encode a full line of input text into a sequence of z-chars (plus shifts). */
    fun encodeLine(text: String): List<Int> {
        val result = mutableListOf<Int>()
        for (c in text) {
            val lower = c.lowercaseChar()
            val z = encodeRune(lower)
            if (z >= 0) {
                if (c != lower && c.uppercaseChar() != c && ROWS[1].getOrNull(z - 6) != c.uppercaseChar()) {
                    val upperZ = encodeRune(c.uppercaseChar())
                    if (upperZ >= 0 && ROWS[1].getOrNull(upperZ - 6) == c.uppercaseChar()) {
                        result += 1  // shift to A1
                        result += upperZ
                    } else {
                        result += z  // fallback to lowercase
                    }
                } else {
                    result += z
                }
            } else {
                result += encodeRune('?')
            }
        }
        return result
    }
}
