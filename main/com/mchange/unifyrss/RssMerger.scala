package com.mchange.unifyrss

import java.time.Instant

import scala.xml.*
import scala.xml.transform.*
import scala.collection.*

import scala.annotation.tailrec

import audiofluidity.rss.*

object RssMerger:
  val RssDateTimeFormatter = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

  @tailrec
  private def namespaces( accum : List[(String,String)], binding : NamespaceBinding ) : List[(String,String)] =
    if binding == null then accum else namespaces( (binding.prefix, binding.uri ) :: accum, binding.parent)

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

  def stripScopes( root : Node ) : Node =
    val rule = new RewriteRule:
      override def transform(n: Node) : Seq[Node] =
        n match
          case e : Elem => e.copy(scope = null)
          case other => other
    val xform = new RuleTransformer(rule)
    xform(root)

  def toText( node : Node ) : String =
    val pp = new PrettyPrinter(120,2)
    pp.format( node )

  //NOT given or implicit please!
  val ItemOrdering =
    def pubDate( itemElem : Elem ) : Instant =
      val pds = (itemElem \ "pubDate").map( _.text )
      if pds.length != 1 then
        throw BadItemXml(s"Expected precisely one 'pubDate' in item, found ${pds.length}:${linesep}${toText(itemElem)}")
      else
        Instant.from( RssDateTimeFormatter.parse(pds.head) )
    Ordering.by[Elem,Instant]( pubDate ).reverse

  def merge(spec : Element.Channel.Spec, roots : Elem* ) : Element.Channel =
    val allNamespaces = extractNamespaces(roots*)
    val unscoped = roots.map( stripScopes ).map( _.asInstanceOf[Elem] )
    val arssNamespaces = allNamespaces.map( (k,v) => Namespace(k,v) ).toList
    val allItems = unscoped.flatMap( _ \ "item" ).map( _.asInstanceOf[Elem] ).sorted(ItemOrdering)
    Element.Channel.create(spec, Iterable.empty[Element.Item]).overNamespaces(arssNamespaces).withExtras( allItems )