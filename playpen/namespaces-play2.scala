//> using scala "3.3.0"
//> using dep "org.scala-lang.modules::scala-xml:2.2.0"

import java.net.*
import scala.xml.*

import scala.annotation.tailrec

@tailrec
def namespaces( accum : List[(String,String)], binding : NamespaceBinding ) : List[(String,String)] =
  if binding == null then accum else namespaces( (binding.prefix, binding.uri ) :: accum, binding.parent)

// we know RSS shouldn't go too deep, but not stack-safe if it did
def namespaces( accum : List[(String,String)], node : Node ) : List[(String,String)] =
  val kids = node.child
  val withKids =
    if node.child != null && node.child.nonEmpty then
      kids.foldLeft( accum )( (soFar, nextKid) => namespaces( soFar, nextKid ) )
    else
      accum
  namespaces( accum, node.scope )

def condenseNamespaces( raw : List[(String,String)] ) : Map[String,String] =
  val noDupTup = raw.toSet.filter( _(0) != null )
  val keys = noDupTup.toSeq.map( _(0) )
  if keys.size != keys.toSet.size then throw new Exception(s"Contains incompatible duplicate bindings! ${noDupTup})")
  noDupTup.toMap

@main
def play =
  val drafts = XML.load(new URL("https://drafts.interfluidity.com/feed/index.rss"))
  val main = XML.load(new URL("https://www.interfluidity.com/feed"))
  println {
    condenseNamespaces( namespaces( namespaces( List.empty, drafts ), main ) )
  }

