package com.mchange.unifyrss

import scala.annotation.targetName
import scala.collection.*
import scala.xml.{Elem,XML}
import java.net.URL
import unstatic.UrlPath.*
import java.nio.file.{Path as JPath}

trait BaseConfig:
  /**
    * URLPath.Abs resolving to the directory from which rss will be served, either as static files or from memory by the daemon
    */
  def appPathAbs : Abs

  /**
    * The set of feeds (merged from multiple sources) to serve
    */
  def mergedFeeds : immutable.Set[MergedFeed]

  /**
    * Set to true for more verbose logging
    */
  def verbose : Boolean
end BaseConfig

/**
  * @param appPathAbs URLPath.Abs resolving to the directory from which rss will be served, either as static files or from memory by the daemon
  * @param appStaticDir Directory into which static files will be generated, from which they will be served
  * @param mergedFeeds The set of feeds (merged from multiple sources) to serve
  * @param verbose Set to true for more verbose logging
  */
case class StaticGenConfig( appPathAbs : Abs, appStaticDir : JPath, mergedFeeds : immutable.Set[MergedFeed], verbose : Boolean = false ) extends BaseConfig

case class DaemonConfig( serverUrl : Abs, proxiedPort : Option[Int], appPathServerRooted : Rooted, mergedFeeds : immutable.Set[MergedFeed], verbose : Boolean = false ) extends BaseConfig:
  def appPathAbs : Abs = serverUrl.embedRoot(appPathServerRooted)
  def servicePort =
    def fromUrlOrDefault : Int =
      val fromUrl = serverUrl.server.getPort
      if fromUrl >= 0 then fromUrl else 80
    proxiedPort.getOrElse( fromUrlOrDefault )

/**
  * Beware! SourceUrl transformers might see atom feeds rather than RSS, or who knows what!
  * Try to make them resilient to these.
  *
  * There is no need to supply a transformer just to convert atom to RSS. We handle that
  * automatically later in the pipeline. But if you want to do more than that, then you might
  * first normalize feeds to RSS (from atom) before doing whatever else it is you are doing.
  */
object SourceUrl:
  def apply( url : URL )                               : SourceUrl = SourceUrl(url, identity)
  def apply( url : String)                             : SourceUrl = SourceUrl(new URL(url))
  def apply( url : String, transformer : Elem => Elem) : SourceUrl = SourceUrl( new URL(url), transformer )
final case class SourceUrl( url : URL, transformer : Elem => Elem )

/**
  * Beware! MetaSource eachFeedTransformers might see atom feeds rather than RSS, or who knows what!
  * Try to make them resilient to these.
  *
  * There is no need to supply a transformer just to convert atom to RSS. We handle that
  * automatically later in the pipeline. But if you want to do more than that, then you might
  * first normalize feeds to RSS (from atom) before doing whatever else it is you are doing.
  *
  * MetaSource outputTransformers will reliably see RSS.
  */
object MetaSource:
  case class OPML( opmlUrl : URL, opmlTransformer : Elem => Elem = identity, eachFeedTransformer : Elem => Elem = identity, urlFilter : String => Boolean = _ => true ) extends MetaSource:
    def sourceUrls : immutable.Seq[SourceUrl] =
      val opmlElem =
        // XML.load(opmlUrl) // in practice, loading via requests-scala proves more reliable, especially for long documents
        requests.get.stream( opmlUrl.toString ).readBytesThrough( XML.load )
      ( opmlTransformer( opmlElem ) \\ "outline")
        .map( _ \@ "xmlUrl" )
        .filter( _.nonEmpty)
        .filter( urlFilter )
        .map( feedUrl => SourceUrl( feedUrl, eachFeedTransformer ) )
trait MetaSource:
  def sourceUrls : immutable.Seq[SourceUrl]
end MetaSource

trait MergedFeed:
  def sourceUrls                                     : immutable.Seq[SourceUrl]
  def metaSources                                    : immutable.Seq[MetaSource]
  def itemLimit                                      : Int
  def title( rootElems : immutable.Seq[Elem] )       : String
  def description( rootElems : immutable.Seq[Elem] ) : String
  def feedPath                                       : Rel
  def stubSitePath                                   : Rel
  def stubSite( rootElems : immutable.Seq[Elem] )    : String
  def stubSiteContentType                            : String
  def refreshSeconds                                 : Int
  def outputTransformer                              : Elem => Elem

object MergedFeed:
  class Default(
    val baseName : String,
    override val sourceUrls        : immutable.Seq[SourceUrl]  = Nil,
    override val metaSources       : immutable.Seq[MetaSource] = Nil,
    override val itemLimit         : Int                       = Int.MaxValue,
    override val refreshSeconds    : Int                       = 600,
    override val outputTransformer : Elem => Elem              = identity,
  ) extends MergedFeed:
    require( sourceUrls.nonEmpty || metaSources.nonEmpty, s"Bad MergedFeed '${baseName}' configured, no sources or metasources specified." )
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
