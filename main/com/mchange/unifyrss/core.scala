package com.mchange.unifyrss

import scala.collection.*
import unstatic.UrlPath.*
import zio.*
import sttp.tapir.Endpoint

val linesep = System.lineSeparator

class UnifyRssException( message : String, cause : Throwable = null ) extends Exception( message, cause )

class IncompatibleDuplicateBindings(bindings : immutable.Set[(String,String)]) extends UnifyRssException(s"Incompatible duplicate bindings! ${bindings}", null)
class BadItemXml(message : String, cause : Throwable = null)                   extends UnifyRssException( message, cause )
class RssFetchFailure(message : String, cause : Throwable = null)              extends UnifyRssException( message, cause )

type FeedRefMap      = immutable.Map[Rel,Ref[immutable.Seq[Byte]]]
type FeedEndpointMap = immutable.Map[Rel,Endpoint[Unit,Unit,Unit,Array[Byte],Any]]