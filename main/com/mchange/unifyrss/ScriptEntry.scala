package com.mchange.unifyrss

import zio.*

import java.lang.System

object ScriptEntry:
  def performStaticGen( sgc : StaticGenConfig ) : Unit =
    val effect =
      for
        _ <- staticGenMergedFeeds( sgc )
        _ <- ZIO.attempt( System.err.println("All configured feeds (re)generated.") )
      yield ()
    Unsafe.unsafely:
      Runtime.default.unsafe.run(effect).getOrThrow()

  def startupDaemon( daemonConfig : DaemonConfig ) : Unit =
    val effect =
      for
        mergedFeedRefs   <- initMergedFeedRefs( daemonConfig )
        _                <- periodicallyResilientlyUpdateAllMergedFeedRefs( daemonConfig, mergedFeedRefs )
        _                <- ZIO.logInfo(s"Starting up unify-rss server on port ${daemonConfig.servicePort}")
        _                <- server( daemonConfig, mergedFeedRefs )
      yield ()
    Unsafe.unsafely:
      Runtime.default.unsafe.run(effect).getOrThrow()
