package com.mchange.unifyrss

import scala.collection.*
import zio.*
import java.net.URL
import audiofluidity.rss.Element
import scala.xml.{Elem,XML}
import unstatic.UrlPath.*

def fetchFeed(url : URL)             : Task[Elem]                = ZIO.attemptBlocking(XML.load(url))
def fetchFeeds(urls : Iterable[URL]) : Task[immutable.Seq[Elem]] = ZIO.collectAllPar(Chunk.fromIterable(urls.map(fetchFeed)))

def mergeFeeds(ac : AppConfig, mf : MergedFeed, feeds : immutable.Seq[Elem]) : Task[immutable.Seq[Byte]] = ZIO.attempt:
  val spec = Element.Channel.Spec(mf.title(feeds), ac.appPathAbs.resolve(mf.stubSitePath).toString, mf.description(feeds))
  RssMerger.merge(spec, mf.itemLimit, feeds*).bytes

def stubSite(ac : AppConfig, mf : MergedFeed, feeds : immutable.Seq[Elem]) : Task[String] = ZIO.attempt(mf.stubSite(feeds))

def initMergedFeedRefs( ac : AppConfig ) : Task[immutable.Map[Rel,Ref[immutable.Seq[Byte]]]] =
  def refTup( mf : MergedFeed ) =
    for
      elems <- fetchFeeds(mf.sourceUrls)
      feed  <- mergeFeeds( ac, mf, elems )
      ref   <- Ref.make(feed)
    yield (mf.feedPath, ref)
  val tupEffects = ac.mergedFeeds.map( refTup )
  ZIO.mergeAll(tupEffects)( immutable.Map.empty[Rel,Ref[immutable.Seq[Byte]]] )( (accum, next) => accum + next )

def updateMergedFeedRef( ac : AppConfig, mf : MergedFeed, mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) : Task[Unit] =
  for
    elems <- fetchFeeds(mf.sourceUrls)
    feed  <- mergeFeeds(ac, mf, elems)
    _     <- mergedFeedRefs(mf.feedPath).set(feed)
  yield ()

def periodicallyUpdateMergedFeedRef( ac : AppConfig, mf : MergedFeed, mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) : Task[Long] =
  updateMergedFeedRef( ac, mf, mergedFeedRefs ).schedule( Schedule.fixed( Duration.fromSeconds(mf.refreshSeconds) ) )

def periodicallyUpdateAllMergedFeedRefs( ac : AppConfig, mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) =
  val allForks = ac.mergedFeeds.map( mf => periodicallyUpdateMergedFeedRef( ac, mf, mergedFeedRefs ).forkDaemon )
  ZIO.collectAllDiscard( allForks )

