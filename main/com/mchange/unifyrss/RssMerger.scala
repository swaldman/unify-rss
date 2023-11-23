package com.mchange.unifyrss

import java.time.Instant

import scala.xml.*
import scala.xml.transform.*
import scala.collection.*
import scala.util.{Try,Success,Failure}

import scala.annotation.tailrec

import audiofluidity.rss.*
import audiofluidity.rss.util.attemptLenientParsePubDateToInstant

object RssMerger:

  def stableEarlyInstant( s : String ) = Instant.ofEpochMilli(s.hashCode * 1_000_000)

  @tailrec
  private def namespaces( accum : List[Namespace], binding : NamespaceBinding ) : List[Namespace] =
    if binding == TopScope || binding == null then accum else namespaces( Namespace(binding.prefix, binding.uri ) :: accum, binding.parent)

  private def namespaces( accum : List[Namespace], bindingSeq : Seq[NamespaceBinding] ) : List[Namespace] =
    bindingSeq.foldLeft( accum )( (soFar, next) => namespaces( soFar, next ) )

  private def elemNamespaces( accum : List[Namespace], elems : Seq[Elem] ) : List[Namespace] =
    elems.foldLeft( accum )( (soFar, next) => namespaces( soFar, next.descendant_or_self.map( _.scope ) ) )

  private def findDupKeys( namespaces : immutable.Set[(String,String)] ) : Set[String] =
    val keys = namespaces.toSeq.map( _(0) )
    keys.groupBy( identity ).filter( (_,v) => v.size > 1 ).keys.toSet

  private def findDupPrefixes( namespaces : immutable.Set[Namespace] ) : Set[String] =
    findDupKeys( namespaces.map( Tuple.fromProductTyped ) )

  def extractNamespaces( roots : Elem* ) : Map[String,String] =
    val raw = elemNamespaces( List.empty, roots )
    val real = raw.toSet.filter( _.prefix != null ).map( _.canonical )
    val dupPrefixes = findDupPrefixes( real )
    if dupPrefixes.nonEmpty then
      val badNamespaces = real.filter{ case Namespace(p,u) => dupPrefixes(p) }
      throw new IncompatibleNamespaces( badNamespaces )
    else
      real.map( ns => (ns.prefix, ns.uri) ).toMap

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
    val allNamespaces = extractNamespaces(roots*)
    val unscoped = roots.map( stripScopes ).map( _.asInstanceOf[Elem] )
    val arssNamespaces = allNamespaces.map( (k,v) => Namespace(k,v) ).toList
    val allItems = unscoped.flatMap( _ \\ "item" ).map( _.asInstanceOf[Elem] ).sorted(ItemOrdering)
    val limitedItems = allItems.take( itemLimit )
    val channel = Element.Channel.create(spec, Iterable.empty[Element.Item]).withExtras( limitedItems )
    Element.Rss(channel).overNamespaces(arssNamespaces)
