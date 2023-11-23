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
  private def namespaces( accum : List[(String,String)], binding : NamespaceBinding ) : List[(String,String)] =
    if binding == TopScope || binding == null then accum else namespaces( (binding.prefix, binding.uri ) :: accum, binding.parent)

  private def namespaces( accum : List[(String,String)], bindingSeq : Seq[NamespaceBinding] ) : List[(String,String)] =
    bindingSeq.foldLeft( accum )( (soFar, next) => namespaces( soFar, next ) )

  private def elemNamespaces( accum : List[(String,String)], elems : Seq[Elem] ) : List[(String,String)] =
    elems.foldLeft( accum )( (soFar, next) => namespaces( soFar, next.descendant_or_self.map( _.scope ) ) )

  private def findDupKeys( namespaces : immutable.Set[(String,String)] ) : Set[String] =
    val keys = namespaces.toSeq.map( _(0) )
    keys.groupBy( identity ).filter( (_,v) => v.size > 1 ).keys.toSet

  private def attemptCanonicalizeNamespaces( withDups : immutable.Set[(String,String)] ) : immutable.Set[(String,String)] =
    val attempted = withDups.map { ( prefix, uri ) =>
      val normalizedUri =
        uri.dropWhile( _ != ':' ) // ignore http / https variation
          .reverse
          .dropWhile( _ == '/' ) // ignore trailing slashes
          .reverse

      (prefix, normalizedUri) match
        case ("atom", "://www.w3.org/2005/Atom") => ("atom", "http://www.w3.org/2005/Atom")
        case ("podcast", "://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/1.0.md") => ("podcast", "https://podcastindex.org/namespace/1.0")
        case ("podcast", "://podcastindex.org/namespace/1.0") => ("podcast", "https://podcastindex.org/namespace/1.0")
        case ("psc", "://podlove.org/simple-chapters") => ("psc", "http://podlove.org/simple-chapters")
        case ("googleplay", "://www.google.com/schemas/play-podcasts/1.0") => ("googleplay", "http://www.google.com/schemas/play-podcasts/1.0")
        case ("cc", "://blogs.law.harvard.edu/tech/creativeCommonsRssModule") => ("cc", "http://web.resource.org/cc/")
        case ("cc", "://backend.userland.com/creativeCommonsRssModule") => ("cc", "http://web.resource.org/cc/")
        case ("cc", "://cyber.law.harvard.edu/rss/creativeCommonsRssModule.html") => ("cc", "http://web.resource.org/cc/")
        case ("media", "://www.rssboard.org/media-rss") => ("media", "http://search.yahoo.com/mrss/")
        case ("media", "://search.yahoo.com/rss") => ("media", "http://search.yahoo.com/mrss/")
        case ("source", "://source.smallpict.com/2014/07/12/theSourceNamespace.html") => ("source", "http://source.scripting.com/")
        case ("source", "://source.scripting.com") => ("source", "http://source.scripting.com/")
        case other => ( prefix, uri )
    }
    val dupKeys = findDupKeys( attempted )
    if dupKeys.nonEmpty then
      val badBindings = withDups.filter( (k,v) => dupKeys(k) )
      throw new IncompatibleDuplicateBindings( badBindings )
    else
      attempted

  def extractNamespaces( roots : Elem* ) : Map[String,String] =
    val raw = elemNamespaces( List.empty, roots )
    val real = raw.toSet.filter( _(0) != null )
    val dupKeys = findDupKeys( real )
    if dupKeys.nonEmpty then
      attemptCanonicalizeNamespaces( real ).toMap
    else
      real.toMap

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
