package com.thelightphone.zork.engine

/**
 * V3 opcode tables and operand/store/branch classification.
 *
 * A 0OP opcode may also branch (e.g. rtrue/rfalse are 0OP store-less;
 * only the branching 0OPs like save, restore branch).
 * We classify per-opcode below.
 */
object ZOpcodes {

    // ── Opcode name tables (index = opcode number) ──
    val ZERO_OP = arrayOf(
        "rtrue", "rfalse", "print", "print_ret", "nop", "save", "restore", "restart",
        "ret_popped", "pop", "quit", "new_line", "show_status", "verify", "?", "?",
        "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?"
    )

    val ONE_OP = arrayOf(
        "jz", "get_sibling", "get_child", "get_parent", "get_prop_len", "inc", "dec",
        "print_addr", "call_1s", "remove_obj", "print_obj", "ret", "jump", "print_paddr",
        "load", "not", "call_1n", "call_1s", "?", "?", "?", "?", "?", "?"
    )

    val TWO_OP = arrayOf(
        "je", "jl", "jg", "dec_chk", "inc_chk", "jin", "test", "or",
        "and", "test_attr", "set_attr", "clear_attr", "store", "insert_obj", "loadw", "loadb",
        "get_prop", "get_prop_addr", "get_next_prop", "add", "sub", "mul", "div", "mod",
        "call_2s", "call_2n", "set_colour", "throw", "?", "?", "?", "?"
    )

    val VAR_OP = arrayOf(
        "call", "storew", "storeb", "put_prop", "sread", "read_char", "print_char",
        "print_num", "random", "push", "pull", "split_window", "set_window", "call_vs",
        "erase_window", "erase_line", "set_cursor", "get_cursor", "set_text_style",
        "buffer_mode", "output_stream", "input_stream", "sound_effect", "read_mouse",
        "mouse_window", "push", "pull", "?", "?", "?", "?", "?", "?", "?", "?"
    )

    // ── Classification ──
    // Opcodes that STORE a result. Some also branch (e.g. get_sibling stores AND branches).
    private val STORE_OPS = setOf(
        "get_sibling", "get_child", "get_parent", "get_prop_len", "inc", "dec",
        "call_1s", "call_1n", "ret", "load", "not", "get_prop", "get_prop_addr",
        "get_next_prop", "add", "sub", "mul", "div", "mod", "call_2s", "call_2n",
        "call_vs", "random", "catch", "or", "and", "loadw", "loadb",
        "storew", "storeb", "put_prop"
    )
    // Opcodes that BRANCH (condition). These read a branch byte after the operands.
    // NOTE: pure branch opcodes (je, jl, jg, jin, test, test_attr) do NOT store.
    // Call opcodes (call_2s, call_vs, etc.) store but do NOT branch.
    private val BRANCH_OPS = setOf(
        "jz", "je", "jl", "jg", "dec_chk", "inc_chk", "jin", "test", "test_attr",
        "get_sibling", "get_child", "get_prop_len", "inc", "dec",
        "read_char", "random", "save", "restore", "verify"
    )

    fun needsStore(op: String): Boolean = STORE_OPS.contains(op)
    fun needsBranch(op: String): Boolean = BRANCH_OPS.contains(op)
}
