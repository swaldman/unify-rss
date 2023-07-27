package com.mchange.unifyrss

import scala.collection.*

import zio.*

abstract class AbstractMain extends ZIOAppDefault:

  def appConfig : AppConfig
  
  override def run =
    val fem = feedEndpoints(appConfig)
    for
      mergedFeedRefs   <- initMergedFeedRefs( appConfig )
      _                <- periodicallyResilientlyUpdateAllMergedFeedRefs( appConfig, mergedFeedRefs )
      _                <- ZIO.logInfo(s"Starting up unify-rss server on port ${appConfig.servicePort}")
      exitCode         <- server( appConfig, fem, mergedFeedRefs )
    yield exitCode
