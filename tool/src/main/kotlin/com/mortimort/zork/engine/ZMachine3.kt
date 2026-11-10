package com.thelightphone.zork.engine

/** Version-3 machine (ported from Zplet's ZMachine3). */
class ZMachine3(screen: ZScreen, status_line: ZStatus, memory_image: ByteArray) : ZMachine(screen, status_line, memory_image) {

    init {
        header = ZHeader3(memory_image)
        objects = ZObjectTree3(this)
        zd = ZDictionary3(this)
        globals = header.global_table()
        window = arrayOf(ZWindow(screen, false), ZWindow(screen, true))
        current_window = window[0]
        zi = ZInstruction(this)
        restart_state = ZState(this)
        restart_state.save_current()
    }

    override fun update_status_line() {
        val header = header as ZHeader3
        status_redirect = true
        status_location = ""
        print_string(objects!!.short_name_addr(get_variable(16)))
        status_redirect = false
        if (header.time_game()) {
            status_line.update_time_line(status_location ?: "", get_variable(17).toInt(), get_variable(18).toInt())
        } else {
            status_line.update_score_line(status_location ?: "", get_variable(17).toInt(), get_variable(18).toInt())
        }
        status_location = null
    }

    override fun string_address(addr: Short): Int = (addr.toInt() and 0xFFFF) shl 1
    override fun routine_address(addr: Short): Int = (addr.toInt() and 0xFFFF) shl 1

    override fun restart() {
        super.restart()
        window[0].moveto(0, 0)
        window[1].moveto(0, 0)
        window[0].resize(screen.getchars(), screen.getlines())
        window[1].resize(0, 0)
        window[0].movecursor(0, window[0].getHeight() - 1)
    }

    override fun set_header_flags() {
        val header = header as ZHeader3
        super.set_header_flags()
        header.set_status_unavailable(false)
        header.set_splitting_available(true)
        header.set_variable_default(false)
    }
}
