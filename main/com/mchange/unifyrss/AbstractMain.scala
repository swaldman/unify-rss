package com.mchange.unifyrss

import scala.collection.*

import zio.*
import java.net.URL
import audiofluidity.rss.Element
import scala.xml.{Elem,XML}
import unstatic.UrlPath.*
import sttp.tapir.ztapir.*

/* abstract class */
object AbstractMain extends ZIOAppDefault:

  /* abstract */
  val appConfig : AppConfig = AppConfig(
    serverUrl           = Abs("https://www.interfluidity.com/"),
    appPathServerRooted = Rooted("/rss"),
    mergedFeeds         = immutable.Set (
      MergedFeed.Default(
        sourceUrls =immutable.Seq(
          new URL("https://drafts.interfluidity.com/feed/index.rss"),
          new URL("https://tech.interfluidity.com/feed/index.rss"),
          new URL("https://www.interfluidity.com/feed"),
        ),
        baseName = "all-blogs",
        itemLimit = 8
      )
    )
  )

  def feedServerLogic( feedPath : Rel, mergedFeedRefs : FeedRefMap ) : Unit => Task[Array[Byte]] =
    (_ : Unit) => mergedFeedRefs(feedPath).get.map( _.toArray )

  /*
  def feedZServerEndpoint( feedPath : Rel, fem : FeedEndpointMap, mergedFeedRefs : FeedRefMap ) =
    val endpoint = fem(feedPath)
    val logic    = feedServerLogic(feedPath, mergedFeedRefs)
    endpoint.zServerLogic[Any](logic)
  */

  // def allServerLogics( ac : AppConfig, fem : Map[Rel,Endpoint[Unit,Unit,Unit,Array[Byte],Any]], mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) =


  override def run =
    val fem = feedEndpoints(appConfig)
    for
      mergedFeedRefs <- initMergedFeedRefs( appConfig )
      _              <- periodicallyResilientlyUpdateAllMergedFeedRefs( appConfig, mergedFeedRefs )
    yield ()

/*
  override def run =
    mergeFeeds(appConfig, appConfig.mergedFeeds.head)
      .map( _.asXmlText )
      .debug
*/