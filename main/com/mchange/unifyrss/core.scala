package com.mchange.unifyrss

import scala.collection.*

import scala.xml.Elem

import unstatic.UrlPath.*

val linesep = System.lineSeparator

class UnifyRssException( message : String, cause : Throwable = null ) extends Exception( message, cause )

class IncompatibleDuplicateBindings(bindings : immutable.Set[(String,String)]) extends UnifyRssException(s"Incompatible duplicate bindings! ${bindings}", null)
class BadItemXml(message : String, cause : Throwable = null) extends UnifyRssException( message, cause )

import java.net.URL

case class AppConfig( serverUrl : Abs, basePathServerRooted : Rooted, mergedFeeds : immutable.Set[MergedFeed] )

object MergedFeed:
  class Default( override val sourceUrls : immutable.Seq[URL], val basePath : String, override val refreshSeconds : Int = 600 ) extends MergedFeed:
    override def feedPath = Rel(s"${basePath}.rss")
    override def stubSiteContentType = "text/html"
    override def stubSitePath =
      val suffix = stubSiteContentType match
        case "text/html"  => "html"
        case "text/plain" => "txt"
        case _            => "txt"
      Rel(s"${basePath}.${suffix}")
    override def title( rootElems : immutable.Seq[Elem] ) : String =
      val titlesRaw = rootElems.map( _ \ "title").headOption.fold("untitled")( _.text ).map(s=>s"\"$s\"")
      val titleList = titlesRaw.length match
        case 0 => "(no feeds provided)"
        case 1 => titlesRaw(0)
        case 2 => titlesRaw(0) + " and " + titlesRaw(1)
        case _ => titlesRaw.init.map(s => s"${s}, ").mkString + " and " + titlesRaw.last
      s"Merger of feeds ${titleList}"
    override def stubSite( rootElems : immutable.Seq[Elem]) : String =
      val t = title(rootElems)
      val titles = rootElems.map( _ \ "title").headOption.fold("untitled")( _.text )
      val desc   = rootElems.map( _ \ "description").headOption.fold("no description")( _.text )
      val dlEntries = titles.zip(desc).map( (a,b) => s"<dt>$a</dt><dd>$b</dd>").mkString
      s"""
         |<html>
         |  <head><title>${title}</title></head>
         |  <body>
         |    <h1>${title}</h1>
         |    <dl>${dlEntries}</dl>
         |  </body>
         |</html>
         |""".stripMargin.trim
trait MergedFeed:
  def sourceUrls                                 : immutable.Seq[URL]
  def title( rootElems : immutable.Seq[Elem])    : String
  def feedPath                                   : Rel
  def stubSitePath                               : Rel
  def stubSite( rootElems : immutable.Seq[Elem]) : String
  def stubSiteContentType                        : String
  def refreshSeconds                             : Int