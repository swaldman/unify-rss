package com.mchange.unifyrss

import scala.collection.*

import zio.*
import sttp.client3.*
import sttp.model.Uri
import sttp.client3.httpclient.zio.{SttpClient,HttpClientZioBackend}
import audiofluidity.rss.Element
import scala.xml.{Elem,XML}

/* abstract class */ object AbstractMain extends ZIOAppDefault:
  def fetchFeed( uri : Uri ) : ZIO[SttpClient,Throwable,String] =
    for
      sttpClient <- ZIO.service[SttpClient]
      response <- basicRequest.get(uri).send(sttpClient)
    yield
      response.body match
        case Right(feedText) => feedText
        case _ => throw new RssFetchFailure( response.show( true, true, immutable.Set.empty ) )

  def fetchFeeds( uris : Iterable[Uri] ) : ZIO[SttpClient,Throwable,immutable.Set[String]] =
    ZIO.collectAllPar( uris.map( fetchFeed ).toSet )

  def mergeFeeds( spec : Element.Channel.Spec, uris : Iterable[Uri] ) : ZIO[SttpClient,Throwable,Element.Rss] =
    for
      feeds <- fetchFeeds(uris)
    yield
      // it took some trial an error to avoid MalformedURLExceptions ("no protocol") when parsing the prefetched XML
      // this version works. i don't understand why some other approaches fail
      val elems = feeds.map(XML.loadStringDocument).map(_.docElem.asInstanceOf[Elem]).toSeq
      RssMerger.merge( spec, elems* )

  override def run =
    val spec = Element.Channel.Spec("Test Feed","https://www.dev.null/","Description")
    mergeFeeds(spec, uri"https://drafts.interfluidity.com/feed/index.rss"::uri"https://www.interfluidity.com/feed"::Nil)
      .map( _.asXmlText )
      .debug
      .provide(HttpClientZioBackend.layer())

