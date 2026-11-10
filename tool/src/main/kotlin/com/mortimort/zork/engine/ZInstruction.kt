package com.thelightphone.zork.engine

import java.util.Stack

/** Instruction decoder + executor (ported from Zplet's ZInstruction). */
class ZInstruction(val zm: ZMachine) {
    protected var opnum: Int = 0
    fun get_opnum(): Int = opnum
    protected var count: Int = 0
    protected var save_pc: Int = 0
    protected var operands: ShortArray = ShortArray(4)
    protected var storevar: Short = 0
    protected var branchoffset: Short = 0
    protected var branchtype: Boolean = false

    fun decode_instruction() {
        decode_first_half()
        save_pc = zm.pc
        decode_second_half()
    }

    protected fun decode_first_half() {
        var opcode = zm.get_code_byte().toInt() and 0xFF
        var isextended = 0

        if (opcode == OP_EXTENDED) isextended = 0xC0

        when (isextended or (opcode and 0xC0)) {
            0xC0 -> { /* variable form */
                opnum = if (isextended == 0xC0) {
                    (0x100 or (zm.get_code_byte().toInt() and 0xFF))
                } else {
                    opcode
                }
                if ((opcode and 0x20) == 0) {
                    opnum = opcode and 0x1F
                }
                count = 0
                var optypes = zm.get_code_byte().toInt()
                val optypebytes: Int
                if (opnum == OP_CALL_VS2 || opnum == OP_CALL_VN2) {
                    optypebytes = 2
                    optypes2 = zm.get_code_byte().toInt()
                } else {
                    optypebytes = 1
                }
                var bytesLeft = optypebytes
                while (bytesLeft-- != 0) {
                    var optype = (optypes and 0xC0) shr 6
                    if (optype == ZMachine.OP_OMITTED) break
                    operands[count++] = zm.get_operand(optype)
                    optype = (optypes and 0x30) shr 4
                    if (optype == ZMachine.OP_OMITTED) break
                    operands[count++] = zm.get_operand(optype)
                    optype = (optypes and 0x0C) shr 2
                    if (optype == ZMachine.OP_OMITTED) break
                    operands[count++] = zm.get_operand(optype)
                    optype = optypes and 0x03
                    if (optype == ZMachine.OP_OMITTED) break
                    operands[count++] = zm.get_operand(optype)
                    optypes = optypes2
                }
            }
            0x80 -> { /* short form */
                val optype = (opcode and 0x30) shr 4
                if (optype == ZMachine.OP_OMITTED) {
                    opnum = opcode
                    count = 0
                } else {
                    opnum = opcode and 0x8F
                    count = 1
                    operands[0] = zm.get_operand(optype)
                }
            }
            else -> { /* long form */
                opnum = opcode and 0x1F
                count = 2
                var optype = ((opcode and 0x40) shr 6) + 1
                operands[0] = zm.get_operand(optype)
                optype = ((opcode and 0x20) shr 5) + 1
                operands[1] = zm.get_operand(optype)
            }
        }
    }

    private var optypes2: Int = 0

    protected fun decode_second_half() {
        if (isstore()) storevar = (zm.get_code_byte().toInt() and 0xFF).toShort()
        if (isbranch()) {
            branchoffset = (zm.get_code_byte().toInt() and 0xFF).toShort()
            branchtype = (branchoffset.toInt() and 0x80) != 0
            branchoffset = if ((branchoffset.toInt() and 0x40) != 0) {
                (branchoffset.toInt() and 0x3F).toShort()
            } else if ((branchoffset.toInt() and 0x20) != 0) {
                (0xC000 or ((branchoffset.toInt() shl 8) or (zm.get_code_byte().toInt() and 0xFF))).toShort()
            } else {
                (((branchoffset.toInt() and 0x3F) shl 8) or (zm.get_code_byte().toInt() and 0xFF)).toShort()
            }
        }
    }

    fun execute() {
        val result: Short = when (opnum) {
            OP_JE -> op_je()
            OP_JL -> op_jl()
            OP_JG -> op_jg()
            OP_DEC_CHK -> op_dec_chk()
            OP_INC_CHK -> op_inc_chk()
            OP_JIN -> op_jin()
            OP_TEST -> op_test()
            OP_OR -> op_or()
            OP_AND -> op_and()
            OP_TEST_ATTR -> op_test_attr()
            OP_SET_ATTR -> op_set_attr()
            OP_CLEAR_ATTR -> op_clear_attr()
            OP_STORE -> op_store()
            OP_INSERT_OBJ -> op_insert_obj()
            OP_LOADW -> op_loadw()
            OP_LOADB -> op_loadb()
            OP_GET_PROP -> op_get_prop()
            OP_GET_PROP_ADDR -> op_get_prop_addr()
            OP_GET_NEXT_PROP -> op_get_next_prop()
            OP_ADD -> op_add()
            OP_SUB -> op_sub()
            OP_MUL -> op_mul()
            OP_DIV -> op_div()
            OP_MOD -> op_mod()
            OP_JZ -> op_jz()
            OP_GET_SIBLING -> op_get_sibling()
            OP_GET_CHILD -> op_get_child()
            OP_GET_PARENT -> op_get_parent()
            OP_GET_PROP_LEN -> op_get_prop_len()
            OP_INC -> op_inc()
            OP_DEC -> op_dec()
            OP_PRINT_ADDR -> op_print_addr()
            OP_CALL_1S -> op_call_1s()
            OP_REMOVE_OBJ -> op_remove_obj()
            OP_PRINT_OBJ -> op_print_obj()
            OP_RET -> op_ret()
            OP_JUMP -> op_jump()
            OP_PRINT_PADDR -> op_print_paddr()
            OP_LOAD -> op_load()
            OP_NOT -> op_not()
            OP_RTRUE -> op_rtrue()
            OP_RFALSE -> op_rfalse()
            OP_PRINT -> op_print()
            OP_PRINT_RET -> op_print_ret()
            OP_NOP -> op_nop()
            OP_SAVE -> op_save()
            OP_RESTORE -> op_restore()
            OP_RESTART -> op_restart()
            OP_RET_POPPED -> op_ret_popped()
            OP_POP -> op_pop()
            OP_QUIT -> op_quit()
            OP_NEW_LINE -> op_new_line()
            OP_SHOW_STATUS -> op_show_status()
            OP_VERIFY -> op_verify()
            OP_EXTENDED -> op_extended()
            OP_PIRACY -> op_piracy()
            OP_CALL -> op_call()
            OP_STOREW -> op_storew()
            OP_STOREB -> op_storeb()
            OP_PUT_PROP -> op_put_prop()
            OP_SREAD -> op_sread()
            OP_PRINT_CHAR -> op_print_char()
            OP_PRINT_NUM -> op_print_num()
            OP_RANDOM -> op_random()
            OP_PUSH -> op_push()
            OP_PULL -> op_pull()
            OP_SPLIT_WINDOW -> op_split_window()
            OP_SET_WINDOW -> op_set_window()
            OP_OUTPUT_STREAM -> op_output_stream()
            OP_INPUT_STREAM -> op_input_stream()
            OP_SOUND_EFFECT -> op_sound_effect()
            else -> op_illegal()
        }

        if (((opnum != OP_CALL) && isstore()) || isret()) {
            zm.set_variable(storevar, result)
        }

        if (isbranch()) {
            if ((result.toInt() == 0) != branchtype) {
                when (branchoffset.toInt() and 0xFFFF) {
                    0 -> { z_ret(); zm.set_variable(storevar, ZFALSE) }
                    1 -> { z_ret(); zm.set_variable(storevar, ZTRUE) }
                    else -> zm.pc += branchoffset - 2
                }
            }
        }
    }

    protected fun isbranch(): Boolean = branch!![opnum]
    protected fun isstore(): Boolean = store!![opnum]

    protected fun isret(): Boolean = when (opnum) {
        OP_RET, OP_RFALSE, OP_RTRUE, OP_RET_POPPED, OP_PRINT_RET -> true
        else -> false
    }

    protected fun op_illegal(): Short {
        zm.fatal("Unknown opcode: $opnum")
    }

    protected fun op_je(): Short {
        for (i in 1 until count) if (operands[0] == operands[i]) return ZTRUE
        return ZFALSE
    }

    protected fun op_jl(): Short = if (operands[0] < operands[1]) ZTRUE else 0
    protected fun op_jg(): Short = if (operands[0] > operands[1]) ZTRUE else ZFALSE

    protected fun op_dec_chk(): Short {
        var vval = zm.get_variable(operands[0])
        vval = (vval - 1).toShort()
        zm.set_variable(operands[0], vval)
        return if (vval < operands[1]) ZTRUE else ZFALSE
    }

    protected fun op_inc_chk(): Short {
        var vval = zm.get_variable(operands[0])
        vval = (vval + 1).toShort()
        zm.set_variable(operands[0], vval)
        return if (vval > operands[1]) ZTRUE else ZFALSE
    }

    protected fun op_jin(): Short = if (zm.objects!!.parent(operands[0]) == operands[1]) ZTRUE else ZFALSE

    protected fun op_test(): Short = if ((operands[0].toInt() and operands[1].toInt()) == operands[1].toInt()) ZTRUE else ZFALSE
    protected fun op_or(): Short = ((operands[0].toInt() or operands[1].toInt()) and 0xFFFF).toShort()
    protected fun op_and(): Short = ((operands[0].toInt() and operands[1].toInt()) and 0xFFFF).toShort()

    protected fun op_test_attr(): Short = if (zm.objects!!.attribute(operands[0], operands[1])) ZTRUE else ZFALSE
    protected fun op_set_attr(): Short { zm.objects!!.set_attribute(operands[0], operands[1]); return ZFALSE }
    protected fun op_clear_attr(): Short { zm.objects!!.clear_attribute(operands[0], operands[1]); return ZFALSE }

    protected fun op_store(): Short { zm.set_variable(operands[0], operands[1]); return ZFALSE }

    protected fun op_insert_obj(): Short {
        detach_obj(operands[0])
        val dchild = zm.objects!!.child(operands[1])
        zm.objects!!.set_child(operands[1], operands[0])
        zm.objects!!.set_sibling(operands[0], dchild)
        zm.objects!!.set_parent(operands[0], operands[1])
        return ZFALSE
    }

    protected fun op_loadw(): Short {
        val byte_index = (operands[0].toInt() and 0xFFFF) + ((operands[1].toInt() and 0xFFFF) * 2)
        return (((zm.memory_image[byte_index].toInt() shl 8) and 0xFF00) or (zm.memory_image[byte_index + 1].toInt() and 0xFF)).toShort()
    }

    protected fun op_loadb(): Short {
        val byte_index = (operands[0].toInt() and 0xFFFF) + (operands[1].toInt() and 0xFFFF)
        return (zm.memory_image[byte_index].toInt() and 0xFF).toShort()
    }

    protected fun op_get_prop(): Short = zm.objects!!.prop(operands[0], operands[1])
    protected fun op_get_prop_addr(): Short = zm.objects!!.prop_address(operands[0], operands[1])
    protected fun op_get_next_prop(): Short = zm.objects!!.next_prop(operands[0], operands[1])
    protected fun op_add(): Short = (operands[0] + operands[1]).toShort()
    protected fun op_sub(): Short = (operands[0] - operands[1]).toShort()
    protected fun op_mul(): Short = (operands[0] * operands[1]).toShort()

    protected fun op_div(): Short {
        val dividend = operands[0].toInt()
        val divisor = operands[1].toInt()
        if (divisor == 0) zm.fatal("Remainder from division by zero")
        return (dividend / divisor).toShort()
    }

    protected fun op_mod(): Short {
        val dividend = operands[0].toInt()
        val divisor = operands[1].toInt()
        if (divisor == 0) zm.fatal("Remainder from division by zero")
        return (dividend % divisor).toShort()
    }

    protected fun op_jz(): Short = if (operands[0].toInt() == 0) ZTRUE else ZFALSE
    protected fun op_get_sibling(): Short = zm.objects!!.sibling(operands[0])
    protected fun op_get_child(): Short = zm.objects!!.child(operands[0])
    protected fun op_get_parent(): Short = zm.objects!!.parent(operands[0])
    protected fun op_get_prop_len(): Short = zm.objects!!.prop_len(operands[0])

    protected fun op_inc(): Short {
        var vval = zm.get_variable(operands[0])
        vval = (vval + 1).toShort()
        zm.set_variable(operands[0], vval)
        return ZFALSE
    }

    protected fun op_dec(): Short {
        var vval = zm.get_variable(operands[0])
        vval = (vval - 1).toShort()
        zm.set_variable(operands[0], vval)
        return ZFALSE
    }

    protected fun op_print_addr(): Short { zm.print_string(operands[0].toInt() and 0xFFFF); return ZFALSE }
    protected fun op_call_1s(): Short = ZNOTDONE

    fun detach_obj(obj: Short) {
        val parent = zm.objects!!.parent(obj)
        if (parent.toInt() != 0) {
            var cursor = zm.objects!!.child(parent)
            if (cursor == obj) {
                zm.objects!!.set_child(parent, zm.objects!!.sibling(obj))
            } else {
                while (zm.objects!!.sibling(cursor) != obj) {
                    cursor = zm.objects!!.sibling(cursor)
                    if (cursor.toInt() == 0) zm.fatal("Malformed object tree")
                }
                zm.objects!!.set_sibling(cursor, zm.objects!!.sibling(obj))
            }
        }
    }

    protected fun op_remove_obj(): Short {
        detach_obj(operands[0])
        zm.objects!!.set_parent(operands[0], 0)
        zm.objects!!.set_sibling(operands[0], 0)
        return ZFALSE
    }

    protected fun op_print_obj(): Short { zm.print_string(zm.objects!!.short_name_addr(operands[0])); return ZTRUE }

    protected fun z_ret() {
        var tos: Any
        do {
            tos = zm.zstack.pop()
        } while (tos !is ShortArray)
        zm.locals = tos
        zm.pc = zm.zstack.pop() as Int
        storevar = (zm.zstack.pop() as Int).toShort()
        zm.zstack.pop()
    }

    protected fun op_ret(): Short { z_ret(); return operands[0] }
    protected fun op_jump(): Short { zm.pc += operands[0] - 2; return ZFALSE }
    protected fun op_print_paddr(): Short { zm.print_string(zm.string_address(operands[0])); return ZFALSE }
    protected fun op_load(): Short = zm.get_variable(operands[0])
    protected fun op_not(): Short = (operands[0].toInt().inv()).toShort()
    protected fun op_rtrue(): Short { z_ret(); return ZTRUE }
    protected fun op_rfalse(): Short { z_ret(); return ZFALSE }

    protected fun op_print(): Short {
        val nchars = zm.print_string(zm.pc)
        zm.pc += nchars
        return ZFALSE
    }

    protected fun op_print_ret(): Short {
        zm.print_string(zm.pc)
        zm.print_ascii_char(13)
        z_ret()
        return ZTRUE
    }

    protected fun op_nop(): Short = ZFALSE
    protected fun op_save(): Short { zm.saveCalled = true; return ZFALSE }
    protected fun op_restore(): Short { zm.restoreCalled = true; return ZFALSE }
    protected fun op_restart(): Short { zm.restart(); return ZFALSE }

    protected fun op_ret_popped(): Short {
        val returnvalue = zm.get_variable(0)
        z_ret()
        return returnvalue
    }

    protected fun op_pop(): Short { zm.get_variable(0); return ZFALSE }

    protected fun op_quit(): Short {
        for (ch in "***END OF SESSION*") zm.print_ascii_char(ch.code.toShort())
        zm.print_ascii_char(13)
        return ZFALSE
    }

    protected fun op_new_line(): Short { zm.print_ascii_char(13); return ZFALSE }
    protected fun op_show_status(): Short { zm.update_status_line(); return ZFALSE }

    protected fun op_verify(): Short {
        val filesize = zm.header.file_length()
        if (filesize > zm.memory_image.size || zm.header.checksum().toInt() != zm.checksum) {
            System.err.println("VERIFY failed: ")
            return ZFALSE
        }
        return ZTRUE
    }

    protected fun op_extended(): Short = ZNOTDONE
    protected fun op_piracy(): Short = ZFALSE

    protected fun op_call(): Short {
        if (operands[0].toInt() == 0) {
            zm.set_variable(storevar, ZFALSE)
        } else {
            zm.zstack.push(ZFrameBound(isstore()))
            zm.zstack.push(storevar.toInt())
            zm.zstack.push(zm.pc)
            zm.zstack.push(zm.locals)
            zm.pc = zm.routine_address(operands[0])
            val nlocals = zm.get_code_byte().toInt() and 0xFF
            zm.locals = ShortArray(nlocals)
            for (i in 0 until nlocals) {
                val thislocal = (((zm.get_code_byte().toInt() shl 8) and 0xFF00) or (zm.get_code_byte().toInt() and 0xFF)).toShort()
                zm.locals[i] = if (i < count - 1) operands[i + 1] else thislocal
            }
        }
        return ZFALSE
    }

    protected fun op_storew(): Short {
        val byte_index = (operands[0].toInt() and 0xFFFF) + ((operands[1].toInt() and 0xFFFF) * 2)
        zm.memory_image[byte_index] = (operands[2].toInt() ushr 8).toByte()
        zm.memory_image[byte_index + 1] = (operands[2].toInt() and 0xFF).toByte()
        if (byte_index == ZHeader.FLAGS2 || byte_index == ZHeader.FLAGS2 + 1) {
            zm.current_window?.set_text_style(if (zm.header.force_fixed()) ZWindow.FIXED else ZWindow.ROMAN)
        }
        return ZFALSE
    }

    protected fun op_storeb(): Short {
        val byte_index = (operands[0].toInt() and 0xFFFF) + (operands[1].toInt() and 0xFFFF)
        zm.memory_image[byte_index] = (operands[2].toInt() and 0xFF).toByte()
        if (byte_index == ZHeader.FLAGS2 + 1) {
            zm.current_window?.set_text_style(if (zm.header.force_fixed()) ZWindow.FIXED else ZWindow.ROMAN)
        }
        return ZFALSE
    }

    protected fun op_put_prop(): Short { zm.objects!!.put_prop(operands[0], operands[1], operands[2]); return ZFALSE }

    protected fun op_sread(): Short {
        val tbuf = operands[0].toInt() and 0xFFFF
        zm.update_status_line()
        zm.current_window?.flush()
        zm.current_window?.reset_line_count()
        var tsize = zm.memory_image[tbuf].toInt() and 0xFF
        if (tsize < 2) zm.fatal("Text Buffer < 3 bytes")
        tsize--
        var bufloc = 1
        while (tsize != 0) {
            var c = zm.get_input_byte(true)
            if (c.toInt() == 13 || c.toInt() == 10) break
            if (c >= 'A'.code.toByte() && c <= 'Z'.code.toByte()) {
                c = (c - 'A'.code.toByte() + 'a'.code.toByte()).toByte()
            }
            zm.memory_image[tbuf + bufloc] = c
            bufloc++
            tsize--
        }
        zm.memory_image[tbuf + bufloc] = 0
        zm.zd!!.tokenise(tbuf + 1, bufloc - 1, operands[1].toInt() and 0xFFFF)
        return ZFALSE
    }

    protected fun op_print_char(): Short { zm.print_ascii_char(operands[0]); return ZFALSE }

    protected fun op_print_num(): Short {
        val mychars = operands[0].toInt().toString().toCharArray()
        for (c in mychars) zm.print_ascii_char(c.code.toShort())
        return ZFALSE
    }

    protected fun op_random(): Short {
        val r = operands[0].toInt()
        if (r == 0) {
            zm.zrandom.setSeed(System.currentTimeMillis())
            return 0
        } else if (r < 0) {
            zm.zrandom.setSeed(-r.toLong())
            return 0
        } else {
            return (((zm.zrandom.nextInt() and 0x7FFF) % r) + 1).toShort()
        }
    }

    protected fun op_push(): Short { zm.set_variable(0, operands[0]); return ZFALSE }
    protected fun op_pull(): Short { zm.set_variable(operands[0], zm.get_variable(0)); return ZFALSE }

    protected fun split_screen(lines: Int) {
        zm.window[UPPER_WINDOW].flush()
        zm.window[LOWER_WINDOW].flush()
        val cx = zm.window[LOWER_WINDOW].getx()
        var cy = zm.window[LOWER_WINDOW].gety() + zm.window[UPPER_WINDOW].getlines()
        cy -= lines
        if (cy < 0) cy = 0
        zm.window[LOWER_WINDOW].moveto(0, lines)
        zm.window[UPPER_WINDOW].moveto(0, 0)
        zm.window[LOWER_WINDOW].resize(zm.screen.getchars(), zm.screen.getlines() - lines)
        zm.window[UPPER_WINDOW].resize(zm.screen.getchars(), lines)
        if (cy >= zm.screen.getlines() - lines) {
            cy = zm.screen.getlines() - lines - 1
        }
        zm.window[LOWER_WINDOW].movecursor(cx, cy)
    }

    protected fun op_split_window(): Short { split_screen(operands[0].toInt()); return ZFALSE }

    protected fun op_set_window(): Short {
        zm.current_window?.flush()
        zm.current_window = zm.window[operands[0].toInt()]
        if (operands[0].toInt() == UPPER_WINDOW) zm.current_window?.movecursor(0, 0)
        return ZFALSE
    }

    protected fun op_output_stream(): Short {
        zm.current_window?.flush()
        val s = operands[0].toInt()
        if (s == 3) {
            zm.printmemory = operands[1].toInt() and 0xFFFF
            zm.memory_image[zm.printmemory] = 0
            zm.memory_image[zm.printmemory + 1] = 0
        } else if (s == 2) {
            zm.header.set_transcripting(true)
        } else if (s == -2) {
            zm.header.set_transcripting(false)
        }
        if (s > 0) zm.outputs[s] = true else zm.outputs[-s] = false
        return ZFALSE
    }

    protected fun op_input_stream(): Short { zm.inputstream = operands[0].toInt(); return ZFALSE }
    protected fun op_sound_effect(): Short = ZNOTDONE

    protected fun setupbs() {
        branch!![OP_JE] = true; branch!![OP_JL] = true; branch!![OP_JG] = true
        branch!![OP_DEC_CHK] = true; branch!![OP_INC_CHK] = true; branch!![OP_JIN] = true
        branch!![OP_TEST] = true
        store!![OP_OR] = true; store!![OP_AND] = true
        branch!![OP_TEST_ATTR] = true
        store!![OP_LOADW] = true; store!![OP_LOADB] = true; store!![OP_GET_PROP] = true
        store!![OP_GET_PROP_ADDR] = true; store!![OP_GET_NEXT_PROP] = true
        store!![OP_ADD] = true; store!![OP_SUB] = true; store!![OP_MUL] = true
        store!![OP_DIV] = true; store!![OP_MOD] = true
        branch!![OP_JZ] = true
        branch!![OP_GET_SIBLING] = true; store!![OP_GET_SIBLING] = true
        branch!![OP_GET_CHILD] = true; store!![OP_GET_CHILD] = true
        store!![OP_GET_PARENT] = true; store!![OP_GET_PROP_LEN] = true
        store!![OP_LOAD] = true; store!![OP_NOT] = true
        branch!![OP_SAVE] = true; branch!![OP_RESTORE] = true
        branch!![OP_VERIFY] = true; branch!![OP_PIRACY] = true
        store!![OP_CALL] = true; store!![OP_RANDOM] = true
    }

    companion object {
        protected val ZFALSE: Short = 0
        protected val ZTRUE: Short = 1
        protected val ZNOTDONE: Short = 0
        protected val ZSAVE_SUCCESS: Short = 1
        protected val ZRESTORE_SUCCESS: Short = 2

        const val OP_JE = 1
        const val OP_JL = 2
        const val OP_JG = 3
        const val OP_DEC_CHK = 4
        const val OP_INC_CHK = 5
        const val OP_JIN = 6
        const val OP_TEST = 7
        const val OP_OR = 8
        const val OP_AND = 9
        const val OP_TEST_ATTR = 10
        const val OP_SET_ATTR = 11
        const val OP_CLEAR_ATTR = 12
        const val OP_STORE = 13
        const val OP_INSERT_OBJ = 14
        const val OP_LOADW = 15
        const val OP_LOADB = 16
        const val OP_GET_PROP = 17
        const val OP_GET_PROP_ADDR = 18
        const val OP_GET_NEXT_PROP = 19
        const val OP_ADD = 20
        const val OP_SUB = 21
        const val OP_MUL = 22
        const val OP_DIV = 23
        const val OP_MOD = 24
        const val OP_JZ = 128
        const val OP_GET_SIBLING = 129
        const val OP_GET_CHILD = 130
        const val OP_GET_PARENT = 131
        const val OP_GET_PROP_LEN = 132
        const val OP_INC = 133
        const val OP_DEC = 134
        const val OP_PRINT_ADDR = 135
        const val OP_CALL_1S = 136
        const val OP_REMOVE_OBJ = 137
        const val OP_PRINT_OBJ = 138
        const val OP_RET = 139
        const val OP_JUMP = 140
        const val OP_PRINT_PADDR = 141
        const val OP_LOAD = 142
        const val OP_NOT = 143
        const val OP_RTRUE = 176
        const val OP_RFALSE = 177
        const val OP_PRINT = 178
        const val OP_PRINT_RET = 179
        const val OP_NOP = 180
        const val OP_SAVE = 181
        const val OP_RESTORE = 182
        const val OP_RESTART = 183
        const val OP_RET_POPPED = 184
        const val OP_POP = 185
        const val OP_QUIT = 186
        const val OP_NEW_LINE = 187
        const val OP_SHOW_STATUS = 188
        const val OP_VERIFY = 189
        const val OP_EXTENDED = 190
        const val OP_PIRACY = 191
        const val OP_CALL = 224
        const val OP_STOREW = 225
        const val OP_STOREB = 226
        const val OP_PUT_PROP = 227
        const val OP_SREAD = 228
        const val OP_READ_CHAR = 246
        const val OP_PRINT_CHAR = 229
        const val OP_PRINT_NUM = 230
        const val OP_RANDOM = 231
        const val OP_PUSH = 232
        const val OP_PULL = 233
        const val OP_SPLIT_WINDOW = 234
        const val OP_SET_WINDOW = 235
        const val OP_CALL_VS2 = 236
        const val OP_OUTPUT_STREAM = 243
        const val OP_INPUT_STREAM = 244
        const val OP_SOUND_EFFECT = 245
        const val OP_CALL_VN = 249
        const val OP_CALL_VN2 = 250

        const val LOWER_WINDOW = 0
        const val UPPER_WINDOW = 1

        var store: BooleanArray? = null
        var branch: BooleanArray? = null
    }

    init {
        if (store == null) {
            store = BooleanArray(256)
            branch = BooleanArray(256)
            setupbs()
        }
    }
}
