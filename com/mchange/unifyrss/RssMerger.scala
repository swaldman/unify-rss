package com.mchange.unifyrss

import java.time.Instant

import scala.xml.*
import scala.xml.transform.*
import scala.collection.*
import scala.util.control.NonFatal
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

  extension ( ns : NodeSeq )
    def stripElem(prefix : String, label : String) : NodeSeq = ns.collect { case el : Elem if el.prefix != prefix || el.label != label => el; case n if !n.isInstanceOf[Elem] => n }

  def embedReplaceProvenance( item : Elem, href : String ) : Elem =
    def viaLink = Element.Atom.Link(href=href,rel=Some(Element.Atom.LinkRelation.via),`type`=Some("application/rss+xml"))
    val newItem =
      val provenances = (item \ "provenance").filter( _.prefix == "iffy" )
      if provenances.nonEmpty then
        if provenances.size > 1 then
          System.err.println("Found an item with more than one 'iffy:provenance' element. Will drop all but the first!")
        val newProvenance =
          val provenance = provenances.head.asInstanceOf[Elem]
          provenance.copy( child = (viaLink.toElem +: provenance.child) )
        val newChildren = item.child.stripElem("iffy", "provenance") :+ newProvenance
        item.copy( child = newChildren )
      else
        item.copy( child = item.child :+ Element.Iffy.Provenance(viaLink::Nil).toElem )
    newItem

  def embedProvenance( root : Elem ) : Elem =
    val origChannel = (root \ "channel").headOption.getOrElse( throw new BadRssXml("Expected a channel, found none.") ).asInstanceOf[Elem]
    val atomSelfLinks =
      (origChannel \ "link")
        .collect { case elem : scala.xml.Elem if elem.prefix == "atom" => elem }
        .filter( _ \@ "rel" == "self" )
    if atomSelfLinks.length != 1 then
      System.err.println("No unique atom:link with rel=\"self\" found in channel. Cannot embed provenance.");
      root
    else
      val href = atomSelfLinks.head \@ "href"
      val items = (origChannel \ "item")
      val newItems = items.map( elem => embedReplaceProvenance(elem.asInstanceOf[Elem],href) )
      val newChannelChildren = origChannel.child.stripElem(null, "item") ++ newItems
      val newChannel = origChannel.copy( child = newChannelChildren )
      val newRssChildren = root.child.stripElem(null, "channel") :+ newChannel
      root.copy( child = newRssChildren )

  def attemptEmbedProvenance( root : Elem ) : Elem =
    try embedProvenance( root )
    catch
      case NonFatal(e) =>
        System.err.println("An Exception occurred while trying to embed provenance. Skipping.")
        e.printStackTrace()
        root

  def merge(mergedFeedUrl : String, spec : Element.Channel.Spec, itemLimit : Int, roots : Elem* ) : Element.Rss =
    val allPrefixedNamespaces = extractPrefixedNamespaces(roots*).toList
    val noprefixed = roots.map( stripPrefixedNamespaces ).map( _.asInstanceOf[Elem] )
    val withProvenances = noprefixed.map( attemptEmbedProvenance )
    val allItems = withProvenances.flatMap( _ \\ "item" ).map( _.asInstanceOf[Elem] ).sorted(ItemOrdering)
    val limitedItems = allItems.take( itemLimit )
    val atomSelfLink = Element.Atom.Link(href=mergedFeedUrl,rel=Some(Element.Atom.LinkRelation.self),`type`=Some("application/rss+xml")) // see https://www.rssboard.org/rss-profile#namespace-elements-atom-link
    val channel = Element.Channel.create(spec, Iterable.empty[Element.Item]).withExtra(atomSelfLink).withExtras( limitedItems )
    Element.Rss(channel).overNamespaces(allPrefixedNamespaces :+ Namespace.Iffy) // Namespace.Iffy for provenance element
