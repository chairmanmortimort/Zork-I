package com.thelightphone.zork.engine

/** Version-3 dictionary (ported from Zplet's ZDictionary3). */
class ZDictionary3(private val zm: ZMachine, dict_address: Int) : ZDictionary() {
    private var separators: String
    private var nentries: Int
    private var wtable_addr: Int
    private var entry_length: Int

    constructor(zm: ZMachine) : this(zm, zm.header.dictionary())

    init {
        val n = zm.memory_image[dict_address].toInt() and 0xFF
        val separray = CharArray(n)
        for (i in 0 until n) separray[i] = (zm.memory_image[dict_address + i + 1].toInt() and 0xFF).toChar()
        separators = String(separray)
        entry_length = zm.memory_image[dict_address + n + 1].toInt() and 0xFF
        nentries = ((zm.memory_image[dict_address + n + 2].toInt() shl 8) or (zm.memory_image[dict_address + n + 3].toInt() and 0xFF))
        wtable_addr = dict_address + n + 4
    }

    override fun parse_word(textloc: Int, wordloc: Int, wordlength: Int, parseloc: Int): Boolean {
        if (zm.memory_image[parseloc].toInt() == zm.memory_image[parseloc + 1].toInt()) return true

        val encword = zm.encode_word(wordloc, wordlength, 2)
        val encint = ((encword[0].toInt() and 0xFFFF) shl 16) or (encword[1].toInt() and 0xFFFF)
        var first = 0
        var last = nentries - 1
        var middle = (last + first) / 2
        var dictloc: Int
        var dictint: Int

        while (true) {
            dictloc = wtable_addr + middle * entry_length
            dictint = ((zm.memory_image[dictloc].toInt() and 0xFF) shl 24) or
                    ((zm.memory_image[dictloc + 1].toInt() and 0xFF) shl 16) or
                    ((zm.memory_image[dictloc + 2].toInt() and 0xFF) shl 8) or
                    (zm.memory_image[dictloc + 3].toInt() and 0xFF)
            if (encint < dictint) {
                if (first == middle) break
                last = middle - 1
                middle = (first + middle) / 2
            } else if (encint > dictint) {
                if (last == middle) break
                first = middle + 1
                middle = (middle + last + 1) / 2
            } else break
        }
        var result = dictloc
        if (encint != dictint) result = 0

        val parseentry = parseloc + ((zm.memory_image[parseloc + 1].toInt() and 0xFF) * 4) + 2
        zm.memory_image[parseentry] = ((result and 0xFF00) shr 8).toByte()
        zm.memory_image[parseentry + 1] = (result and 0xFF).toByte()
        zm.memory_image[parseentry + 2] = wordlength.toByte()
        zm.memory_image[parseentry + 3] = (wordloc - textloc + 1).toByte()
        zm.memory_image[parseloc + 1] = (zm.memory_image[parseloc + 1].toInt() + 1).toByte()
        return zm.memory_image[parseloc].toInt() == zm.memory_image[parseloc + 1].toInt()
    }

    override fun tokenise(textloc: Int, textlength: Int, parseloc: Int) {
        if ((zm.memory_image[parseloc].toInt() and 0xFF) < 1) zm.fatal("Parse buffer less than 1 word (6 bytes)")
        zm.memory_image[parseloc + 1] = 0
        var wordloc = textloc
        var wordlength = 0
        var pbfull = false
        var textleft = textlength
        while (!pbfull && textleft-- > 0) {
            val ch = (zm.memory_image[wordloc + wordlength].toInt() and 0xFF).toChar()
            if (separators.indexOf(ch) != -1) {
                if (wordlength > 0) parse_word(textloc, wordloc, wordlength, parseloc)
                pbfull = parse_word(textloc, wordloc + wordlength, 1, parseloc)
                wordloc = wordloc + wordlength + 1
                wordlength = 0
            } else if (ch == ' ') {
                if (wordlength > 0) pbfull = parse_word(textloc, wordloc, wordlength, parseloc)
                wordloc = wordloc + wordlength + 1
                wordlength = 0
            } else {
                wordlength++
            }
        }
        if (!pbfull && wordlength > 0) parse_word(textloc, wordloc, wordlength, parseloc)
    }
}
