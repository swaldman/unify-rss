//> using scala "3.3.0"
//> using dep "org.scala-lang.modules::scala-xml:2.2.0"
//> using dep "dev.zio::zio:2.0.15"
//> using options "-deprecation"

import scala.collection.*
import scala.xml.{Elem, XML}
import java.net.URL
import zio.*

// this is much simpler than the real AppConfig!
case class AppConfig( sourceUrls : immutable.Seq[URL], refreshSeconds : Int )

/*
def fetchFeed(url : URL) : Task[Elem] =
  ZIO.attemptBlocking(XML.load(url))

def fetchFeeds(urls : Iterable[URL]) : Task[immutable.Seq[Elem]] =
  ZIO.collectAllPar(Chunk.fromIterable(urls.map(fetchFeed)))
*/

/*
def fetchFeed(url : URL) : Task[Option[Elem]] =
  ZIO.attemptBlocking(XML.load(url))
    .foldCauseZIO(
      cause => ZIO.logCause(s"Problem loading feed '${url}'", cause) *> ZIO.succeed(None),
      elem => ZIO.succeed(Some(elem))
    )
*/

def fetchFeed(url : URL) : Task[Option[Elem]] =
  ZIO.attemptBlocking(XML.load(url))
    .retry( Schedule.spaced( 6.seconds ).upTo( 60.seconds ) )
    .foldCauseZIO(
      cause => ZIO.logCause(s"Problem loading feed '${url}'", cause) *> ZIO.succeed(None),
      elem => ZIO.succeed(Some(elem))
    )

def fetchFeeds(urls : Iterable[URL]) : Task[immutable.Seq[Elem]] =
  ZIO.collectAllPar(Chunk.fromIterable(urls.map(fetchFeed)))
    .map( _.collect { case Some(elem) => elem } )


def mergeFeeds( config : AppConfig, elems : immutable.Seq[Elem] ) : Task[immutable.Seq[Byte]] = ???

def initMergeFeed( config : AppConfig ) : Task[Ref[immutable.Seq[Byte]]]  =
  for
    elems <- fetchFeeds( config.sourceUrls ) // the config is actually a bit more complicated than this
    feed  <- mergeFeeds( config, elems )
    ref   <- Ref.make(feed)
  yield ref

def updateMergedFeed( config : AppConfig, ref : Ref[immutable.Seq[Byte]] ) : Task[Unit] =
  for
    elems <- fetchFeeds( config.sourceUrls ) // the config is actually a bit more complicated than this
    feed  <- mergeFeeds( config, elems )
    _     <- ref.set(feed)
  yield ()

//def periodicallyUpdateMergedFeed( config : AppConfig, ref : Ref[immutable.Seq[Byte]] ) : Task[Long] =
//  updateMergedFeed( config, ref ).schedule( Schedule.fixed( Duration.fromSeconds(config.refreshSeconds) ) )

def retrySchedule( config : AppConfig ) =
  Schedule.exponential( 10.seconds, 1.5d ) || Schedule.fixed( Duration.fromSeconds( config.refreshSeconds ) ) 

def periodicallyUpdateMergedFeed( config : AppConfig, ref : Ref[immutable.Seq[Byte]] ) : Task[Long] =
  val resilient = updateMergedFeed( config, ref ).schedule( retrySchedule( config ) )
  resilient.schedule( Schedule.fixed( Duration.fromSeconds(config.refreshSeconds) ) )

// use tapir http-zio to create an effect starting a web endpoint that serves RSS from the ref
def server(ac : AppConfig, ref : Ref[immutable.Seq[Byte]] ) : UIO[ExitCode] = ???

object Main extends ZIOAppDefault:
  val config : AppConfig = ???

  override def run =
    for
      ref      <- initMergeFeed( config )
      _        <- periodicallyUpdateMergedFeed( config, ref ).forkDaemon
      exitCode <- server( config, ref )
    yield exitCode
