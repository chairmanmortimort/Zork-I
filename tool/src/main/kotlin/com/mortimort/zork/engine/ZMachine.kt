package com.thelightphone.zork.engine

import java.io.File
import java.util.EmptyStackException
import java.util.Random
import java.util.Stack

/** Core Z-machine (ported from Zplet's ZMachine). */
abstract class ZMachine(val screen: ZScreen, val status_line: ZStatus, val memory_image: ByteArray) {
    var current_window: ZWindow? = null
    var pc: Int = 0
    lateinit var window: Array<ZWindow>
    lateinit var header: ZHeader
    var objects: ZObjectTree? = null
    var zd: ZDictionary? = null
    lateinit var restart_state: ZState
    var zstack: Stack<Any> = Stack()
    var zrandom: Random = Random()
    var globals: Int = 0
    var locals: ShortArray = ShortArray(0)
    var argcount: Short = 0
    var inputstream: Int = 0
    var outputs: BooleanArray = BooleanArray(5)
    var printmemory: Int = 0
    protected var alphabet: Int = 0
    protected var build_ascii: Int = 0
    protected var built_ascii: Int = 0
    protected var abbrev_mode: Int = 0
    var checksum: Int = 0
    protected lateinit var zi: ZInstruction
    protected var status_redirect: Boolean = false
    protected var status_location: String? = null

    private val A2 = "0123456789.,!?_#'\"/\\-:()"

    private var runState: Int = STATE_INIT
    private var inputBuffer: CharArray? = null
    private var inputIndex: Int = 0

    var saveCalled: Boolean = false
    var restoreCalled: Boolean = false
    private var quickSaveSlot: File? = null

    init {
        outputs[1] = true
        alphabet = 0
    }

    abstract fun update_status_line()

    fun fillInputBuffer(input: CharArray) {
        inputBuffer = input
        inputIndex = 0
    }

    fun get_input_byte(buffered: Boolean): Byte {
        val buf = inputBuffer
        if (buf != null && inputIndex < buf.size) {
            var ret: Byte = 13
            try {
                val c = buf[inputIndex]
                if ((c.toInt() > 128 && c.toInt() < 145) || c == 8.toChar()) {
                    ret = c.code.toByte()
                } else {
                    ret = ZScreen.unicode_to_zascii(c).toByte()
                }
            } catch (e: NoSuchKeyException) {
                e.printStackTrace()
            }
            inputIndex++
            return ret
        } else {
            fatal("Reading past the keyboard input buffer!!!")
            return 13
        }
    }

    fun print_ascii_char(ch: Short) {
        if (status_redirect) {
            status_location = (status_location ?: "") + ch.toInt().toChar()
        } else if (outputs[3]) {
            var nchars = ((memory_image[printmemory].toInt() shl 8) and 0xFF00) or (memory_image[printmemory + 1].toInt() and 0xFF)
            if (ch > 255) memory_image[printmemory + nchars + 2] = '?'.code.toByte()
            else if (ch.toInt() == 10) memory_image[printmemory + nchars + 2] = 13.toByte()
            else memory_image[printmemory + nchars + 2] = ch.toByte()
            nchars++
            memory_image[printmemory] = ((nchars ushr 8) and 0xFF).toByte()
            memory_image[printmemory + 1] = (nchars and 0xFF).toByte()
        } else {
            if (outputs[1]) {
                if (ch.toInt() == 13 || ch.toInt() == 10) current_window?.newline()
                else current_window?.printzascii(ch)
            }
            outputs[2] = header.transcripting()
        }
    }

    abstract fun string_address(addr: Short): Int
    abstract fun routine_address(addr: Short): Int

    fun encode_word(wordloc: Int, wordlen: Int, nwords: Int): ShortArray {
        val encword = ShortArray(nwords)
        val zchars = IntArray(nwords * 3)
        var zi = 0
        var i = 0
        while (i < wordlen) {
            val ch = memory_image[wordloc + i].toInt() and 0xFF
            if (ch in 'a'.code..'z'.code) {
                zchars[zi] = ch - 'a'.code + 6
                if (++zi == nwords * 3) break
            } else if (ch in 'A'.code..'Z'.code) {
                System.err.println("Tried to encode uppercase dictionary word")
                zchars[zi] = ch - 'A'.code + 6
                if (++zi == nwords * 3) break
            } else {
                val a2index = A2.indexOf(ch.toChar())
                if (a2index != -1) {
                    zchars[zi] = 5
                    if (++zi == nwords * 3) break
                    zchars[zi] = a2index + 8
                    if (++zi == nwords * 3) break
                } else {
                    zchars[zi] = 5
                    if (++zi == nwords * 3) break
                    zchars[zi] = 6
                    if (++zi == nwords * 3) break
                    zchars[zi] = ch shr 5
                    if (++zi == nwords * 3) break
                    zchars[zi] = ch and 0x1F
                    if (++zi == nwords * 3) break
                }
            }
            i++
        }
        while (zi < nwords * 3) zchars[zi++] = 5
        zi = 0
        for (j in 0 until nwords) {
            encword[j] = (zchars[zi++] shl 10).toShort()
            encword[j] = (encword[j].toInt() or (zchars[zi++] shl 5)).toShort()
            encword[j] = (encword[j].toInt() or zchars[zi++]).toShort()
        }
        encword[nwords - 1] = (encword[nwords - 1].toInt() or 0x8000).toShort()
        return encword
    }

    protected fun alphabet_lookup(zchar: Byte): Short {
        val alphabet_table_address = ((memory_image[0x34].toInt() shl 8) and 0xFF00) or (memory_image[0x34 + 1].toInt() and 0x00FF)
        if (alphabet_table_address == 0) {
            when (alphabet) {
                0 -> return ('a'.code + zchar - 6).toShort()
                1 -> return ('A'.code + zchar - 6).toShort()
                2 -> {
                    if (zchar.toInt() == 7) return 13
                    return A2[zchar - 8].code.toShort()
                }
            }
        } else {
            if (alphabet in 0..2) {
                val zscii = memory_image[alphabet_table_address + alphabet * 26 + zchar - 6].toInt()
                if (zscii == '^'.code) return 13
                if (zscii >= 0) return zscii.toShort()
                return (zscii + 256).toShort()
            }
        }
        fatal("Bad Alphabet")
        return -1
    }

    fun print_abbrev(abbr_num: Int) {
        abbrev_mode = -1
        val abbrev_index = header.abbrev_table() + 2 * abbr_num
        val string_addr = (((memory_image[abbrev_index].toInt() shl 8) and 0xFF00) or (memory_image[abbrev_index + 1].toInt() and 0xFF)) * 2
        print_string(string_addr)
    }

    fun print_zchar(zchar: Byte) {
        if (build_ascii > 0) {
            built_ascii = (built_ascii shl 5) or zchar.toInt()
            build_ascii++
            if (build_ascii == 3) {
                print_ascii_char(built_ascii.toShort())
                build_ascii = 0
                built_ascii = 0
            }
            alphabet = 0
        } else if (abbrev_mode > 0) {
            print_abbrev(32 * (abbrev_mode - 1) + zchar)
            abbrev_mode = 0
            build_ascii = 0
            alphabet = 0
        } else {
            when (zchar.toInt()) {
                0 -> print_ascii_char(' '.code.toShort())
                1, 2, 3 -> {
                    if (abbrev_mode != 0) fatal("Abbreviation in abbreviation")
                    abbrev_mode = zchar.toInt()
                    alphabet = 0
                }
                4 -> alphabet = (alphabet + 1) % 3
                5 -> alphabet = (alphabet + 2) % 3
                6 -> {
                    if (alphabet == 2) {
                        build_ascii = 1
                        alphabet = 0
                    } else {
                        print_ascii_char(alphabet_lookup(zchar))
                        alphabet = 0
                    }
                }
                else -> {
                    print_ascii_char(alphabet_lookup(zchar))
                    alphabet = 0
                }
            }
        }
    }

    fun print_string(addr: Int): Int {
        var nbytes = 0
        var a = addr
        build_ascii = 0
        alphabet = 0
        abbrev_mode = 0
        val zchars = ByteArray(3)
        while (true) {
            val zseq = ((memory_image[a++].toInt() shl 8) and 0xFF00) or (memory_image[a++].toInt() and 0xFF)
            zchars[0] = ((zseq shr 10) and 0x1F).toByte()
            zchars[1] = ((zseq shr 5) and 0x1F).toByte()
            zchars[2] = (zseq and 0x1F).toByte()
            for (i in 0..2) print_zchar(zchars[i])
            nbytes += 2
            if ((zseq and 0x8000) != 0) break
        }
        return nbytes
    }

    fun getRunState(): Int = runState

    fun run(): Int {
        try {
            if (runState != STATE_RUNNING && runState != STATE_INIT) {
                runState = STATE_RUNNING
                zi.execute()
            }
            while (true) {
                zi.decode_instruction()
                when (zi.get_opnum()) {
                    ZInstruction.OP_SREAD -> {
                        runState = STATE_WAIT_CMD
                        return runState
                    }
                    ZInstruction.OP_READ_CHAR -> {
                        runState = STATE_WAIT_CHAR
                        return runState
                    }
                    else -> {
                        runState = STATE_RUNNING
                        zi.execute()
                    }
                }
            }
        } catch (booga: ArrayIndexOutOfBoundsException) {
            System.err.print("pc = ")
            System.err.println(Integer.toString(pc, 16))
            fatal("Memory address corruption")
        } catch (booga: ClassCastException) {
            System.err.print("pc = ")
            System.err.println(Integer.toString(pc, 16))
            fatal("Story corruption")
        }
        return STATE_RUNNING
    }

    fun calculate_checksum() {
        val filesize = header.file_length()
        checksum = 0
        if (filesize <= memory_image.size) {
            for (i in 0x40 until filesize) checksum += memory_image[i].toInt() and 0xFF
        }
    }

    open fun restart() {
        runState = STATE_INIT
        restart_state.header!!.set_transcripting(header!!.transcripting())
        restart_state.restore_saved()
        set_header_flags()
        pc = header.initial_pc()
        calculate_checksum()
    }

    open fun restore(zs: ZState) {
        zs.header!!.set_transcripting(header!!.transcripting())
        restart()
        zs.restore_saved()
        runState = STATE_WAIT_CMD
    }

    open fun set_header_flags() {
        header.set_revision(0, 2)
    }

    fun fatal(s: String): Nothing = throw GrueException(s)

    fun get_variable(varnum: Short): Short {
        val v = varnum.toInt() and 0xFF
        if (v == 0) {
            return try {
                (zstack.pop() as Int and 0xFFFF).toShort()
            } catch (booga: EmptyStackException) {
                fatal("Empty Stack")
            }
        } else if (v >= 0x10) {
            return (((memory_image[globals + ((v - 0x10) shl 1)].toInt() shl 8) and 0xFF00) or
                    (memory_image[globals + ((v - 0x10) shl 1) + 1].toInt() and 0xFF)).toShort()
        } else {
            return locals[v - 1]
        }
    }

    fun set_variable(varnum: Short, value: Short) {
        val v = varnum.toInt() and 0xFF
        if (v == 0) {
            zstack.push(value.toInt())
        } else if (v >= 0x10) {
            memory_image[globals + ((v - 0x10) shl 1)] = (value.toInt() ushr 8).toByte()
            memory_image[globals + ((v - 0x10) shl 1) + 1] = (value.toInt() and 0xFF).toByte()
        } else {
            locals[v - 1] = value
        }
    }

    fun get_code_byte(): Byte = memory_image[pc++]

    fun get_operand(optype: Int): Short {
        return when (optype) {
            OP_SMALL -> (get_code_byte().toInt() and 0xFF).toShort()
            OP_LARGE -> (((get_code_byte().toInt() shl 8) and 0xFF00) or (get_code_byte().toInt() and 0xFF)).toShort()
            OP_VARIABLE -> get_variable(get_code_byte().toShort())
            else -> -1
        }
    }

    fun getQuickSaveSlot(): File? = quickSaveSlot
    fun setQuickSaveSlot(file: File?) { this.quickSaveSlot = file }

    companion object {
        const val STATE_INIT = 0
        const val STATE_RUNNING = 1
        const val STATE_WAIT_CMD = 2
        const val STATE_WAIT_CHAR = 3

        const val OP_LARGE = 0
        const val OP_SMALL = 1
        const val OP_VARIABLE = 2
        const val OP_OMITTED = 3
    }
}
