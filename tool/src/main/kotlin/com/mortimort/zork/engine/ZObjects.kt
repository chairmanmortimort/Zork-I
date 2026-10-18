package com.thelightphone.zork.engine

/**
 * Object table access for v3.
 *
 * Layout (v3):
 *   [objectTable .. ] = default property values: one 2-byte word per property
 *                       number 1..N. The count is not stored explicitly.
 *   Then object entries, each 9 bytes:
 *     4 bytes attributes, 1 parent, 1 sibling, 1 child,
 *     2-byte PROPERTY TABLE POINTER.
 *
 * IMPORTANT (v3): the property-table pointer is a PLAIN byte address, not a
 * packed address. (Only versions 4+ pack it.) The property table itself starts
 * with a 1-byte name length followed by Z-encoded name text.
 *
 * Object entries are contiguous; the first entry's property pointer points just
 * past the last entry, so (propPtr - firstObj) is a whole multiple of 9 only
 * when the object count times 9 equals that gap minus obj1's property data. We
 * locate firstObj by scanning default-word counts and picking the one whose
 * object-1 property pointer is a sane byte address after the entries region.
 */
class ZObjects(private val story: ZStory) {

    private val objTable = story.objectTable
    private val entrySize = 9

    // Locate firstObj by scanning default-word counts. Object-1's property
    // pointer (a plain byte address in v3) must satisfy: (propPtr - firstObj)
    // is a whole multiple of the 9-byte entry size, AND object-1's property
    // chain (name + properties) must parse validly — properties numbers
    // descending, terminating in a 0 byte. This reliably disambiguates the
    // correct count from coincidental larger multiples. Zork1 -> defWords=31.
    private val firstObj: Int by lazy {
        val forced = System.getProperty("zork.defwords")
        if (forced != null) return@lazy objTable + forced.toInt() * 2
        // Locate the object table by finding the default-property-word count
        // whose object-1 property chain parses validly (descending property
        // numbers, 0 terminator) AND whose property pointer points PAST the
        // object entries (propPtr > firstObj). A pointer that lands inside the
        // defaults/object region is a false positive (e.g. defWords=29 yields
        // propPtr=48 in the header). Zork1 -> defWords=31.
        for (defWords in 0..64) {
            val candidate = objTable + defWords * 2
            val p = story.readWord(candidate + 7)
            if (p <= candidate || p >= story.data.size) continue
            if (propertyChainValid(p)) return@lazy candidate
        }
        objTable + 31 * 2   // fallback (known Zork1 value)
    }

    // Validate object-1's property table at [propPtr]: 1-byte name length, then
    // Z-encoded name, then property entries with descending numbers, terminated
    // by a 0 byte.
    private fun propertyChainValid(propPtr: Int): Boolean {
        if (propPtr <= 0 || propPtr + 1 >= story.data.size) return false
        val nameLen = story.readByte(propPtr)
        if (nameLen > 64) return false
        // The name must decode to printable Z-text (real object names do).
        if (nameLen > 0) {
            val name = ZText.decode(story, propPtr + 1)
            if (name.none { it.isLetterOrDigit() }) return false
            if (name.any { it.code > 0x7F || it.code < 0x20 }) return false
        }
        var a = propPtr + 1 + ((nameLen + 1) / 2) * 2
        var prev = 64
        var guard = 0
        while (guard++ < 100) {
            if (a >= story.data.size) return false
            val h = story.readByte(a)
            val size = h and 0x07
            if (size == 0) return true   // terminating 0 byte
            val num = (h shr 3) and 0x1F
            if (num !in 1..63) return false
            if (num > prev) return false  // must be descending
            prev = num
            a += size
        }
        return false
    }

    private fun addr(obj: Int): Int = firstObj + (obj - 1) * entrySize

    fun name(obj: Int): String {
        if (obj < 1) return ""
        val p = story.readWord(addr(obj) + 7)   // byte address (v3)
        if (p == 0) return ""
        // Property table: [1-byte name length][Z-encoded name][properties...].
        // Decode the name starting after the length byte.
        return ZText.decode(story, p + 1)
    }

    /** Byte address of object [obj]'s 9-byte entry. */
    fun firstObjAddr(obj: Int): Int = addr(obj)

    fun getProp(obj: Int, prop: Int): Int {
        val a = findProp(obj, prop) ?: return 0
        return story.readWord(a + 1)
    }

    fun getPropAddr(obj: Int, prop: Int): Int {
        return findProp(obj, prop) ?: 0
    }

    fun getNextProp(obj: Int, after: Int): Int {
        val propBase = story.readWord(addr(obj) + 7)   // byte address (v3)
        if (propBase == 0) return 0
        var a = propBase + 2  // skip the name length byte/word region (name is 1 byte len + text)
        // Name length byte is at propBase; text occupies ceil(len/2)*2 bytes.
        val nameLen = story.readByte(propBase)
        a = propBase + 1 + ((nameLen + 1) / 2) * 2
        if (after == 0) {
            val size = propSizeAt(a)
            if (size == 0) return 0
            return propNumberOf(a)
        }
        var cur = a
        while (true) {
            val size = propSizeAt(cur)
            if (size == 0) return 0
            val num = propNumberOf(cur)
            cur += size
            if (num == after) {
                val nsize = propSizeAt(cur)
                if (nsize == 0) return 0
                return propNumberOf(cur)
            }
        }
    }

    private fun findProp(obj: Int, prop: Int): Int? {
        val propBase = story.readWord(addr(obj) + 7)   // byte address (v3)
        if (propBase == 0) return null
        val nameLen = story.readByte(propBase)
        var a = propBase + 1 + ((nameLen + 1) / 2) * 2
        while (true) {
            val size = propSizeAt(a)
            if (size == 0) return null
            if (propNumberOf(a) == prop) return a
            a += size
        }
    }

    // V3 property header byte: high 5 bits = property number, low 3 bits =
    // size in bytes (1..7). For size 0 (i.e. low bits == 0) the property entry
    // is terminator: a single 0 byte.
    private fun propSizeAt(a: Int): Int {
        val b = story.readByte(a)
        val size = b and 0x07
        return if (size == 0) 0 else size
    }

    private fun propNumberOf(a: Int): Int {
        val b = story.readByte(a)
        return (b shr 3) and 0x1F
    }
}
