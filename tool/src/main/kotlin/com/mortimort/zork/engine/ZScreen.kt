package com.thelightphone.zork.engine

/** Text screen / output buffer (ported from Zplet's ZScreen). */
open class ZScreen {
    var lines: Int = 80
    var chars: Int = 80
    var zforeground: Int = ZColor.Z_BLACK
    var zbackground: Int = ZColor.Z_WHITE

    val frameBuffer: CharArray = CharArray(20 * 1024)
    var cursor: Int = 0

    protected fun isterminator(key: Int): Boolean = key == 10 || key == 13

    fun getlines(): Int = lines
    fun getchars(): Int = chars
    fun clear() {}
    fun getZForeground(): Int = zforeground
    fun getZBackground(): Int = zbackground
    fun setZForeground(zcolor: Int) { zforeground = zcolor }
    fun setZBackground(zcolor: Int) { zbackground = zcolor }

    companion object {
        private val accent_table = charArrayOf(
            '\u00e4', '\u00f6', '\u00fc', '\u00c4', '\u00d6', '\u00dc', '\u00df', '\u00bb',
            '\u00ab', '\u00eb', '\u00ef', '\u00ff', '\u00cb', '\u00cf', '\u00e1', '\u00e9',
            '\u00ed', '\u00f3', '\u00fa', '\u00fd', '\u00c1', '\u00c9', '\u00cd', '\u00d3',
            '\u00da', '\u00dd', '\u00e0', '\u00e8', '\u00ec', '\u00f2', '\u00f9', '\u00c0',
            '\u00c8', '\u00cc', '\u00d2', '\u00d9', '\u00e2', '\u00ea', '\u00ee', '\u00f4',
            '\u00fb', '\u00c2', '\u00ca', '\u00ce', '\u00d4', '\u00da', '\u00e5', '\u00c5',
            '\u00f8', '\u00d8', '\u00e3', '\u00f1', '\u00f5', '\u00c3', '\u00d1', '\u00d5',
            '\u00e6', '\u00c6', '\u00e7', '\u00c7', '\u00fe', '\u00f0', '\u00de', '\u00d0',
            '\u00a3', '\u0153', '\u0152', '\u00a1', '\u00bf'
        )

        fun zascii_to_unicode(zascii: Short): Char {
            val z = zascii.toInt()
            if (z in 32..126) return z.toChar()
            if (z in 155..251) {
                val idx = z - 155
                return if (idx < accent_table.size) accent_table[idx] else '\ufffd'
            }
            if (z >= 256) return z.toChar()
            return '\ufffd'
        }

        fun unicode_to_zascii(unicode: Char): Short {
            if (unicode == '\n') return 13
            if (unicode == '\b') return 127
            if (unicode.code < 0x20 && unicode != '\r' && unicode != '\u001b')
                throw NoSuchKeyException("Illegal character input: ${unicode.code}")
            if (unicode.code < 0x80) return unicode.code.toShort()
            for (i in accent_table.indices) {
                if (accent_table[i] == unicode) return (155 + i).toShort()
            }
            throw NoSuchKeyException("Illegal character input: ${unicode.code}")
        }
    }
}
