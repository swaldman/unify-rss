package com.mchange.unifyrss

import scala.collection.*

import zio.*
import java.nio.file.Path

import java.lang.System

abstract class AbstractStaticGenMain extends ZIOAppDefault:

  def daemonConfig : DaemonConfig
  def appStaticDir : Path

  override def run =
    val effect = 
      for
        _ <- staticGenMergedFeeds( daemonConfig, appStaticDir )
        _ <- ZIO.attempt( System.err.println("All configured feeds (re)generated.") )
      yield ()
    effect.exitCode
