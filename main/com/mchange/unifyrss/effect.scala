package com.mchange.unifyrss

import scala.collection.*
import zio.*

import java.net.URL
import audiofluidity.rss.Element

import scala.xml.{Elem, NamespaceBinding, PrettyPrinter, TopScope, XML}
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

private def errorEmptyRssElement(mf : MergedFeed, location : String, description : String) : Element.Rss =
  val badFeeds = if mf.sourceUrls.isEmpty then "none" else mf.sourceUrls.map( u => s"'${u.toString}'").mkString(", ")
  val badMetaSources = if mf.metaSources.isEmpty then "none" else mf.metaSources.map( ms => s"'${ms.toString}'").mkString(", ")
  val channel = Element.Channel.create(
    title = s"ERROR — Failed to merge feeds, problem at location '${location}'. Feeds: [${badFeeds}], MetaSources: [${badMetaSources}]",
    linkUrl = "about:blank",
    description = description,
    items = List.empty
  )
  Element.Rss(channel)

def fetchElem( url : URL ) : Task[Elem] =
  ZIO.attemptBlocking(XML.load(url))
    .mapError( e => new XmlFetchFailure( s"Problem loading: ${url}", e ) )

def fetchElem( sourceUrl : SourceUrl ) : Task[Elem] = fetchElem( sourceUrl.url ).map( sourceUrl.transformer )

def bestAttemptFetchElem(sourceUrl : SourceUrl) : Task[Option[Elem]] =
  fetchElem(sourceUrl)
    .logError
    .retry( quickRetrySchedule )
    .foldCauseZIO(cause => ZIO.logCause(s"Problem loading feed '${sourceUrl.url}'", cause) *> ZIO.succeed(None), elem => ZIO.succeed(Some(elem)))

def bestAttemptFetchSourceUrls( ms : MetaSource ) : Task[immutable.Seq[SourceUrl]] =
  ZIO.attempt( ms.sourceUrls )
     .logError
     .retry( quickRetrySchedule )
     .foldCauseZIO(cause => ZIO.logCause(s"Problem loading MetaSource '${ms}'", cause) *> ZIO.succeed(immutable.Seq.empty), sourceUrls => ZIO.succeed(sourceUrls))

def bestAttemptFetchElems(mf : MergedFeed) : Task[immutable.Seq[Elem]] =
  val raw = 
    for
      fromMetaSources <- ZIO.collectAllPar(mf.metaSources.map( bestAttemptFetchSourceUrls )).map( _.flatten )
      allSourceUrls   =  mf.sourceUrls ++ fromMetaSources
      maybeElems      <- ZIO.collectAllPar(allSourceUrls.map( bestAttemptFetchElem) )
    yield
      maybeElems
        .collect { case Some(elem) => elem }
  raw.rejectZIO:
    case fetched if fetched.isEmpty =>
      ZIO.fail(XmlFetchFailure(s"Could load no feeds for merged feed at '${mf.feedPath}'"))

def elemToRssElem( elem : Elem ) : Elem =
  if elem.prefix == null then // we expect no prefix on top-level elements
    elem.label match
      case "rss" => elem
      case "feed" if scopeContains( null, "http://www.w3.org/2005/Atom", elem.scope ) => rssElemFromAtomFeedElem(elem)
      case _ => throw CantConvertToRss(s"Unknown feed type with label '${elem.label}' and scope '${elem.scope}'")
  else
    throw new CantConvertToRss (
      s"No prefixed elements are currently convertible to RSS, elem.prefix: ${elem.prefix}, elem.label: ${elem.label}, elem.scope: ${elem.scope}"
    )

def attemptElemToRssElem( elem : Elem ) : Task[Option[Elem]] =
  ZIO.attempt( Some(elemToRssElem(elem)) ).logError.catchAll( _ => ZIO.succeed(None : Option[Elem]) )

def bestAttemptElemsToRssElems( elems : immutable.Seq[Elem] ) : Task[immutable.Seq[Option[Elem]]] =
  ZIO.mergeAll( elems.map( attemptElemToRssElem ) )( immutable.Seq.empty[Option[Elem]])( _ :+ _)

def bestAttemptFetchFeeds(mf : MergedFeed) : Task[immutable.Seq[Elem]] =
  for
    elems <- bestAttemptFetchElems(mf)
    converted <- bestAttemptElemsToRssElems( elems )
  yield
    converted.collect{ case Some(elem) => elem }

def bestAttemptFetchFeedsOrEmptyFeed(mf : MergedFeed) : UIO[immutable.Seq[Elem]] =
  bestAttemptFetchFeeds(mf).logError.catchAll( t => ZIO.succeed(List(errorEmptyRssElement(mf, "bestAttemptFetchFeedsOrEmptyFeed", t.toString).toElem) ) )

def elemToBytes( elem : Elem ) : immutable.Seq[Byte] =
  val pp = new PrettyPrinter(width=120, step=2, minimizeEmpty=true)
  val text = s"<?xml version='1.0' encoding='UTF-8'?>\n${pp.format(elem)}"
  immutable.ArraySeq.ofByte(text.getBytes(scala.io.Codec.UTF8.charSet))

def mergeFeeds(ac : AppConfig, mf : MergedFeed, feeds : immutable.Seq[Elem]) : Task[immutable.Seq[Byte]] = ZIO.attempt:
  val spec = Element.Channel.Spec(mf.title(feeds), ac.appPathAbs.resolve(mf.stubSitePath).toString, mf.description(feeds))
  val rssElement = RssMerger.merge(spec, mf.itemLimit, feeds*)
  val xformed = mf.outputTransformer( rssElement.toElem )
  elemToBytes( xformed )

def bestAttemptMergeFeeds(ac : AppConfig, mf : MergedFeed, feeds : immutable.Seq[Elem]) : UIO[immutable.Seq[Byte]] =
  mergeFeeds(ac, mf, feeds)
    .logError
    .catchAll( t => ZIO.succeed(errorEmptyRssElement(mf, "bestAttemptFetchFeedsOrEmptyFeed", t.toString).bytes) )

def stubSite(ac : AppConfig, mf : MergedFeed, feeds : immutable.Seq[Elem]) : Task[String] = ZIO.attempt(mf.stubSite(feeds))

def initMergedFeedRefs( ac : AppConfig ) : Task[FeedRefMap] =
  def refTup( mf : MergedFeed ) =
    for
      elems <- bestAttemptFetchFeedsOrEmptyFeed(mf)
      feed  <- bestAttemptMergeFeeds( ac, mf, elems )
      ref   <- Ref.make(feed).logError  // let's be sure to see if anything goes wrong
    yield (mf.feedPath, ref)
  val tupEffects = ac.mergedFeeds.map( refTup )
  ZIO.mergeAll(tupEffects)( immutable.Map.empty[Rel,Ref[immutable.Seq[Byte]]] )( (accum, next) => accum + next )

def updateMergedFeedRef( ac : AppConfig, mf : MergedFeed, mergedFeedRefs : FeedRefMap ) : Task[Unit] =
  for
    elems <- bestAttemptFetchFeedsOrEmptyFeed(mf)
    feed  <- bestAttemptMergeFeeds(ac, mf, elems)
    _     <- mergedFeedRefs(mf.feedPath).set(feed)
  yield ()

def periodicallyResilientlyUpdateMergedFeedRef( ac : AppConfig, mf : MergedFeed, mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) : Task[Long] =
  val refreshDuration = Duration.fromSeconds(mf.refreshSeconds)
  val resilient = updateMergedFeedRef( ac, mf, mergedFeedRefs ).logError.retry( retrySchedule( refreshDuration ) ) // let's be sure to see if anything goes wrong
  resilient.schedule( Schedule.fixed( refreshDuration ) )

def periodicallyResilientlyUpdateAllMergedFeedRefs( ac : AppConfig, mergedFeedRefs : immutable.Map[Rel,Ref[immutable.Seq[Byte]]] ) =
  val allForks = ac.mergedFeeds.map( mf => periodicallyResilientlyUpdateMergedFeedRef( ac, mf, mergedFeedRefs ).forkDaemon )
  ZIO.collectAllDiscard( allForks )
