package com.mchange.unifyrss

import scala.annotation.tailrec
import scala.xml.*
import scala.collection.*
import unstatic.UrlPath.*
import zio.*
import sttp.tapir.Endpoint
import audiofluidity.rss.Namespace

val linesep = System.lineSeparator

class UnifyRssException( message : String, cause : Throwable = null ) extends Exception( message, cause )

class IncompatibleNamespaces(namespaces : immutable.Set[Namespace]) extends UnifyRssException(s"Incompatible namespaces! ${namespaces}", null)
class BadItemXml(message : String, cause : Throwable = null)                   extends UnifyRssException( message, cause )
class BadAtomXml(message : String, cause : Throwable = null)                   extends UnifyRssException( message, cause )
class XmlFetchFailure(message : String, cause : Throwable = null)              extends UnifyRssException( message, cause )
class CantConvertToRss(message : String, cause : Throwable = null)              extends UnifyRssException( message, cause )

type FeedRefMap      = immutable.Map[Rel,Ref[immutable.Seq[Byte]]]
type FeedEndpointMap = immutable.Map[Rel,Endpoint[Unit,Unit,String,Array[Byte],Any]]

def fullStackTrace(t:Throwable) : String =
  val sw = new java.io.StringWriter()
  t.printStackTrace(new java.io.PrintWriter(sw))
  sw.toString()

def stripScopes(root: Node): Node =
  // didn't work, namespaces reappeared, don't understand why exactly,
  // i think maybe it's because we want to keep the prefixes in attributed
  /*
  val rule = new RewriteRule:
    override def transform(n: Node) : Seq[Node] =
      n match
        case e : Elem => e.copy(scope = TopScope)
        case other => other
  val xform = new RuleTransformer(rule)
  xform(root)
  */
  // this does work,
  // from https://stackoverflow.com/questions/12535014/scala-completely-remove-namespace-from-xml
  def clearScope(x: Node): Node = x match {
    case e: Elem => e.copy(scope = TopScope, child = e.child.map(clearScope))
    case o => o
  }
  clearScope(root)

@tailrec
def unprefixedNamespaceOnly( binding : NamespaceBinding ) : NamespaceBinding =
  binding match
    case NamespaceBinding(null,   null, null  ) => TopScope
    case NamespaceBinding(null,   null, parent) => unprefixedNamespaceOnly( parent )
    case NamespaceBinding(null,    uri, _     ) => NamespaceBinding(null, uri, TopScope)
    case NamespaceBinding(prefix,    _, null  ) => TopScope
    case NamespaceBinding(prefix,    _, parent) => unprefixedNamespaceOnly( parent )
    

def stripPrefixedNamespaces( root : Node ) : Node =
  def clearPrefixedNamespaces(x: Node): Node = x match {
    case e: Elem => e.copy(scope = unprefixedNamespaceOnly(e.scope), child = e.child.map(clearPrefixedNamespaces))
    case o => o
  }
  clearPrefixedNamespaces(root)

@tailrec
def scopeContains( prefix : String, uri : String, binding : NamespaceBinding ) : Boolean =
  if binding == TopScope then
    false
  else if prefix == binding.prefix && uri == binding.uri then
    true
  else
    scopeContains( prefix, uri, binding.parent )

