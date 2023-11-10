package com.mchange.unifyrss

import scala.collection.*

import zio.*

abstract class AbstractDaemonMain extends ZIOAppDefault:

  def appConfig : AppConfig

  override def run =
    for
      mergedFeedRefs   <- initMergedFeedRefs( appConfig )
      _                <- periodicallyResilientlyUpdateAllMergedFeedRefs( appConfig, mergedFeedRefs )
      _                <- ZIO.logInfo(s"Starting up unify-rss server on port ${appConfig.servicePort}")
      exitCode         <- server( appConfig, mergedFeedRefs )
    yield exitCode
