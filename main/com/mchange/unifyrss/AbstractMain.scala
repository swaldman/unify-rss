package com.mchange.unifyrss

import scala.collection.*

import zio.*
import java.net.URL
import audiofluidity.rss.Element
import scala.xml.{Elem,XML}
import unstatic.UrlPath.*

/* abstract class */
object AbstractMain extends ZIOAppDefault:

  /* abstract */
  val appConfig : AppConfig = AppConfig(
    appPath     = Abs("https://www.interfluidity.com/feeds"),
    mergedFeeds = immutable.Set (
      MergedFeed.Default(
        sourceUrls =immutable.Seq(
          new URL("https://drafts.interfluidity.com/feed/index.rss"),
          new URL("https://www.interfluidity.com/feed"),
        ),
        baseName = "feed-all-blogs",
        itemLimit = 8
      )
    )
  )

  def fetchFeed( url : URL ) : Task[Elem] = ZIO.attemptBlocking(XML.load(url))
  def fetchFeeds( urls : Iterable[URL] ) : Task[immutable.Seq[Elem]] = ZIO.collectAllPar( Chunk.fromIterable(urls.map( fetchFeed )) )

  def mergeFeeds( ac : AppConfig, mf : MergedFeed ) : Task[Element.Rss] =
    for
      feeds <- fetchFeeds(mf.sourceUrls)
    yield
      val spec = Element.Channel.Spec(mf.title(feeds), ac.appPath.resolve(mf.feedPath).toString, mf.description(feeds))
      RssMerger.merge( spec, mf.itemLimit, feeds* )

  override def run =
    mergeFeeds(appConfig, appConfig.mergedFeeds.head)
      .map( _.asXmlText )
      .debug
