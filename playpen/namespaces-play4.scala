//> using scala "3.3.0"
//> using dep "org.scala-lang.modules::scala-xml:2.2.0"

import java.net.*
import scala.xml.*

import scala.annotation.tailrec

@tailrec
def namespaces( accum : List[(String,String)], binding : NamespaceBinding ) : List[(String,String)] =
  if binding == null then accum else namespaces( (binding.prefix, binding.uri ) :: accum, binding.parent)

def namespaces( accum : List[(String,String)], bindingSeq : Seq[NamespaceBinding] ) : List[(String,String)] =
  bindingSeq.foldLeft( accum )( (soFar, next) => namespaces( soFar, next ) )

def allNamespaces( root : Elem ) : List[(String,String)] =
  namespaces(List.empty, root.descendant_or_self.map( _.scope ))

def condenseNamespaces( raw : List[(String,String)] ) : Map[String,String] =
  val noDupTup = raw.toSet.filter( _(0) != null )
  val keys = noDupTup.toSeq.map( _(0) )
  val dups = keys.groupBy( identity ).filter( (_,v) => v.size > 1 ).keys
  if dups.nonEmpty then throw new Exception(s"Contains incompatible duplicate bindings! ${keys})")
  noDupTup.toMap

@main
def play =
  val drafts = XML.load(new URL("https://drafts.interfluidity.com/feed/index.rss"))
  val main = XML.load(new URL("https://www.interfluidity.com/feed"))
  println( condenseNamespaces( allNamespaces( drafts ) ::: allNamespaces( main ) ) )


