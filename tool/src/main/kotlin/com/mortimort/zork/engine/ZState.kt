package com.thelightphone.zork.engine

import java.io.IOException
import java.util.Stack

/** Save/restore state, Quetzal IFF format (ported from Zplet's ZState). */
class ZState(val zm: ZMachine) {
    var header: ZHeader? = null
    var zstack: Stack<Any>? = null
    var pc: Int = 0
    var dynamic: ByteArray? = null
    var locals: ShortArray? = null
    var argcount: Short = 0

    fun save_current() {
        header = ZStateHeader(zm.memory_image)
        val dyn_size = header!!.static_base()
        @Suppress("UNCHECKED_CAST")
        zstack = zm.zstack.clone() as Stack<Any>
        dynamic = ByteArray(dyn_size)
        System.arraycopy(zm.memory_image, 0, dynamic!!, 0, dyn_size)
        locals = ShortArray(zm.locals.size)
        System.arraycopy(zm.locals, 0, locals!!, 0, locals!!.size)
        header = ZStateHeader(dynamic!!)
        pc = zm.pc
    }

    fun restore_saved() {
        val dyn = dynamic ?: return
        System.arraycopy(dyn, 0, zm.memory_image, 0, dyn.size)
        val loc = locals ?: return
        zm.locals = ShortArray(loc.size)
        System.arraycopy(loc, 0, zm.locals, 0, loc.size)
        @Suppress("UNCHECKED_CAST")
        zm.zstack = (zstack?.clone() ?: Stack<Any>()) as Stack<Any>
        zm.pc = pc
    }

    fun restore_from_disk(fname: String): Boolean {
        var infile: IFFInputFile? = null
        var returnvalue = false
        var framelocals: ShortArray? = null
        var lastlocals: ShortArray = ShortArray(0)
        var lastargmask: Byte = 0
        var argcount: Short = 0

        val version = zm.header.version()
        try {
            infile = IFFInputFile(fname)
            val formtype = infile.readFORM()
            if (formtype == "IFZS") {
                infile.skipToChunk("IFhd")
                val release = infile.readShort()
                val serial = ByteArray(6)
                infile.read(serial, 0, 6)
                val checksum = infile.readShort()

                if (release != zm.header.release()) throw IOException("Release # did not match")
                if (checksum != zm.header.checksum()) throw IOException("Checksum did not match")
                for (i in serial.indices) {
                    if (zm.memory_image[ZHeader.SERIAL_NUMBER + i] != serial[i]) throw IOException("Serial # did not match")
                }

                var pc = (infile.readByte().toInt() and 0xFF) shl 16
                pc = pc or (infile.readShort().toInt() and 0xFFFF)
                this.pc = pc

                infile.closeChunk()
                val ifhdend = infile.filePointer

                try {
                    val chunkinfo = infile.skipToChunk("UMem")
                    if (chunkinfo.chunklength != zm.header.static_base())
                        throw IOException("Dynamic memory area is ${chunkinfo.chunklength} expected ${zm.header.static_base()}")
                    dynamic = ByteArray(chunkinfo.chunklength)
                    infile.read(dynamic!!, 0, chunkinfo.chunklength)
                } catch (memnotfound: IFFChunkNotFoundException) {
                    infile.seek(ifhdend)
                    val chunkinfo = infile.skipToChunk("CMem")
                    dynamic = ByteArray(zm.header.static_base())
                    System.arraycopy(zm.restart_state.dynamic!!, 0, dynamic!!, 0, zm.header.static_base())
                    var length = chunkinfo.chunklength
                    var nbytesout = 0
                    var runmode = false
                    while (length-- > 0) {
                        if (nbytesout >= zm.header.static_base()) throw IOException("CMem exceeded dynamic memory size")
                        val ch = infile.readByte()
                        if (runmode) {
                            runmode = false
                            nbytesout += (ch.toInt() and 0xFF) + 1
                        } else if (ch.toInt() != 0) {
                            dynamic!![nbytesout] = (dynamic!![nbytesout].toInt() xor ch.toInt()).toByte()
                            nbytesout++
                        } else {
                            runmode = true
                        }
                    }
                }
                header = ZStateHeader(dynamic!!)
                infile.closeChunk()
                infile.seek(ifhdend)

                val chunkinfo = infile.skipToChunk("Stks")
                val newStack: Stack<Any> = Stack()
                var frameno = 0
                while (infile.getChunkPointer() < chunkinfo.chunklength) {
                    var framepc = (infile.readByte().toInt() and 0xFF) shl 16
                    framepc = framepc or (infile.readShort().toInt() and 0xFFFF)
                    val flags = infile.readByte()
                    val resultvar = infile.readByte()
                    val argmask = infile.readByte()
                    val evalwords = infile.readShort().toInt()
                    val numlocals = flags.toInt() and 0xF
                    framelocals = ShortArray(numlocals)
                    for (i in 0 until numlocals) framelocals[i] = infile.readShort()

                    if (frameno > 0) {
                        if ((flags.toInt() and QUETZAL_PROCEDURE) == QUETZAL_PROCEDURE) {
                            newStack.push(ZFrameBound(false))
                            if (version > 3) newStack.push(ZInstruction.OP_CALL_VN)
                        } else {
                            newStack.push(ZFrameBound(true))
                            newStack.push(resultvar.toInt())
                            if (version > 3) newStack.push(ZInstruction.OP_CALL_1S)
                        }
                        if ((lastargmask.toInt() and (lastargmask.toInt() + 1)) != 0)
                            throw IOException("This implementation does not support noncontiguous arguments")
                        newStack.push(framepc)
                        if (version > 3) {
                            argcount = 0
                            var m = lastargmask
                            while (m.toInt() != 0) {
                                argcount = (argcount + 1).toShort()
                                m = (m.toInt() and (m.toInt() - 1)).toByte()
                            }
                            newStack.push(argcount.toInt())
                        }
                        newStack.push(lastlocals)
                    }

                    lastargmask = argmask
                    lastlocals = framelocals!!
                    for (i in 0 until evalwords) newStack.push(infile.readShort().toInt())
                    frameno++
                }
                this.locals = lastlocals
                if (version > 3) {
                    argcount = 0
                    var m = lastargmask
                    while (m.toInt() != 0) {
                        argcount = (argcount + 1).toShort()
                        m = (m.toInt() and (m.toInt() - 1)).toByte()
                    }
                    this.argcount = argcount
                }
                this.zstack = newStack
                infile.closeChunk()
                returnvalue = true
            }
            infile.close()
        } catch (excpt: IOException) {
            System.err.println(excpt)
            try { infile?.close() } catch (e: IOException) {}
        } catch (cnfexcpt: IFFChunkNotFoundException) {
            System.err.println(cnfexcpt)
            try { infile?.close() } catch (e: IOException) {}
        } catch (sexcpt: SecurityException) {
            System.err.println(sexcpt)
            try { infile?.close() } catch (e: IOException) {}
        } finally {
            if (!returnvalue) {
                dynamic = null
                zstack = null
                framelocals = null
                lastlocals = ShortArray(0)
                header = null
            }
        }
        return returnvalue
    }

    @Throws(IOException::class)
    private fun write_cmem_chunk(outfile: IFFOutputFile) {
        outfile.openChunk("CMem")
        var runsize = 0
        for (i in 0 until zm.header.static_base()) {
            if (zm.memory_image[i].toInt() == zm.restart_state.dynamic!![i].toInt()) {
                runsize++
            } else {
                while (runsize > 0) {
                    outfile.writeByte(0)
                    if (runsize >= 256) {
                        outfile.writeByte(255)
                        runsize -= 256
                    } else {
                        outfile.writeByte(runsize - 1)
                        runsize = 0
                    }
                }
                outfile.writeByte(zm.memory_image[i].toInt() xor zm.restart_state.dynamic!![i].toInt())
            }
        }
        outfile.closeChunk()
    }

    fun disk_save(fname: String, save_pc: Int): Boolean {
        var outfile: IFFOutputFile? = null
        var returnvalue = false
        var framelocals: ShortArray? = null
        var lastlocals: ShortArray = ShortArray(0)

        try {
            if (fname == "" || fname == "nullnull") throw IOException("No file picked")

            outfile = IFFOutputFile(fname, "IFZS")
            outfile.openChunk("IFhd")
            outfile.write(zm.memory_image, ZHeader.RELEASE, 2)
            outfile.write(zm.memory_image, ZHeader.SERIAL_NUMBER, 6)
            outfile.write(zm.memory_image, ZHeader.FILE_CHECKSUM, 2)
            outfile.writeByte((save_pc and 0xFF0000) ushr 16)
            outfile.writeShort(save_pc and 0xFFFF)
            outfile.closeChunk()
            write_cmem_chunk(outfile)
            outfile.openChunk("Stks")
            val version = zm.header.version()

            val e = zm.zstack.elements()
            val f = zm.zstack.elements()

            while (f.hasMoreElements()) {
                val el2 = f.nextElement()
                if (el2 is ZFrameBound) break
            }

            outfile.writeByte(0)
            outfile.writeShort(0)
            outfile.writeByte(0)
            outfile.writeByte(0)
            outfile.writeByte(0)

            var evalstackloc = outfile.filePointer
            outfile.writeShort(0)
            var i = 0
            var el: Any? = null
            while (e.hasMoreElements()) {
                el = e.nextElement()
                if (el is ZFrameBound) break
                outfile.writeShort(el as Int)
                i++
            }
            var placeholder = outfile.filePointer
            outfile.seek(evalstackloc)
            outfile.writeShort(i)
            outfile.seek(placeholder)

            while (e.hasMoreElements()) {
                var el2: Any? = null
                if (f.hasMoreElements()) {
                    do { el2 = f.nextElement() } while (f.hasMoreElements() && el2 !is ZFrameBound)
                }

                var argcount: Short = 4
                val localsArr: ShortArray
                if (f.hasMoreElements()) {
                    if ((el2 as ZFrameBound).store) el2 = f.nextElement()
                    if (version > 3) el2 = f.nextElement()
                    el2 = f.nextElement()
                    if (version > 3) {
                        el2 = f.nextElement()
                        argcount = (el2 as Int).toShort()
                    }
                    el2 = f.nextElement()
                    localsArr = el2 as ShortArray
                } else {
                    if (version > 3) argcount = zm.argcount
                    localsArr = zm.locals
                }

                var numlocals = localsArr.size.toShort()
                if (version <= 3 && numlocals < argcount) argcount = numlocals

                val framestore = (el as ZFrameBound).store
                val storevar: Short = if (framestore) {
                    el = e.nextElement()
                    (el as Int).toShort()
                } else 0

                if (version > 3) el = e.nextElement()
                el = e.nextElement()
                val framepc = (el as Int)
                if (version > 3) el = e.nextElement()
                el = e.nextElement()

                var frameflags = numlocals.toInt().toByte()
                if (!framestore) frameflags = (frameflags.toInt() or QUETZAL_PROCEDURE).toByte()
                val argmask = ((1 shl argcount.toInt()) - 1).toByte()
                outfile.writeByte((framepc and 0xFF0000) ushr 16)
                outfile.writeShort(framepc and 0xFFFF)
                outfile.writeByte(frameflags.toInt())
                outfile.writeByte(storevar.toInt())
                outfile.writeByte(argmask.toInt())
                evalstackloc = outfile.filePointer
                outfile.writeShort(0)
                for (j in 0 until numlocals.toInt()) outfile.writeShort(localsArr[j].toInt())
                i = 0
                while (e.hasMoreElements()) {
                    el = e.nextElement()
                    if (el is ZFrameBound) break
                    outfile.writeShort(el as Int)
                    i++
                }
                placeholder = outfile.filePointer
                outfile.seek(evalstackloc)
                outfile.writeShort(i)
                outfile.seek(placeholder)
            }
            outfile.closeChunk()
            outfile.close()
            returnvalue = true
        } catch (excpt: IOException) {
            try { outfile?.close() } catch (e: IOException) {}
        } catch (sexcpt: SecurityException) {
            try { outfile?.close() } catch (e: IOException) {}
        }
        return returnvalue
    }

    companion object {
        const val QUETZAL_PROCEDURE = 0x10
    }
}
