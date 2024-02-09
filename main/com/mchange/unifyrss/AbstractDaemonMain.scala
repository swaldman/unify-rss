package com.mchange.unifyrss

import scala.collection.*

import zio.*

abstract class AbstractDaemonMain extends ZIOAppDefault:

  def daemonConfig : DaemonConfig

  override def run =
    for
      mergedFeedRefs   <- initMergedFeedRefs( daemonConfig )
      _                <- periodicallyResilientlyUpdateAllMergedFeedRefs( daemonConfig, mergedFeedRefs )
      _                <- ZIO.logInfo(s"Starting up unify-rss server on port ${daemonConfig.servicePort}")
      exitCode         <- server( daemonConfig, mergedFeedRefs )
    yield exitCode
