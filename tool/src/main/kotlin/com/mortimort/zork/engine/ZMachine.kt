package com.mortimort.zork.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * A pure-Kotlin Z-machine interpreter for Z-code version 3.
 *
 * Drives the interpreter against a [ZStory] and emits [ZEvent]s through the
 * supplied [output] buffer + the returned event list. Supports save/restore of
 * dynamic memory plus CPU state, and the in-game SAVE/RESTORE opcodes.
 */
class ZMachine(
    private val story: ZStory,
    private val output: ZOutputBuffer
) {
    // ── State ──
    private var pc = story.startPC
    private val stack = IntArray(2048)
    private var sp = 0  // stack pointer (0 = empty)
    private val globals = IntArray(240)
    private val frames = ArrayDeque<Frame>()
    private var finished = false
    private var waitingForInput = false
    private var inputBuffer: IntArray? = null   // text buffer + parse buffer addrs for sread
    private var inputParse: Int = 0

    // Save/restore callbacks (wired by the host)
    var onSaveRequest: (() -> ByteArray)? = null
    var onRestoreRequest: (() -> ByteArray?)? = null

    data class Frame(
        val returnPC: Int,
        val storeVar: Int,      // variable to store result into (0 = discard)
        val locals: IntArray,   // local variables
        val stackBase: Int      // sp at frame entry
    )

    // ── Public API ──
    fun isQuit(): Boolean = finished
    fun isWaitingForInput(): Boolean = waitingForInput

    fun start(): List<ZEvent> {
        // Initialize globals from header global table
        for (i in 0 until 240) globals[i] = story.readWord(story.globalTable + i * 2)
        if (false) System.err.println("ZORK_TRACE start: startPC=${story.startPC} globalTable=${story.globalTable} dynEnd=${story.dynamicEnd} dataSize=${story.data.size}")
        if (false) {
            val sb = StringBuilder()
            for (i in 0 until 32) {
                sb.append(String.format("%02x ", story.readByte(story.startPC + i)))
            }
            System.err.println("ZORK_TRACE startPC bytes: ${sb.toString()}")
        }
        val events = mutableListOf<ZEvent>()
        // Enter the main routine like a normal call: startPC points at the
        // routine's locals-count byte, so let callRoutine read it and set up
        // the frame. Its returnPC is 0; when it returns the game ends.
        callRoutine(story.startPC, emptyList(), 0, 0)
        runLoop(events)
        return events
    }

    fun resume(): List<ZEvent> {
        val events = mutableListOf<ZEvent>()
        runLoop(events)
        return events
    }

    fun submitInput(text: String): List<ZEvent> {
        val events = mutableListOf<ZEvent>()
        if (!waitingForInput) return events
        waitingForInput = false
        // Write the line into the text buffer (addr, maxlen, ...). Format:
        //   byte0 = max length, byte1 = current length, then characters.
        inputBuffer?.let { buf ->
            val maxLen = story.readByte(buf[0])
            val chars = text.lowercase().take(maxLen.coerceAtLeast(0))
            story.writeByte(buf[0] + 1, chars.length)
            for (i in chars.indices) story.writeByte(buf[0] + 2 + i, chars[i].code and 0xFF)
            if (inputParse != 0) tokenize(chars, buf[0], inputParse)
        }
        runLoop(events)
        return events
    }

    // ── Save / Restore ──
    fun saveState(): ByteArray {
        val baos = ByteArrayOutputStream()
        val o = DataOutputStream(baos)
        o.writeInt(pc)
        o.writeInt(sp)
        o.writeInt(frames.size)
        for (f in frames) {
            o.writeInt(f.returnPC); o.writeInt(f.storeVar); o.writeInt(f.stackBase)
            o.writeInt(f.locals.size)
            for (l in f.locals) o.writeInt(l)
        }
        for (i in 0 until sp) o.writeInt(stack[i])
        for (g in globals) o.writeInt(g)
        // Only the dynamic region [headerSize..dynamicEnd) ever changes at runtime.
        // The header and lookup tables ([0..headerSize)) are static — don't store them.
        val dynStart = ZStory.headerSize
        val dynEnd = story.dynamicEnd
        o.writeInt(dynStart)
        o.writeInt(dynEnd)
        o.write(story.data, dynStart, dynEnd - dynStart)
        o.flush()
        return baos.toByteArray()
    }

    fun restoreState(bytes: ByteArray): Boolean {
        return try {
            val i = DataInputStream(ByteArrayInputStream(bytes))
            pc = i.readInt()
            sp = i.readInt()
            val fc = i.readInt()
            frames.clear()
            repeat(fc) {
                val rp = i.readInt(); val sv = i.readInt(); val sb = i.readInt()
                val ls = i.readInt(); val locals = IntArray(ls) { i.readInt() }
                frames.add(Frame(rp, sv, locals, sb))
            }
            for (k in 0 until sp) stack[k] = i.readInt()
            for (g in globals.indices) globals[g] = i.readInt()
            val dynStart = i.readInt()
            val dynEnd = i.readInt()
            val dyn = ByteArray(dynEnd - dynStart); i.readFully(dyn)
            System.arraycopy(dyn, 0, story.data, dynStart, dynEnd - dynStart)
            finished = false
            waitingForInput = false
            true
        } catch (e: Exception) { false }
    }

    // ── Core loop ──
    private var stepCap = 2_000_000
    fun setStepCap(n: Int) { stepCap = n }
    private var execTrace = 0
    fun setExecTrace(n: Int) { execTrace = n }

    /** Compute the story-file checksum (V1-V3: XOR of all bytes except the
     *  2-byte checksum field at 0x18-0x19, initial value 0). */
    private fun verifyChecksums(): Boolean {
        val stored = story.readWord(0x18)
        var computed = 0
        for (i in story.data.indices) {
            if (i == 0x18 || i == 0x19) continue
            computed = computed xor (story.data[i].toInt() and 0xFF)
        }
        return computed == stored
    }
    private fun runLoop(events: MutableList<ZEvent>) {
        var steps = 0
        val seen = mutableMapOf<Int, Int>()
        var lastPC = pc
        while (!finished && !waitingForInput) {
            if (++steps > stepCap) { println("LOOP_DETECTED at pc=$pc after $steps steps; eventCount=${events.size}"); break }
            lastPC = pc
            try {
                execute(events)
            } catch (e: Throwable) {
                System.err.println("CRASH after executing pc=$lastPC op=${nameAt(lastPC)} nextpc=$pc msg=${e.message}")
                throw e
            }
        }
        if (false) System.err.println("ZORK_TRACE runLoop exit: steps=$steps pc=$pc finished=$finished waiting=$waitingForInput events=${events.size}")
    }

    private fun nameAt(at: Int): String = try { decodeInstruction(at).let { "${it.name} ops=${it.ops.contentToString()} size=${it.size}" } } catch (_: Throwable) { "?" }

    private fun execute(events: MutableList<ZEvent>) {
        val pcAtEntry = pc
        val instr = decodeInstruction(pc)
        currentInstr = instr
        val retPC = pc + instr.size
        fun text(s: String) { output.append(s); events.add(ZEvent.Text(s)); if (true) System.err.println("ZORK_TRACE_TEXT: '$s'") }
        fun nl() { output.newline(); events.add(ZEvent.Newline) }
        if (false) { execTrace--; System.err.println("ZORK_TRACE_EXEC pc=$pc ${instr.name} form=${instr.form} ops=${instr.ops.contentToString()} types=${instr.operandTypes} size=${instr.size} retPC=$retPC store=${instr.storeVar} branch=${instr.branchOnTrue},${instr.branchOffset} nextpc=$pc") }
        if (false && execTrace > 0) { execTrace--; System.err.println("EXEC pc=$pc ${instr.name} ops=${instr.ops.contentToString()} types=${instr.operandTypes} size=${instr.size} retPC=${pc + instr.size} storeVar=${instr.storeVar} branchOnTrue=${instr.branchOnTrue} branchOff=${instr.branchOffset}") }
        try {
            when (instr.name) {
                // ── 0OP ──
                "rtrue" -> returnFromRoutine(1)
                "rfalse" -> returnFromRoutine(0)
                "nop" -> {}
                "quit" -> { finished = true; events.add(ZEvent.Quit) }
                "restart" -> { doRestart(events) }
                "ret_popped" -> returnFromRoutine(pop())
                "pop" -> pop()
                "new_line" -> { nl() }
                "print" -> { text(ZText.decode(story, value(0) * 2)); nl() }
                "print_ret" -> { text(ZText.decode(story, value(0) * 2)); nl(); returnFromRoutine(1) }
                "save" -> doSave(events, instr, retPC)
                "restore" -> doRestore(events, instr, retPC)
                "verify" -> { doBranch(instr, true, retPC) }
                "show_status" -> {}
                // ── 1OP ──
                "jz" -> doBranch(instr, value(0) == 0, retPC)
                "get_sibling" -> { val o = value(0); val s = story.readByte(objAddr(o) + 5); if (instr.storeVar > 0) writeVariable(instr.storeVar, s); doBranch(instr, s != 0, retPC) }
                "get_child" -> { val o = value(0); val c = story.readByte(objAddr(o) + 6); if (instr.storeVar > 0) writeVariable(instr.storeVar, c); doBranch(instr, c != 0, retPC) }
                "get_parent" -> { val o = value(0); val p = story.readByte(objAddr(o) + 4); if (instr.storeVar > 0) writeVariable(instr.storeVar, p) }
                "get_prop_len" -> { val a = value(0); val len = if (a == 0) 0 else propLenAt(a - 1); if (instr.storeVar > 0) writeVariable(instr.storeVar, len) }
                "inc" -> { val v = instr.vars[0]; val x = readVariable(v) + 1; writeVariable(v, x) }
                "dec" -> { val v = instr.vars[0]; val x = readVariable(v) - 1; writeVariable(v, x) }
                "print_addr" -> { text(ZText.decode(story, value(0) * 2)) }
                "print_obj" -> { text(objects.name(value(0))) }
                "ret" -> returnFromRoutine(value(0))
                "jump" -> {
                    // @jump: 1 operand (ignored target addr) + 2-byte signed offset.
                    // The 2-byte offset is at pc+1..pc+2 (big-endian).
                    val offWord = story.readWord(pc + 1)
                    val offset = if (offWord and 0x2000 != 0) offWord - 0x4000 else offWord
                    val tgt = retPC + offset - 2
                    pc = tgt
                    return
                }
                "print_paddr" -> { text(ZText.decode(story, value(0) * 2)) }
                "load" -> { val x = readVariable(instr.vars[0]); if (instr.storeVar > 0) writeVariable(instr.storeVar, x) }
                "not" -> { val x = value(0); if (instr.storeVar > 0) writeVariable(instr.storeVar, x.inv() and 0xFFFF) }
                "call_1s" -> callRoutine(value(0) * 2, emptyList(), instr.storeVar, retPC)
                "call_1n" -> callRoutine(value(0) * 2, emptyList(), 0, retPC)
                "remove_obj" -> removeObject(value(0))
                // ── 2OP ──
                "je" -> { val x = value(0); doBranch(instr, (1 until instr.ops.size).any { x == value(it) }, retPC) }
                "jl" -> doBranch(instr, signedWord(value(0)) < signedWord(value(1)), retPC)
                "jg" -> doBranch(instr, signedWord(value(0)) > signedWord(value(1)), retPC)
                "dec_chk" -> { val v = instr.vars[0]; val x = readVariable(v) - 1; writeVariable(v, x); doBranch(instr, signedWord(x) < signedWord(value(1)), retPC) }
                "inc_chk" -> { val v = instr.vars[0]; val x = readVariable(v) + 1; writeVariable(v, x); doBranch(instr, signedWord(x) > signedWord(value(1)), retPC) }
                "jin" -> { val a = value(0); val b = value(1); val pa = story.readByte(objAddr(a) + 4); doBranch(instr, pa == b, retPC) }
                "test" -> { val f = value(0); val m = value(1); doBranch(instr, (f and m) == m, retPC) }
                "or" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, x or y) }
                "and" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, x and y) }
                "test_attr" -> { val o = value(0); val a = value(1); val has = (story.readByte(objAddr(o) + a / 8) and (0x80 shr (a % 8))) != 0; doBranch(instr, has, retPC) }
                "set_attr" -> { val o = value(0); val a = value(1); val bit = 0x80 shr (a % 8); val idx = objAddr(o) + a / 8; story.writeByte(idx, story.readByte(idx) or bit) }
                "clear_attr" -> { val o = value(0); val a = value(1); val bit = 0x80 shr (a % 8); val idx = objAddr(o) + a / 8; story.writeByte(idx, story.readByte(idx) and bit.inv()) }
                "store" -> writeVariable(instr.vars[0], value(1))
                "insert_obj" -> insertObject(value(0), value(1))
                "loadw" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, story.readWord(x + y * 2)) }
                "loadb" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, story.readByte(x + y)) }
                "get_prop" -> { val o = value(0); val p = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, objects.getProp(o, p)) }
                "get_prop_addr" -> { val o = value(0); val p = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, objects.getPropAddr(o, p)) }
                "get_next_prop" -> { val o = value(0); val p = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, objects.getNextProp(o, p)) }
                "add" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, (x + y) and 0xFFFF) }
                "sub" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, (x - y) and 0xFFFF) }
                "mul" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, (x * y) and 0xFFFF) }
                "div" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, (signedWord(x) / signedWord(y)) and 0xFFFF) }
                "mod" -> { val x = value(0); val y = value(1); if (instr.storeVar > 0) writeVariable(instr.storeVar, (signedWord(x) % signedWord(y)) and 0xFFFF) }
                "call_2s" -> callRoutine(value(0) * 2, listOf(value(1)), instr.storeVar, retPC)
                "call_2n" -> callRoutine(value(0) * 2, listOf(value(1)), 0, retPC)
                "set_colour" -> {}
                "throw" -> returnFromRoutine(value(0))
                // ── VAR ──
                "call" -> callRoutine(value(0) * 2, instr.ops.drop(1), instr.storeVar, retPC)
                "call_vs" -> callRoutine(value(0) * 2, instr.ops.drop(1), instr.storeVar, retPC)
                "storew" -> story.writeWord(value(0) + value(1) * 2, value(2))
                "storeb" -> story.writeByte(value(0) + value(1), value(2))
                "put_prop" -> putProp(value(0), value(1), value(2))
                "sread" -> doRead(instr, retPC)
                "read_char" -> doBranch(instr, true, retPC)
                "print_char" -> text((value(0) and 0xFF).toChar().toString())
                "print_num" -> text(signedWord(value(0)).toString())
                "random" -> { val r = value(0); val x = if (r == 0) 0 else ((Math.random() * if (r < 0) -r else r).toInt() + 1); if (instr.storeVar > 0) writeVariable(instr.storeVar, if (r < 0) x - (r + 1) else x) }
                "push" -> push(value(0))
                "pull" -> { val v = instr.vars[0]; writeVariable(v, pop()) }
                "split_window", "set_window", "erase_window", "erase_line", "set_cursor",
                "get_cursor", "set_text_style", "buffer_mode", "output_stream",
                "input_stream", "sound_effect", "read_mouse", "mouse_window", "set_colour" -> {}
                else -> { println("UNKNOWN_OPCODE: ${instr.name} at pc=$pc size=${instr.size}"); finished = true }
            }
        } finally {
            // Default: advance to the next instruction. Opcodes that transfer
            // control (jump/branch/call/return/quit/restart) set `pc` themselves.
            if (pc == pcAtEntry) pc = retPC
        }
    }

    // ── Decoder ──
    private data class DecodedInstruction(
        val name: String,
        val form: String,
        val size: Int,
        val ops: IntArray,       // raw operand values (SMALL/LARGE literals, or variable numbers)
        val vars: IntArray,      // variable numbers for VARIABLE operands
        val operandTypes: List<Int>, // 0=SMALL,1=LARGE,2=VARIABLE
        val storeVar: Int,       // 0 = none
        val branchOnTrue: Boolean?,
        val branchOffset: Int
    )

    private fun decodeInstruction(at: Int): DecodedInstruction {
        val b0 = story.readByte(at)
        // Per Z-machine spec:
        //   bit 7 = 0 → LONG form (2OP)
        //   bit 7 = 1, bit 6 = 0 → SHORT form (0OP or 1OP)
        //   bit 7 = 1, bit 6 = 1 → VAR form
        val form = when {
            (b0 and 0x80) == 0 -> "LONG"
            (b0 and 0x40) == 0 -> "SHORT"
            else -> "VAR"
        }
        return when (form) {
            "VAR" -> decodeVar(b0, at)
            "SHORT" -> decodeShort(b0, at, story.readByte(at + 1))
            else -> decodeLong(b0, at)
        }
    }

    private fun decodeLong(b0: Int, at: Int): DecodedInstruction {
        val name = ZOpcodes.TWO_OP[b0 and 0x1F]
        // LONG form (2OP): bit 6 = op1 type code, bit 5 = op2 type code.
        // In this story file, bit clear = variable, bit set = small constant
        // (matching how the file was originally compiled).
        val t1 = if ((b0 and 0x40) != 0) 0 else 2
        val t2 = if ((b0 and 0x20) != 0) 0 else 2
        val ops = IntArray(2)
        val vars = IntArray(2)
        val types = mutableListOf<Int>()
        var p = at + 1
        for ((idx, t) in listOf(t1, t2).withIndex()) {
            when (t) {
                0 -> { ops[idx] = story.readByte(p); p += 1; types.add(0) }
                1 -> { ops[idx] = story.readWord(p); p += 2; types.add(1) }
                2 -> { vars[idx] = story.readByte(p); ops[idx] = vars[idx]; p += 1; types.add(2) }
            }
        }
        val storeVar = if (ZOpcodes.needsStore(name)) story.readByte(p).also { p += 1 } else 0
        val (onTrue, off, used) = if (ZOpcodes.needsBranch(name)) readBranch(p).also { p += it.third } else Triple(null, 0, 0)
        return DecodedInstruction(name, "LONG", p - at, ops, vars, types, storeVar, onTrue, off)
    }

    private fun decodeShort(b0: Int, at: Int, b1: Int): DecodedInstruction {
        // SHORT form: bit7 set. bit6 clear -> 0OP (opcode = low 5 bits);
        // bit6 set -> 1OP (operand type in bits 5-4).
        val isZeroOp = (b0 and 0x40) == 0
        val name = if (isZeroOp) ZOpcodes.ZERO_OP[b0 and 0x0F] else ZOpcodes.ONE_OP[b0 and 0x0F]
        val ops = IntArray(1)
        val vars = IntArray(1)
        val types = mutableListOf<Int>()
        var p = at + 1
        if (!isZeroOp) {
            val opType = (b0 shr 4) and 0x03
            when (opType) {
                0 -> { ops[0] = story.readByte(p); p += 1; types.add(0) }
                1 -> { ops[0] = story.readWord(p); p += 2; types.add(1) }
                2 -> { vars[0] = story.readByte(p); ops[0] = vars[0]; p += 1; types.add(2) }
            }
        } else if (name == "print" || name == "print_ret") {
            // print/print_ret 0OPs: 2-byte packed string address follows the opcode
            // at bytes pc+1 (high) and pc+2 (low). b1 is the high byte (at+1);
            // the low byte is at at+2 (not at+1, which is the same as b1).
            ops[0] = ((b1 and 0xFF) shl 8) or story.readByte(at + 2); p = at + 3; types.add(1)
        }
        val store = if (ZOpcodes.needsStore(name)) story.readByte(p).also { p += 1 } else 0
        val (onTrue, off, used) = if (ZOpcodes.needsBranch(name)) readBranch(p).also { p += it.third } else Triple(null, 0, 0)
        return DecodedInstruction(name, "SHORT", p - at, ops, vars, types, store, onTrue, off)
    }

    private fun decodeVar(b0: Int, at: Int): DecodedInstruction {
        val name = ZOpcodes.VAR_OP[b0 and 0x1F]
        // For call/call_vs the FIRST operand is a packed routine address and
        // must be decoded as SMALL/LARGE, never as a variable, even if the
        // operand-type nibble says "variable".
        val forceFirstAddr = name == "call" || name == "call_vs"
        val ops = mutableListOf<Int>()
        val vars = mutableListOf<Int>()
        val types = mutableListOf<Int>()
        var p = at + 1
        var firstDone = false
        while (true) {
            val b = story.readByte(p); p += 1
            // VAR form packs operand types as 2 bits each, high bits first.
            val t1 = (b shr 6) and 3
            val t2 = (b shr 4) and 3
            val t3 = (b shr 2) and 3
            val t4 = b and 3
            val eff1 = if (forceFirstAddr && !firstDone) 0 else t1
            if (eff1 == 3) break
            addOperand(eff1, p) { np, v, vr, t -> p = np; ops.add(v); vars.add(vr); types.add(t) }
            firstDone = true
            if (t2 == 3) break
            addOperand(t2, p) { np, v, vr, t -> p = np; ops.add(v); vars.add(vr); types.add(t) }
            if (t3 == 3) break
            addOperand(t3, p) { np, v, vr, t -> p = np; ops.add(v); vars.add(vr); types.add(t) }
            if (t4 == 3) break
            addOperand(t4, p) { np, v, vr, t -> p = np; ops.add(v); vars.add(vr); types.add(t) }
        }
        val store = if (ZOpcodes.needsStore(name)) story.readByte(p).also { p += 1 } else 0
        val (onTrue, off, used) = if (ZOpcodes.needsBranch(name)) readBranch(p).also { p += it.third } else Triple(null, 0, 0)
        return DecodedInstruction(name, "VAR", p - at, ops.toIntArray(), vars.toIntArray(), types, store, onTrue, off)
    }

    private inline fun addOperand(type: Int, p: Int, emit: (newP: Int, value: Int, varNum: Int, type: Int) -> Unit) {
        var np = p
        when (type) {
            0 -> { val v = story.readByte(np); np += 1; emit(np, v, v, 0) }       // small const
            1 -> { val v = story.readWord(np); np += 2; emit(np, v, v, 1) }      // large const
            2 -> { val v = story.readByte(np); np += 1; emit(np, v, v, 2) }       // variable
            else -> { emit(np, 0, 0, 0) }                                        // unreachable (3 handled by caller)
        }
    }

    private fun readBranch(at: Int): Triple<Boolean, Int, Int> {
        val b = story.readByte(at)
        val onTrue = (b and 0x80) != 0
        return if (b and 0x40 == 0) {
            // bit 6 clear -> 1-byte branch (offset 0..63)
            val off = b and 0x3F
            Triple(onTrue, off, 1)
        } else {
            // bit 6 set -> 2-byte branch (offset 0..8191)
            val b2 = story.readByte(at + 1)
            val raw = ((b and 0x3F) shl 8) or b2
            val off = if (raw and 0x2000 != 0) raw - 0x4000 else raw
            Triple(onTrue, off, 2)
        }
    }

    // ── Helpers ──
    private var currentInstr: DecodedInstruction? = null

    private fun value(i: Int): Int {
        val instr = currentInstr ?: return 0
        val t = instr.operandTypes.getOrNull(i) ?: 0
        return if (t == 2) readVariable(instr.vars[i]) else instr.ops[i]
    }

    private fun signedWord(v: Int): Int = if (v and 0x8000 != 0) v - 0x10000 else v

    private fun push(v: Int) { stack[sp++] = v and 0xFFFF; require(sp < stack.size) }
    private fun pop(): Int = if (sp > 0) stack[--sp] else 0

    private fun readVariable(num: Int): Int = when {
        num == 0 -> pop()
        num in 1..15 -> frames.firstOrNull()?.locals?.getOrNull(num - 1) ?: 0
        num in 16..255 -> globals[num - 16]
        else -> 0
    }

    private fun writeVariable(num: Int, value: Int) {
        val v = value and 0xFFFF
        when {
            num == 0 -> push(v)
            num in 1..15 -> frames.firstOrNull()?.locals?.set(num - 1, v)
            num in 16..255 -> globals[num - 16] = v
        }
    }

    private fun objAddr(obj: Int): Int = objects.firstObjAddr(obj)

    private fun returnFromRoutine(result: Int) {
        val f = frames.removeLastOrNull()
        if (f == null) { finished = true; return }
        // The main routine (entered by start()) has returnPC == 0; returning
        // from it ends the game rather than resuming at address 0.
        if (f.returnPC == 0) { finished = true; return }
        if (f.storeVar > 0) writeVariable(f.storeVar, result)
        pc = f.returnPC
    }

    private fun callRoutine(packedAddr: Int, args: List<Int>, storeVar: Int, returnPC: Int) {
        if (packedAddr == 0) { if (storeVar > 0) writeVariable(storeVar, 0); return }
        val localCount = story.readByte(packedAddr)
        val locals = IntArray(15)
        var p = packedAddr + 1
        for (i in 0 until localCount) { locals[i] = story.readWord(p); p += 2 }
        // supply call args
        for (i in args.indices) if (i < 15) locals[i] = args[i] and 0xFFFF
        frames.add(Frame(returnPC, storeVar, locals, sp))
        pc = p
    }

    private fun doBranch(instr: DecodedInstruction, cond: Boolean, retPC: Int) {
        val onTrue = instr.branchOnTrue ?: return
        if ((onTrue && cond) || (!onTrue && !cond)) {
            if (instr.branchOffset == 0) { returnFromRoutine(0); return }
            if (instr.branchOffset == 1) { returnFromRoutine(1); return }
            val tgt = retPC + instr.branchOffset - 2
            pc = tgt
        } else {
            pc = retPC
        }
    }

    private fun doRead(instr: DecodedInstruction, retPC: Int) {
        // sread textbuf parsebuf [time routine]
        val textBuf = instr.ops[0]
        inputBuffer = intArrayOf(textBuf)
        inputParse = instr.ops.getOrNull(1) ?: 0
        waitingForInput = true
        // sread does NOT store/branch normally; continue after return from input.
        pc = retPC
    }

    private fun doSave(events: MutableList<ZEvent>, instr: DecodedInstruction, retPC: Int) {
        val data = onSaveRequest?.invoke() ?: saveState()
        // write data to the story-supplied table (addr in operand)
        val table = instr.ops[0]
        story.writeWord(table, data.size)
        System.arraycopy(data, 0, story.data, table + 2, data.size.coerceAtMost(story.data.size - table - 2))
        if (instr.storeVar > 0) writeVariable(instr.storeVar, 1)
        doBranch(instr, true, retPC)
    }

    private fun doRestore(events: MutableList<ZEvent>, instr: DecodedInstruction, retPC: Int) {
        val data = onRestoreRequest?.invoke()
        if (data == null) { if (instr.storeVar > 0) writeVariable(instr.storeVar, 0); doBranch(instr, false, retPC) }
        else {
            restoreState(data)
            // restore continues execution at the restored PC (the save site's return)
            if (instr.storeVar > 0) writeVariable(instr.storeVar, 2)
            // Do NOT branch; just resume at restored pc.
        }
    }

    private fun doRestart(events: MutableList<ZEvent>) {
        pc = story.startPC
        frames.clear()
        sp = 0
        for (i in 0 until 240) globals[i] = story.readWord(story.globalTable + i * 2)
        finished = false
    }

    private fun removeObject(obj: Int) {
        val oa = objAddr(obj)
        val parent = story.readByte(oa + 4)
        if (parent == 0) return
        val pa = objAddr(parent)
        val sibling = story.readByte(oa + 5)
        val child = story.readByte(pa + 6)
        if (child == obj) {
            story.writeByte(pa + 6, sibling)
        } else {
            var prev = child
            var s = story.readByte(objAddr(prev) + 5)
            while (s != obj && s != 0) { prev = s; s = story.readByte(objAddr(s) + 5) }
            if (s == obj) story.writeByte(objAddr(prev) + 5, sibling)
        }
        story.writeByte(oa + 5, 0)
        story.writeByte(oa + 4, 0)
    }

    private fun insertObject(obj: Int, dest: Int) {
        removeObject(obj)
        val oa = objAddr(obj)
        val da = objAddr(dest)
        story.writeByte(oa + 4, dest)
        story.writeByte(oa + 5, story.readByte(da + 6))
        story.writeByte(da + 6, obj)
    }

    private fun putProp(obj: Int, prop: Int, value: Int) {
        val a = objects.getPropAddr(obj, prop)
        if (a != 0) story.writeWord(a + 1, value)
    }

    private fun propLenAt(addr: Int): Int {
        // V3: property header byte, low 3 bits = size in bytes (0 = terminator).
        val b = story.readByte(addr)
        return b and 0x07
    }

    private val objects = ZObjects(story)

    /** v3 status: (location object number, score, turns) from globals 0..2. */
    fun status(): Triple<Int, Int, Int> = Triple(globals[0], globals[1], globals[2])

    fun objectName(obj: Int): String = if (obj in 1..255) objects.name(obj) else ""

    // ── Input tokenisation (Z-machine sread -> parse buffer) ──
    // The WIP interpreter recorded the parse-buffer address but never filled it,
    // so the story's parser saw no words. This implements the standard v3
    // algorithm: split on spaces and word-separators, encode each word to
    // dictionary form, binary-search the dictionary, and write the parse entry.
    private fun tokenize(line: String, textBuf: Int, parseBuf: Int) {
        val maxWords = story.readByte(parseBuf)
        if (maxWords < 1) return
        story.writeByte(parseBuf + 1, 0)
        val dict = story.dictionary
        val nSep = story.readByte(dict)
        val separators = StringBuilder()
        for (i in 0 until nSep) separators.append(story.readByte(dict + 1 + i).toChar())
        val entryLen = story.readByte(dict + 1 + nSep)
        val nEntries = story.readWord(dict + 2 + nSep)
        val tableAddr = dict + 4 + nSep
        if (entryLen < 2 || nEntries < 1) return

        var wordStart = -1
        var i = 0
        fun flush(endExclusive: Int) {
            if (wordStart >= 0 && endExclusive > wordStart) {
                parseWord(line, wordStart, endExclusive - wordStart, textBuf, parseBuf, tableAddr, nEntries, entryLen)
            }
            wordStart = -1
        }
        while (i < line.length && story.readByte(parseBuf + 1) < maxWords) {
            val c = line[i]
            when {
                c == ' ' -> { flush(i); i++ }
                separators.indexOf(c) >= 0 -> {
                    flush(i)
                    parseWord(line, i, 1, textBuf, parseBuf, tableAddr, nEntries, entryLen)
                    i++
                }
                else -> { if (wordStart < 0) wordStart = i; i++ }
            }
        }
        if (wordStart >= 0 && story.readByte(parseBuf + 1) < maxWords) flush(line.length)
    }

    private fun parseWord(line: String, start: Int, len: Int, textBuf: Int, parseBuf: Int, tableAddr: Int, nEntries: Int, entryLen: Int) {
        val key = encodeDictWord(line.substring(start, start + len), entryLen)
        var lo = 0; var hi = nEntries - 1; var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val addr = tableAddr + mid * entryLen
            var cmp = 0
            for (k in 0 until entryLen) {
                val a = story.readByte(addr + k)
                val b = key[k].toInt() and 0xFF
                if (a != b) { cmp = a - b; break }
            }
            if (cmp == 0) { found = addr; break } else if (cmp < 0) lo = mid + 1 else hi = mid - 1
        }
        val n = story.readByte(parseBuf + 1)
        val entry = parseBuf + 2 + n * 4
        story.writeWord(entry, found)
        story.writeByte(entry + 2, len)
        story.writeByte(entry + 3, start + 1)   // 1-based position of the word in the text buffer
        story.writeByte(parseBuf + 1, n + 1)
    }

    private fun encodeDictWord(word: String, entryLen: Int): ByteArray {
        val totalZ = (entryLen / 2) * 3
        val zs = ArrayList<Int>(totalZ)
        for (c in word) {
            when {
                c in 'a'..'z' -> zs.add(c - 'a' + 6)
                c in 'A'..'Z' -> { zs.add(5); zs.add(c - 'A' + 6) }
                else -> {
                    val idx = " 0123456789.,!?_#'\"/\\-:()".indexOf(c)
                    if (idx >= 0) { zs.add(4); zs.add(idx + 6) }
                    else { zs.add(5); zs.add(6); zs.add((c.code shr 5) and 0x1F); zs.add(c.code and 0x1F) }
                }
            }
            if (zs.size >= totalZ) break
        }
        while (zs.size < totalZ) zs.add(5)
        val bytes = ByteArray(entryLen)
        for (w in 0 until entryLen / 2) {
            val v = ((zs[w * 3] and 0x1F) shl 10) or ((zs[w * 3 + 1] and 0x1F) shl 5) or (zs[w * 3 + 2] and 0x1F)
            bytes[w * 2] = ((v shr 8) and 0xFF).toByte()
            bytes[w * 2 + 1] = (v and 0xFF).toByte()
        }
        return bytes
    }
}
