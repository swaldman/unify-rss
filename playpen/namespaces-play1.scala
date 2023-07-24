//> using scala "3.3.0"
//> using dep "org.scala-lang.modules::scala-xml:2.2.0"

import java.net.*
import scala.xml.*

import scala.annotation.tailrec

case class BadDup( newTup : (String,String), rest : Map[String,String] )

@main
def play =
  val drafts = XML.load(new URL("https://drafts.interfluidity.com/feed/index.rss"))
  val main = XML.load(new URL("https://www.interfluidity.com/feed"))
  println {
    for
      fromDrafts <- namespaces( Map.empty, drafts )
      fromAll    <- namespaces( fromDrafts, main )
    yield fromAll
  }

val Nonresolver : BadDup => Either[BadDup, Map[String,String]] = badDup => Left(badDup)

@tailrec
def namespacesFromBinding( accum : Map[String,String], binding : NamespaceBinding, resolver : BadDup => Either[BadDup, Map[String,String]] = Nonresolver ) : Either[BadDup,Map[String,String]] =
  if binding == null then Right(accum)
  else
    val newTup = ( binding.prefix, binding.uri )
    accum.get( binding.prefix ) match
      case None                => namespacesFromBinding( accum + newTup, binding.parent, resolver )
      case Some( binding.uri ) => namespacesFromBinding( accum, binding.parent, resolver )
      case Some( otherUrl )    =>
        val badDup = BadDup( newTup, accum )
        resolver(badDup) match
          case stillBad : Left[BadDup,Map[String,String]] => stillBad
          case Right( resolved ) => namespacesFromBinding( resolved, binding.parent, resolver )
end namespacesFromBinding

def namespaces( accum : Map[String,String], node : Node, resolver : BadDup => Either[BadDup, Map[String,String]] = Nonresolver ) : Either[BadDup,Map[String,String]] =
  val kids = node.child
  if node.child != null && node.child.nonEmpty then
    val next = kids.foldLeft( Right(accum) : Either[BadDup,Map[String,String]] ){ (sofar, next) =>
      sofar match
        case oops : Left[BadDup,Map[String,String]] => oops
        case Right( map ) => namespaces( map, next, resolver )
    }
    next match
      case oops : Left[BadDup,Map[String,String]] => oops
      case Right(map) => namespacesFromBinding( map, node.scope, resolver )
  else
    namespacesFromBinding( accum, node.scope, resolver )
end namespaces    




