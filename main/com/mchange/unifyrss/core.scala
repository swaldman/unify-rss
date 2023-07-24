package com.mchange.unifyrss

import scala.collection.*

val linesep = System.lineSeparator

class UnifyRssException( message : String, cause : Throwable = null ) extends Exception( message, cause )

class IncompatibleDuplicateBindings(bindings : immutable.Set[(String,String)]) extends UnifyRssException(s"Incompatible duplicate bindings! ${bindings}", null)
class BadItemXml(message : String, cause : Throwable = null) extends UnifyRssException( message, cause )
