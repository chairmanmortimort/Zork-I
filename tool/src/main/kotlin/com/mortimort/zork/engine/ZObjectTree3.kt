package com.thelightphone.zork.engine

/** Version-3 object tree (ported from Zplet's ZObjectTree3). */
class ZObjectTree3(zm: ZMachine) : ZObjectTree(zm) {
    override fun ptableoffset(): Int = 7

    override fun getentryloc(obj: Short): Int = object_tree + ((obj.toInt() and 0xFFFF) - 1) * 9

    override fun num_properties(): Int = 31

    override fun parent(obj: Short): Short = (zm.memory_image[getentryloc(obj) + 4].toInt() and 0xFF).toShort()
    override fun sibling(obj: Short): Short = (zm.memory_image[getentryloc(obj) + 5].toInt() and 0xFF).toShort()
    override fun child(obj: Short): Short = (zm.memory_image[getentryloc(obj) + 6].toInt() and 0xFF).toShort()

    override fun set_parent(obj: Short, newparent: Short) {
        zm.memory_image[getentryloc(obj) + 4] = newparent.toByte()
    }

    override fun set_sibling(obj: Short, newparent: Short) {
        zm.memory_image[getentryloc(obj) + 5] = newparent.toByte()
    }

    override fun set_child(obj: Short, newparent: Short) {
        zm.memory_image[getentryloc(obj) + 6] = newparent.toByte()
    }

    override fun prop_entry_address(obj: Short, propnum: Short): Int {
        var entry_address = property_table_addr(obj)
        entry_address += (zm.memory_image[entry_address].toInt() and 0xFF) * 2 + 1
        var sizebyte = zm.memory_image[entry_address].toInt() and 0xFF
        while (sizebyte != 0) {
            if ((sizebyte and 31) == propnum.toInt()) return entry_address
            if ((sizebyte and 31) < propnum.toInt()) return 0
            entry_address += (sizebyte shr 5) + 2
            sizebyte = zm.memory_image[entry_address].toInt() and 0xFF
        }
        return 0
    }

    override fun next_prop(obj: Short, propnum: Short): Short {
        var entry_address: Int
        if (propnum.toInt() == 0) {
            entry_address = property_table_addr(obj)
            if (entry_address == 0) zm.fatal("Tried to get next property for object with no properties")
            entry_address += (zm.memory_image[entry_address].toInt() and 0xFF) * 2 + 1
        } else {
            entry_address = prop_entry_address(obj, propnum)
            if (entry_address == 0) zm.fatal("Tried to get next property for nonexistent property")
            entry_address += ((zm.memory_image[entry_address].toInt() and 0xFF) shr 5) + 2
        }
        val sizebyte = zm.memory_image[entry_address].toInt() and 0xFF
        return (sizebyte and 31).toShort()
    }

    override fun prop_address(obj: Short, propnum: Short): Short {
        val entry_address = prop_entry_address(obj, propnum)
        return if (entry_address == 0) 0 else (entry_address + 1).toShort()
    }

    override fun prop_len(prop_address: Short): Short {
        if (prop_address.toInt() == 0) {
            zm.fatal("Tried to find length of missing property")
            return -1
        }
        val sizebyte = zm.memory_image[(prop_address.toInt() and 0xFFFF) - 1].toInt() and 0xFF
        return ((sizebyte shr 5) + 1).toShort()
    }
}
