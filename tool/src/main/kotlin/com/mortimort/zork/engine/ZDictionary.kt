package com.thelightphone.zork.engine

/** Dictionary / lexer (ported from Zplet's ZDictionary). */
abstract class ZDictionary {
    abstract fun tokenise(textloc: Int, textlength: Int, parseloc: Int)
    abstract fun parse_word(textloc: Int, wordloc: Int, wordlength: Int, parseloc: Int): Boolean
}
