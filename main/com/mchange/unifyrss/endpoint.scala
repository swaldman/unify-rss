package com.mchange.unifyrss

import scala.collection.*
import unstatic.UrlPath.*
import sttp.tapir.ztapir.*
import sttp.tapir.Endpoint
import sttp.model.{Header, MediaType}

// stolen from unstatic
val MediaTypeRss = MediaType("application","rss+xml",None,immutable.Map.empty[String,String])
val CharsetUTF8 = scala.io.Codec.UTF8.charSet

// stolen from unstatic
private def endpointForFixedPath( serverRootedPath : Rooted ) : Endpoint[Unit, Unit, Unit, Unit, Any] =
  if (serverRootedPath == Rooted.root) then
    endpoint.get.in("")
  else
    serverRootedPath.elements.foldLeft(endpoint.get)( (accum, next) => accum.in( next ) )

def feedEndpoint( ac : AppConfig, mf : MergedFeed ) : Endpoint[Unit,Unit,String,Array[Byte],Any] =
  endpointForFixedPath( ac.appPathServerRooted.resolve(mf.feedPath) )
    .out(header(Header.contentType(MediaTypeRss)))
    .out(byteArrayBody)
    .errorOut(stringBody(CharsetUTF8))
    .out(header(Header.contentType(MediaType.TextPlain.charset(CharsetUTF8))))

def feedEndpoints( ac : AppConfig ) : immutable.Map[Rel,Endpoint[Unit,Unit,String,Array[Byte],Any]] =
  ac.mergedFeeds.map( mf => (mf.feedPath, feedEndpoint(ac,mf)) ).toMap
