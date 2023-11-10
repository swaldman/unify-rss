package com.mchange.unifyrss

import scala.collection.*

import zio.*
import java.nio.file.Path

import java.lang.System

abstract class AbstractStaticGenMain extends ZIOAppDefault:

  def appConfig : AppConfig
  def appStaticDir : Path

  override def run =
    val effect = 
      for
        _ <- staticGenMergedFeeds( appConfig, appStaticDir )
        _ <- ZIO.attempt( System.err.println("All configured feeds (re)generated.") )
      yield ()
    effect.exitCode
