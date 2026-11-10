package com.thelightphone.zork.engine

import java.util.Arrays

/** A screen window / text buffer (ported from Zplet's ZWindow). */
class ZWindow(private val screen: ZScreen, val upper: Boolean) {
    var cursorx: Int = 0
    var cursory: Int = 0
    var curzfont: Int = NORMAL_FONT
    var curzstyle: Int = ROMAN
    var zforeground: Int = screen.zforeground
    var zbackground: Int = screen.zbackground

    var regions: StyleRegion? = null
    var cursor: Int = 0
    var maxCursor: Int = 0
    var endWindow: Int = 0
    var startWindow: Int = 0

    private var top: Int = 0
    private var left: Int = 0
    private var width: Int = 10
    private var height: Int = 10
    private var line_counter: Int = 0
    private var stylestart: Int = 0

    val frameBuffer: CharArray = if (upper) {
        CharArray(screen.chars * screen.lines * 2).also { Arrays.fill(it, ' ') }
    } else {
        CharArray(screen.chars * 255)
    }

    fun reset_line_count() { line_counter = 0 }
    fun count_line() { line_counter++ }
    fun erase_line(arg: Short) {}

    fun moveto(newleft: Int, newtop: Int) {
        left = newleft
        top = newtop
        startWindow = top * width + left
        endWindow = startWindow + width * height
    }

    fun resize(newwidth: Int, newheight: Int) {
        width = newwidth
        height = newheight
        if (upper) {
            if (cursorx >= newwidth || cursory >= newheight) {
                cursorx = 0
                cursory = 0
                cursor = 0
            }
        }
        startWindow = top * width + left
        endWindow = startWindow + width * height
    }

    fun getlines(): Int = height
    fun getchars(): Int = width
    fun getx(): Int = cursorx
    fun gety(): Int = cursory
    fun getHeight(): Int = height

    fun movecursor(x: Int, y: Int) {
        if (upper) {
            cursorx = x
            cursory = y
            cursor = y * width + x
            maxCursor = maxOf(cursor, maxCursor)
        }
    }

    fun printzascii(ascii: Short) { printChar(ZScreen.zascii_to_unicode(ascii)) }
    fun flush() {}
    fun newline() { printChar('\n') }

    private fun printChar(ch: Char) {
        frameBuffer[cursor] = ch
        if (cursor < frameBuffer.size - 1) {
            cursor++
            maxCursor = maxOf(cursor, maxCursor)
            cursorx++
            if (cursorx > screen.chars - 2 || ch == '\n') {
                cursory++
                cursorx = 0
            }
        }
    }

    fun clear() {
        if (upper) {
            cursor = top * width + left
            Arrays.fill(frameBuffer, startWindow, endWindow, ' ')
        } else {
            cursor = 0
        }
        cursorx = 0
        cursory = 0
        maxCursor = cursor
    }

    fun set_text_style(style: Int) {
        val region = StyleRegion()
        region.style = style
        region.start = cursor
        region.end = cursor
        if (regions == null) {
            regions = region
        } else {
            var tmp = regions
            while (tmp!!.next != null) tmp = tmp.next!!
            tmp.end = cursor
            tmp.next = region
        }
    }

    /** Tell the buffer that it got fetched and can be overwritten on the next turn. */
    fun retrieved() {
        if (upper) {
            if (maxCursor > endWindow) {
                Arrays.fill(frameBuffer, endWindow, maxCursor, ' ')
            }
            maxCursor = endWindow
        } else {
            cursorx = 0
            cursory = 0
            cursor = 0
        }
        regions = null
    }

    /** Transform a portion of the framebuffer into a string, adding newlines by width. */
    fun stringyfy(start: Int, end: Int): String {
        if (width < 1 || start >= end) return ""
        val len = width
        val total = end - start
        val tmp = CharArray(total + total / len)
        var i = start
        var o = 0
        while (i < total - len) {
            System.arraycopy(frameBuffer, i, tmp, o, len)
            i += len
            o += len + 1
            tmp[o - 1] = '\n'
        }
        if (i < total) {
            System.arraycopy(frameBuffer, i, tmp, o, total - i)
        }
        return String(tmp)
    }

    /** Length of the buffer excluding the trailing prompt line. */
    fun noPrompt(): Int {
        var ret = cursor - 1
        if (ret < 0) return cursor
        while (ret > 0 && frameBuffer[ret] == ' ') ret--
        if (ret > 1 && frameBuffer[ret] == '>') {
            ret--
        } else {
            return cursor
        }
        while (ret > 0 && frameBuffer[ret] == '\n') ret--
        ret++
        return ret
    }

    companion object {
        const val ROMAN = 0
        const val REVERSE = 1
        const val BOLD = 2
        const val ITALIC = 4
        const val FIXED = 8

        const val FIRST_STYLE = '\u8000'
        const val BUF_ROMAN = (FIRST_STYLE + ROMAN)
        const val BUF_REVERSE = (FIRST_STYLE + REVERSE)
        const val BUF_BOLD = (FIRST_STYLE + BOLD)
        const val BUF_ITALIC = (FIRST_STYLE + ITALIC)
        const val BUF_FIXED = (FIRST_STYLE + FIXED)
        const val LAST_STYLE = '\u800f'

        const val NORMAL_FONT = 1
        const val PICTURE_FONT = 2
        const val GRAPHICS_FONT = 3
        const val FIXED_FONT = 4

        const val FIRST_FONT = '\u8010'
        const val BUF_NORMAL_FONT = '\u8010'
        const val BUF_PICTURE_FONT = '\u8011'
        const val BUF_GRAPHICS_FONT = '\u8012'
        const val BUF_FIXED_FONT = '\u8013'
        const val LAST_FONT = '\u8013'
    }
}
