package com.mchange.unifyrss

import java.time.Instant

import scala.xml.*
import scala.xml.transform.*
import scala.collection.*
import scala.util.{Try,Success,Failure}

import scala.annotation.tailrec

import audiofluidity.rss.*
import audiofluidity.rss.util.*

object RssMerger:

  def stableEarlyInstant( s : String ) = Instant.ofEpochMilli(s.hashCode * 1_000_000)

  def extractPrefixedNamespaces( roots : Elem* ) : Set[Namespace] =
    val raw = Namespace.fromElemsRecursive( roots* )
    val prefixed = raw.toSet.filter( _.prefix != None )
    val excludingConflicts = Namespace.canonicalizeConflicts( prefixed )
    if excludingConflicts.excluded.nonEmpty then
      throw new IncompatibleNamespaces( excludingConflicts.excludedNamespaces )
    else
      excludingConflicts.withUniquePrefixes

  def toText( node : Node ) : String =
    val pp = new PrettyPrinter(120,2)
    pp.format( node )

  //NOT given or implicit please!
  val ItemOrdering =
    def parsePubDate( str : String ) : Instant =
      attemptLenientParsePubDateToInstant(str) match
         case Success( instant ) => instant
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
        System.err.println(s"Expected precisely one 'pubDate' in item, found ${pds.length}:${linesep}${toText(itemElem)}, will use an arbitrary early timestamp!")
        stableEarlyInstant( itemElem.toString )

    Ordering.by[Elem,Instant]( pubDate ).reverse
  end ItemOrdering

  def merge(spec : Element.Channel.Spec, itemLimit : Int, roots : Elem* ) : Element.Rss =
    val allPrefixedNamespaces = extractPrefixedNamespaces(roots*).toList
    val noprefixed = roots.map( stripPrefixedNamespaces ).map( _.asInstanceOf[Elem] )
    val allItems = noprefixed.flatMap( _ \\ "item" ).map( _.asInstanceOf[Elem] ).sorted(ItemOrdering)
    val limitedItems = allItems.take( itemLimit )
    val channel = Element.Channel.create(spec, Iterable.empty[Element.Item]).withExtras( limitedItems )
    Element.Rss(channel).overNamespaces(allPrefixedNamespaces)
