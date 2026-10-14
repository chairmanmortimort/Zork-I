package com.mortimort.zork.engine

/**
 * Output event produced by the interpreter, consumed by the UI.
 */
sealed class ZEvent {
    data class Text(val text: String) : ZEvent()
    object Newline : ZEvent()
    object InputPrompt : ZEvent()
    object Quit : ZEvent()
}

/**
 * Accumulates output between UI reads. Holds a String to avoid
 * platform-specific Appendable type variance issues.
 */
class ZOutputBuffer {
    private val sb = StringBuilder()

    fun append(text: String) {
        sb.append(text)
    }

    fun append(char: Char) {
        sb.append(char)
    }

    fun newline() {
        sb.append('\n')
    }

    fun take(): String {
        val s = sb.toString()
        sb.setLength(0)
        return s
    }

    fun isEmpty(): Boolean = sb.isEmpty()
}
