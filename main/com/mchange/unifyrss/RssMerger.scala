package com.mchange.unifyrss

import java.time.Instant

import scala.xml.*
import scala.xml.transform.*
import scala.collection.*
import scala.util.{Try,Success,Failure}

import scala.annotation.tailrec

import audiofluidity.rss.*

object RssMerger:
  val RssDateTimeFormatter = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

  // see https://stackoverflow.com/questions/45829799/java-time-format-datetimeformatter-rfc-1123-date-time-fails-to-parse-time-zone-n
  val LenientRssDateTimeFormatter =
    import java.time.format.*
    import java.time.temporal.ChronoField.*
    import java.time.chrono.IsoChronology
    import scala.jdk.CollectionConverters._
    val dow = Map(1L->"Mon",2L->"Tue",3L->"Wed",4L->"Thu",5L->"Fri",6L->"Sat",7L->"Sun").map( (k,v) => (k.asInstanceOf[java.lang.Long],v) )
    val moy = Map(1L->"Jan",2L->"Feb",3L->"Mar",4L->"Apr",5L->"May",6L->"Jun",7L->"Jul",8L->"Aug",9L->"Sep",10L->"Oct",11L->"Nov",12L->"Dec").map( (k,v) => (k.asInstanceOf[java.lang.Long],v) )
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .parseLenient()
      .optionalStart()
      .appendText(DAY_OF_WEEK, dow.asJava)
      .appendLiteral(", ")
      .optionalEnd()
      .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
      .appendLiteral(' ')
      .appendText(MONTH_OF_YEAR, moy.asJava)
      .appendLiteral(' ')
      .appendValue(YEAR, 4)  // 2 digit year not handled
      .appendLiteral(' ')
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .optionalStart()
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)
      .optionalEnd()
      .appendLiteral(' ')
      // difference from RFC_1123_DATE_TIME: optional offset OR zone ID
      .optionalStart()
      .appendZoneText(TextStyle.SHORT)
      .optionalEnd()
      .optionalStart()
      .appendOffset("+HHMM", "GMT")
      // use the same resolver style and chronology
      .toFormatter().withResolverStyle(ResolverStyle.SMART).withChronology(IsoChronology.INSTANCE);

  def stableEarlyInstant( s : String ) = Instant.ofEpochMilli(s.hashCode * 1_000_000)

  @tailrec
  private def namespaces( accum : List[(String,String)], binding : NamespaceBinding ) : List[(String,String)] =
    if binding == TopScope || binding == null then accum else namespaces( (binding.prefix, binding.uri ) :: accum, binding.parent)

  private def namespaces( accum : List[(String,String)], bindingSeq : Seq[NamespaceBinding] ) : List[(String,String)] =
    bindingSeq.foldLeft( accum )( (soFar, next) => namespaces( soFar, next ) )

  private def elemNamespaces( accum : List[(String,String)], elems : Seq[Elem] ) : List[(String,String)] =
    elems.foldLeft( accum )( (soFar, next) => namespaces( soFar, next.descendant_or_self.map( _.scope ) ) )

  def extractNamespaces( roots : Elem* ) : Map[String,String] =
    val raw = elemNamespaces( List.empty, roots )
    val noDupTups = raw.toSet.filter( _(0) != null )
    val keys = noDupTups.toSeq.map( _(0) )
    val dupKeys = keys.groupBy( identity ).filter( (_,v) => v.size > 1 ).keys.toSet
    if dupKeys.nonEmpty then
      val badBindings = noDupTups.filter( (k,v) => dupKeys(k) )
      throw new IncompatibleDuplicateBindings( badBindings )
    else
      noDupTups.toMap
  
  def toText( node : Node ) : String =
    val pp = new PrettyPrinter(120,2)
    pp.format( node )

//Fri, 25 Aug 2023 12:10:00 EDT

  //NOT given or implicit please!
  val ItemOrdering =
    def parsePubDate( str : String ) : Instant =
      (Try(RssDateTimeFormatter.parse(str)) orElse Try(LenientRssDateTimeFormatter.parse(str))) match
         case Success( ta ) => Instant.from( ta ) // TemporalAccessor
         case Failure( t ) =>
           System.err.println(t.toString)
           System.err.println(s"Found unparsable date: ${str}, using an arbitrary very early date.")
           stableEarlyInstant(str)
      
    def pubDate( itemElem : Elem ) : Instant =
      val pds = (itemElem \ "pubDate").map( _.text )
      if pds.length > 1 then
        //throw BadItemXml(s"Expected precisely one 'pubDate' in item, found ${pds.length}:${linesep}${toText(itemElem)}")
        System.err.println(s"Expected precisely one 'pubDate' in item, found ${pds.length}:${linesep}${toText(itemElem)}, will use first.")
        parsePubDate( pds.head )  
      else if pds.length == 1 then
        parsePubDate( pds.head )
      else  
        System.err.println(s"Expected precisely one 'pubDate' in item, found ${pds.length}:${linesep}${toText(itemElem)}, will use an arbitrar early timestamp!")
        stableEarlyInstant( itemElem.toString )
        
    Ordering.by[Elem,Instant]( pubDate ).reverse
  end ItemOrdering  

  def merge(spec : Element.Channel.Spec, itemLimit : Int, roots : Elem* ) : Element.Rss =
    val allNamespaces = extractNamespaces(roots*)
    val unscoped = roots.map( stripScopes ).map( _.asInstanceOf[Elem] )
    val arssNamespaces = allNamespaces.map( (k,v) => Namespace(k,v) ).toList
    val allItems = unscoped.flatMap( _ \\ "item" ).map( _.asInstanceOf[Elem] ).sorted(ItemOrdering)
    val limitedItems = allItems.take( itemLimit )
    val channel = Element.Channel.create(spec, Iterable.empty[Element.Item]).withExtras( limitedItems )
    Element.Rss(channel).overNamespaces(arssNamespaces)
