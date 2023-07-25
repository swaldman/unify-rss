package com.mchange.unifyrss

import scala.collection.*

import zio.*
import java.net.URL
import audiofluidity.rss.Element
import scala.xml.{Elem,XML}

/* abstract class */ object AbstractMain extends ZIOAppDefault:
  def fetchFeed( url : URL ) : Task[Elem] = ZIO.attemptBlocking(XML.load(url))
  def fetchFeeds( urls : Iterable[URL] ) : Task[immutable.Set[Elem]] = ZIO.collectAllPar( urls.map( fetchFeed ).toSet )

  def mergeFeeds( spec : Element.Channel.Spec, uris : Iterable[URL] ) : Task[Element.Rss] =
    for
      feeds <- fetchFeeds(uris)
    yield
      RssMerger.merge( spec, feeds.toSeq* )

  override def run =
    val spec = Element.Channel.Spec("Test Feed","https://www.dev.null/","Description")
    mergeFeeds(spec, new URL("https://drafts.interfluidity.com/feed/index.rss")::new URL("https://www.interfluidity.com/feed")::Nil)
      .map( _.asXmlText )
      .debug
