package com.thelightphone.zork.engine

/** Thrown when the Z-code is somehow messed up (Zplet port). */
class GrueException : RuntimeException {
    constructor() : super()
    constructor(detailMessage: String) : super(detailMessage)
    constructor(throwable: Throwable) : super(throwable)
    constructor(detailMessage: String, throwable: Throwable) : super(detailMessage, throwable)
}
