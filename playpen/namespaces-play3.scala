//> using scala "3.3.0"
//> using dep "org.scala-lang.modules::scala-xml:2.2.0"
//> using dep "dev.optics::monocle-core:3.2.0"

import java.net.*
import scala.xml.*

import scala.annotation.tailrec
import monocle.Traversal
import monocle.function.Plated
import cats.Applicative
import cats.syntax.all.*

given Traversal[Seq[Node],Seq[Node]] with
  def modifyA[F[_] : Applicative](f: (Seq[Node]) => F[Seq[Node]])(s: Seq[Node]): F[Seq[Node]] = s.flatMap( _.child ).pure[F]

given Plated[Seq[Node]] with
  def plate : Traversal[Seq[Node], Seq[Node]] = summon[Traversal[Seq[Node],Seq[Node]]]

@tailrec
def namespaces( accum : List[(String,String)], binding : NamespaceBinding ) : List[(String,String)] =
  if binding == null then accum else namespaces( (binding.prefix, binding.uri ) :: accum, binding.parent)

def namespaces( accum : List[(String,String)], bindingSeq : Seq[NamespaceBinding] ) : List[(String,String)] =
  bindingSeq.foldLeft( accum )( (soFar, next) => namespaces( soFar, next ) )

def allNamespaces( root : Seq[Node] ) : List[(String,String)] =
  Plated.universe( root ).foldLeft( List.empty[(String,String)] )( ( soFar, next ) => namespaces( soFar, next.map( _.scope ) ) )

def condenseNamespaces( raw : List[(String,String)] ) : Map[String,String] =
  val noDupTup = raw.toSet.filter( _(0) != null )
  val keys = noDupTup.toSeq.map( _(0) )
  if keys.size != keys.toSet.size then throw new Exception(s"Contains incompatible duplicate bindings! ${noDupTup})")
  noDupTup.toMap

@main
def play =
  val drafts = XML.load(new URL("https://drafts.interfluidity.com/feed/index.rss"))
  val main = XML.load(new URL("https://www.interfluidity.com/feed"))
  println( condenseNamespaces( allNamespaces( drafts ) ::: allNamespaces( main ) ) )


