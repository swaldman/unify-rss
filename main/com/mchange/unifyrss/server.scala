package com.mchange.unifyrss

import zio.*
import zio.http.Server
import java.net.URL
import audiofluidity.rss.Element
import scala.xml.{Elem,XML}
import unstatic.UrlPath.*
import sttp.tapir.ztapir.*
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter,ZioHttpServerOptions}

val VerboseServerInterpreterOptions: ZioHttpServerOptions[Any] =
// modified from https://github.com/longliveenduro/zio-geolocation-tapir-tapir-starter/blob/b79c88b9b1c44a60d7c547d04ca22f12f420d21d/src/main/scala/com/tsystems/toil/Main.scala
  ZioHttpServerOptions
    .customiseInterceptors
    .serverLog(
      DefaultServerLog[Task](
        doLogWhenReceived = msg => ZIO.succeed(println(msg)),
        doLogWhenHandled = (msg, error) => ZIO.succeed(error.fold(println(msg))(err => println(s"msg: ${msg}, err: ${err}"))),
        doLogAllDecodeFailures = (msg, error) => ZIO.succeed(error.fold(println(msg))(err => println(s"msg: ${msg}, err: ${err}"))),
        doLogExceptions = (msg: String, exc: Throwable) => ZIO.succeed(println(s"msg: ${msg}, exc: ${exc}")),
        noLog = ZIO.unit
      )
    )
    .options

val DefaltServerInterpreterOptions: ZioHttpServerOptions[Any] = ZioHttpServerOptions.default.widen[Any]

def interpreterOptions(verbose: Boolean) = if verbose then VerboseServerInterpreterOptions else DefaltServerInterpreterOptions

def feedServerLogic(feedPath: Rel, mergedFeedRefs: FeedRefMap): Unit => UIO[Array[Byte]] =
  (_: Unit) => mergedFeedRefs(feedPath).get.map(_.toArray)

def feedZServerEndpoint(feedPath: Rel, fem: FeedEndpointMap, mergedFeedRefs: FeedRefMap) =
  val endpoint = fem(feedPath)
  val logic = feedServerLogic(feedPath, mergedFeedRefs)
  endpoint.zServerLogic[Any](logic)

def allServerEndpoints(ac: AppConfig, fem: FeedEndpointMap, mergedFeedRefs: FeedRefMap) =
  ac.mergedFeeds.map(mf => feedZServerEndpoint(mf.feedPath, fem, mergedFeedRefs)).toList

def toHttpApp(ac: AppConfig, zServerEndpoints: List[ZServerEndpoint[Any, Any]]) =
  ZioHttpInterpreter(interpreterOptions(ac.verbose)).toHttp(zServerEndpoints)

def server(ac: AppConfig, mergedFeedRefs: FeedRefMap) : UIO[ExitCode] =
  val fem = feedEndpoints( ac )
  val zServerEndpoints = allServerEndpoints(ac, fem, mergedFeedRefs)
  val httpApp = toHttpApp(ac, zServerEndpoints)
  Server
    .serve(httpApp.withDefaultErrorResponse)
    .provide(ZLayer.succeed(Server.Config.default.port(ac.servicePort)), Server.live)
    .exitCode



