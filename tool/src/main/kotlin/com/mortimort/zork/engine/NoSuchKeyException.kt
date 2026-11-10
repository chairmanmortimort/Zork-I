package com.thelightphone.zork.engine

internal class NoSuchKeyException : Exception {
    constructor() : super()
    constructor(s: String) : super(s)
}
