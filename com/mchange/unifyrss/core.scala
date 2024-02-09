package com.mchange.unifyrss

import scala.annotation.tailrec
import scala.xml.*
import scala.collection.*
import unstatic.UrlPath.*
import zio.*
import sttp.tapir.Endpoint
import audiofluidity.rss.Namespace

val linesep = System.lineSeparator

class UnifyRssException( message : String, cause : Throwable = null ) extends Exception( message, cause )

class IncompatibleNamespaces(namespaces : immutable.Set[Namespace]) extends UnifyRssException(s"Incompatible namespaces! ${namespaces}", null)
class BadItemXml(message : String, cause : Throwable = null)                   extends UnifyRssException( message, cause )
class BadAtomXml(message : String, cause : Throwable = null)                   extends UnifyRssException( message, cause )
class XmlFetchFailure(message : String, cause : Throwable = null)              extends UnifyRssException( message, cause )
class CantConvertToRss(message : String, cause : Throwable = null)              extends UnifyRssException( message, cause )

type FeedRefMap      = immutable.Map[Rel,Ref[immutable.Seq[Byte]]]
type FeedEndpointMap = immutable.Map[Rel,Endpoint[Unit,Unit,String,Array[Byte],Any]]

