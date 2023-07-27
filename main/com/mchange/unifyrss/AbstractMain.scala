package com.mchange.unifyrss

import scala.collection.*

import zio.*
import zio.http.Server
import java.net.URL
import audiofluidity.rss.Element
import scala.xml.{Elem,XML}
import unstatic.UrlPath.*
import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter

abstract class AbstractMain extends ZIOAppDefault:

  def appConfig : AppConfig

  def feedServerLogic( feedPath : Rel, mergedFeedRefs : FeedRefMap ) : Unit => UIO[Array[Byte]] =
    (_ : Unit) => mergedFeedRefs(feedPath).get.map( _.toArray )

  def feedZServerEndpoint( feedPath : Rel, fem : FeedEndpointMap, mergedFeedRefs : FeedRefMap ) =
    val endpoint = fem(feedPath)
    val logic    = feedServerLogic(feedPath, mergedFeedRefs)
    endpoint.zServerLogic[Any](logic)

  def allServerEndpoints( ac : AppConfig, fem : FeedEndpointMap, mergedFeedRefs : FeedRefMap ) =
    ac.mergedFeeds.map( mf => feedZServerEndpoint( mf.feedPath, fem, mergedFeedRefs ) ).toList

  def toHttpApp( zServerEndpoints : List[ZServerEndpoint[Any,Any]] ) =
    ZioHttpInterpreter().toHttp( zServerEndpoints )

  def server( ac : AppConfig, fem : FeedEndpointMap, mergedFeedRefs : FeedRefMap ) =
    val zServerEndpoints = allServerEndpoints(ac, fem, mergedFeedRefs)
    val httpApp = toHttpApp(zServerEndpoints)
    Server
      .serve(httpApp.withDefaultErrorResponse)
      .provide(ZLayer.succeed(Server.Config.default.port(ac.servicePort)), Server.live)
      .exitCode

  override def run =
    val fem = feedEndpoints(appConfig)
    for
      mergedFeedRefs   <- initMergedFeedRefs( appConfig )
      _                <- periodicallyResilientlyUpdateAllMergedFeedRefs( appConfig, mergedFeedRefs )
      exitCode         <- server( appConfig, fem, mergedFeedRefs )
    yield exitCode
