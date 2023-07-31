package com.mchange.unifyrss

import scala.collection.*
import zio.*

import java.net.URL
import audiofluidity.rss.Element

import scala.xml.{Elem, XML}
import unstatic.UrlPath.*

private val ExponentialBackoffFactor = 1.5d

private val FirstErrorRetry  = 10.seconds
private val InitLongestRetry = 600.seconds

private val QuickRetryPeriod = 6.seconds
private val QuickRetryLimit  = 60.seconds

def retrySchedule( normalRefresh : Duration, firstErrorRetry : Duration = FirstErrorRetry ) =
  Schedule.exponential( firstErrorRetry, ExponentialBackoffFactor ) || Schedule.fixed( normalRefresh )

val quickRetrySchedule =
  Schedule.spaced(QuickRetryPeriod).upTo(QuickRetryLimit)

private def errorEmptyFeed(mf : MergedFeed) : Elem =
  val badFeeds = mf.sourceUrls.map( u => s"'${u.toString}'").mkString(", ")
  val channel = Element.Channel.create(
    title = s"ERROR — Failed to merge feeds at ${badFeeds}",
    linkUrl = "about:blank",
    description = s"Attempt to merge feeds (${badFeeds}) failed to load even a single feed. This empty feed is a placeholder",
    items = List.empty
  )
  Element.Rss(channel).toElem

def fetchElem(url : URL) : Task[Elem] = ZIO.attemptBlocking(XML.load(url))

def bestAttemptFetchElem(url : URL) : Task[Option[Elem]] =
  fetchElem(url)
    .logError
    .retry( quickRetrySchedule )
    .foldCauseZIO(cause => ZIO.logCause(s"Problem loading feed '${url}'", cause) *> ZIO.succeed(None), elem => ZIO.succeed(Some(elem)))

def bestAttemptFetchElems(mf : MergedFeed) : Task[immutable.Seq[Elem]] =
  val maybeElems = ZIO.collectAllPar(Chunk.fromIterable(mf.sourceUrls.map(bestAttemptFetchElem)))
  val elemsFetched = maybeElems.map( _.collect { case Some(elem) => elem } )
  elemsFetched.rejectZIO:
    case chunk if chunk.isEmpty =>
      ZIO.fail(XmlFetchFailure(s"Could load no feeds for merged feed at '${mf.feedPath}'"))

def elemToRssElem( elem : Elem ) : Option[Elem] =
  if elem.prefix == null then // we expect no prefix on top-level elements
    elem.label match
      case "rss" => Some(elem)
      case "feed" if elem \@ "xmlns" == "http://www.w3.org/2005/Atom" => Some(rssElemFromAtomFeedElem(elem))
      case _     => None
  else
    None

def attemptElemToRssElem( elem : Elem ) : Task[Option[Elem]] =
  ZIO.attempt( elemToRssElem(elem) ).logError.catchAll( _ => ZIO.succeed(None : Option[Elem]) )

def bestAttemptElemsToRssElems( elems : immutable.Seq[Elem] ) : Task[immutable.Seq[Option[Elem]]] =
  ZIO.mergeAll( elems.map( attemptElemToRssElem ) )( immutable.Seq.empty[Option[Elem]])( _ :+ _)

def bestAttemptFetchFeeds(mf : MergedFeed) : Task[immutable.Seq[Elem]] =
  for
    elems <- bestAttemptFetchElems(mf)
    converted <- bestAttemptElemsToRssElems( elems )
  yield
    converted.collect{ case Some(elem) => elem }

def bestAttemptFetchFeedsOrEmptyFeed(mf : MergedFeed) : Task[immutable.Seq[Elem]] =
  bestAttemptFetchFeeds(mf).logError.catchSome { case _ : XmlFetchFailure => ZIO.succeed( List(errorEmptyFeed(mf)) ) }

def mergeFeeds(ac : AppConfig, mf : MergedFeed, feeds : immutable.Seq[Elem]) : Task[immutable.Seq[Byte]] = ZIO.attempt:
  val spec = Element.Channel.Spec(mf.title(feeds), ac.appPathAbs.resolve(mf.stubSitePath).toString, mf.description(feeds))
  RssMerger.merge(spec, mf.itemLimit, feeds*).bytes

def stubSite(ac : AppConfig, mf : MergedFeed, feeds : immutable.Seq[Elem]) : Task[String] = ZIO.attempt(mf.stubSite(feeds))

def initMergedFeedRefs( ac : AppConfig ) : Task[FeedRefMap] =
  def refTup( mf : MergedFeed ) =
    for
      elems <- bestAttemptFetchFeedsOrEmptyFeed(mf)
      feed  <- mergeFeeds( ac, mf, elems )
      ref   <- Ref.make(feed).logError  // let's be sure to see if anything goes wrong
    yield (mf.feedPath, ref)
  val tupEffects = ac.mergedFeeds.map( refTup )
  ZIO.mergeAll(tupEffects)( immutable.Map.empty[Rel,Ref[immutable.Seq[Byte]]] )( (accum, next) => accum + next )

def updateMergedFeedRef( ac : AppConfig, mf : MergedFeed, mergedFeedRefs : FeedRefMap ) : Task[Unit] =
  for
    elems <- bestAttemptFetchFeedsOrEmptyFeed(mf)
    feed  <- mergeFeeds(ac, mf, elems)
    _     <- mergedFeedRefs(mf.feedPath).set(feed)
  yield ()

def periodicallyResilientlyUpdateMergedFeedRef( ac : AppConfig, mf : MergedFeed, mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) : Task[Long] =
  val refreshDuration = Duration.fromSeconds(mf.refreshSeconds)
  val resilient = updateMergedFeedRef( ac, mf, mergedFeedRefs ).logError.retry( retrySchedule( refreshDuration ) ) // let's be sure to see if anything goes wrong
  resilient.schedule( Schedule.fixed( refreshDuration ) )

def periodicallyResilientlyUpdateAllMergedFeedRefs( ac : AppConfig, mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) =
  val allForks = ac.mergedFeeds.map( mf => periodicallyResilientlyUpdateMergedFeedRef( ac, mf, mergedFeedRefs ).forkDaemon )
  ZIO.collectAllDiscard( allForks )

