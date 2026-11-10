package com.thelightphone.zork.engine

/** Object tree (ported from Zplet's ZObjectTree). */
abstract class ZObjectTree(protected val zm: ZMachine) {
    protected var object_table: Int = zm.header.object_table()
    protected var object_tree: Int = object_table + num_properties() * 2

    fun default_property(property: Short): Short {
        val p = (property - 1).toInt()
        val v = ((zm.memory_image[object_table + p * 2].toInt() shl 8) and 0xFF00) or
                (zm.memory_image[object_table + p * 2 + 1].toInt() and 0xFF)
        return v.toShort()
    }

    abstract fun num_properties(): Int
    abstract fun ptableoffset(): Int
    abstract fun getentryloc(objectnum: Short): Int

    fun attribute(obj: Short, attr_num: Short): Boolean {
        val entryloc = getentryloc(obj)
        val bytenum = attr_num.toInt() shr 3
        val bitmask = 1 shl (7 - (attr_num.toInt() and 7))
        return (zm.memory_image[entryloc + bytenum].toInt() and bitmask) != 0
    }

    fun set_attribute(obj: Short, attr_num: Short) {
        val entryloc = getentryloc(obj)
        val bytenum = attr_num.toInt() shr 3
        val bitmask = 1 shl (7 - (attr_num.toInt() and 7))
        zm.memory_image[entryloc + bytenum] = (zm.memory_image[entryloc + bytenum].toInt() or bitmask).toByte()
    }

    fun clear_attribute(obj: Short, attr_num: Short) {
        val entryloc = getentryloc(obj)
        val bytenum = attr_num.toInt() shr 3
        val bitmask = 1 shl (7 - (attr_num.toInt() and 7))
        zm.memory_image[entryloc + bytenum] = (zm.memory_image[entryloc + bytenum].toInt() and (bitmask xor 0xFF)).toByte()
    }

    abstract fun parent(obj: Short): Short
    abstract fun child(obj: Short): Short
    abstract fun sibling(obj: Short): Short
    abstract fun set_parent(obj: Short, newparent: Short)
    abstract fun set_sibling(obj: Short, newparent: Short)
    abstract fun set_child(obj: Short, newparent: Short)

    fun property_table_addr(obj: Short): Int {
        val entryloc = getentryloc(obj)
        return ((zm.memory_image[entryloc + ptableoffset()].toInt() shl 8) and 0xFF00) or
                (zm.memory_image[entryloc + ptableoffset() + 1].toInt() and 0xFF)
    }

    fun short_name_addr(obj: Short): Int = property_table_addr(obj) + 1

    abstract fun prop_entry_address(obj: Short, propnum: Short): Int
    abstract fun next_prop(obj: Short, propnum: Short): Short
    abstract fun prop_address(obj: Short, propnum: Short): Short
    abstract fun prop_len(prop_address: Short): Short

    fun prop(obj: Short, propnum: Short): Short {
        val entry_address = prop_entry_address(obj, propnum)
        if (entry_address == 0) return default_property(propnum)
        val size = (zm.memory_image[entry_address].toInt() ushr 5) + 1
        return if (size == 1) {
            (zm.memory_image[entry_address + 1].toInt() and 0xFF).toShort()
        } else {
            (((zm.memory_image[entry_address + 1].toInt() shl 8) and 0xFF00) or
                    (zm.memory_image[entry_address + 2].toInt() and 0xFF)).toShort()
        }
    }

    fun put_prop(obj: Short, propnum: Short, value: Short) {
        val entry_address = prop_entry_address(obj, propnum)
        if (entry_address == 0) {
            zm.fatal("Tried to set nonexistent property")
        } else {
            val size = (zm.memory_image[entry_address].toInt() ushr 5) + 1
            if (size == 1) {
                zm.memory_image[entry_address + 1] = (value.toInt() and 0xFF).toByte()
            } else {
                zm.memory_image[entry_address + 1] = (value.toInt() ushr 8).toByte()
                zm.memory_image[entry_address + 2] = (value.toInt() and 0xFF).toByte()
            }
        }
    }
}
