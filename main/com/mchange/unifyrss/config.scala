package com.mchange.unifyrss

import scala.annotation.targetName
import scala.collection.*
import scala.xml.Elem
import java.net.URL
import unstatic.UrlPath.*

case class AppConfig( serverUrl : Abs, proxiedPort : Option[Int], appPathServerRooted : Rooted, mergedFeeds : immutable.Set[MergedFeed], verbose : Boolean = false ):
  def appPathAbs : Abs = serverUrl.embedRoot(appPathServerRooted)
  def servicePort =
    def fromUrlOrDefault : Int =
      val fromUrl = serverUrl.server.getPort
      if fromUrl >= 0 then fromUrl else 80
    proxiedPort.getOrElse( fromUrlOrDefault )

trait MergedFeed:
  def sourceUrls                                     : immutable.Seq[MergedFeed.SourceUrl]
  def itemLimit                                      : Int
  def title( rootElems : immutable.Seq[Elem] )       : String
  def description( rootElems : immutable.Seq[Elem] ) : String
  def feedPath                                       : Rel
  def stubSitePath                                   : Rel
  def stubSite( rootElems : immutable.Seq[Elem] )    : String
  def stubSiteContentType                            : String
  def refreshSeconds                                 : Int

object MergedFeed:
  object SourceUrl:
    def apply( url : URL )                               : SourceUrl = SourceUrl(url, identity)
    def apply( url : String)                             : SourceUrl = SourceUrl(new URL(url))
    def apply( url : String, transformer : Elem => Elem) : SourceUrl = SourceUrl( new URL(url), transformer )
  final case class SourceUrl( url : URL, transformer : Elem => Elem )
  object Default:
    @targetName("apply_from_URLs")
    def apply( urls : immutable.Seq[URL], baseName : String, itemLink : Int, refreshSeconds : Int ) : MergedFeed.Default =
      new Default( urls.map( SourceUrl.apply ), baseName, itemLink, refreshSeconds )
    @targetName("apply_from_Strings")
    def apply( urls : immutable.Seq[String], baseName : String, itemLink : Int = Int.MaxValue, refreshSeconds : Int = 600 ) : MergedFeed.Default =
      new Default( urls.map( SourceUrl.apply ), baseName, itemLink, refreshSeconds )
    def apply( sourceUrls : immutable.Seq[SourceUrl], baseName : String, itemLink : Int, refreshSeconds : Int ) : MergedFeed.Default =
      new Default( sourceUrls, baseName, itemLink, refreshSeconds )
  class Default(
    override val sourceUrls : immutable.Seq[SourceUrl],
    val baseName : String,
    override val itemLimit : Int = Int.MaxValue,
    override val refreshSeconds : Int = 600
  ) extends MergedFeed:
    override def feedPath = Rel(s"${baseName}.rss")
    override def stubSiteContentType = "text/html"
    override def stubSitePath =
      val suffix = stubSiteContentType match
        case "text/html"  => "html"
        case "text/plain" => "txt"
        case _            => "txt"
      Rel(s"${baseName}.${suffix}")
    private def titleDesc( rootElems : immutable.Seq[Elem] ) : String =
      val titlesRaw = rootElems.map( elem => (elem \ "channel" \ "title").headOption.fold("\"untitled\"")( s => s"\"${s.text}\"" ) )
      val titleList = titlesRaw.length match
        case 0 => "(no feeds provided)"
        case 1 => titlesRaw(0)
        case 2 => titlesRaw(0) + " and " + titlesRaw(1)
        case _ => titlesRaw.init.map(s => s"${s}, ").mkString + " and " + titlesRaw.last
      s"Merger of feeds ${titleList}"
    override def title( rootElems : immutable.Seq[Elem] ) : String = titleDesc(rootElems)
    override def description( rootElems : immutable.Seq[Elem] ) : String = titleDesc(rootElems)
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
  end Default
