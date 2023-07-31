package com.mchange.unifyrss

import java.time.{ZonedDateTime,ZoneId}
import java.time.format.DateTimeFormatter
import scala.xml.*
import audiofluidity.rss.{Element,Namespace}

private def mbUniqueText(parent : Elem, label : String) : Option[String] =
  val nseq = parent \ label
  if nseq.size == 1 then
    Some(nseq.head.text)
  else
    None

private def uniqueText(parent : Elem, label : String) : String =
  mbUniqueText(parent, label).getOrElse:
    throw new BadAtomXml(s"Expected, did not find, a unique '${label}' in '${parent.label}'.")

private def findPubDate(entryElem : Elem) : ZonedDateTime =
  val isoInstant =
    (mbUniqueText( entryElem, "published" ) orElse mbUniqueText( entryElem, "updates")).getOrElse:
      throw new BadAtomXml(s"Found neither an 'published' nor 'updated' element in entry.")
  ZonedDateTime.from( DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault()).parse(isoInstant) )

val DefaultAuthorEmail = "nospam@dev.null"

private def namesToNameStr( names : Seq[String] ) : Option[String] =
  names.size match
    case 0 => None
    case 1 => Some( names.head )
    case 2 => Some( names.mkString(" and ") )
    case _ =>
      val modifiedNames = names.init :+ s"and ${names.last}"
      Some( modifiedNames.mkString(", ") )

private def rssAuthor( entryElem : Elem, defaultAuthor : Option[String] = None ) : String =
  val entryAuthors = entryElem \ "author"
  val email =
    if entryAuthors.length > 1 || entryAuthors.isEmpty then
      DefaultAuthorEmail
    else
      mbUniqueText( entryAuthors.head.asInstanceOf[Elem], "email" ).getOrElse( DefaultAuthorEmail )
  val names = entryAuthors.map( authorElem => mbUniqueText(authorElem.asInstanceOf[Elem], "name") ).collect { case Some(name) => name }
  val mbNamesStr = namesToNameStr( names )
  val suffix = mbNamesStr.fold("")(str => s" (${str})")
  s"""${email}${suffix}"""

def itemElemFromEntryElem( entryElem : Elem, defaultAuthor : Option[String] = None ) : Elem =
  val title = uniqueText( entryElem, "title")
  val guid = uniqueText(entryElem, "id")
  val link = (entryElem \ "link").filter( node => node \@ "rel" == "alternate" || node \@ "rel" == "").head \@ "href"
  val description = mbUniqueText( entryElem, "summary" ).getOrElse(s"Entry entitled '${title}.'") // really lame, but do I want to bring in jsoup to summarize contents?
  val author = rssAuthor( entryElem, defaultAuthor )
  val pubDate = findPubDate( entryElem )
  val mbContents = mbUniqueText( entryElem, "content" )
  val guidIsPermalink = guid == link
  val simpleItem =
    Element.Item.create(
      title = title,
      linkUrl = link,
      description = description,
      author = author,
      guid = Some(Element.Guid(guidIsPermalink, guid)),
      pubDate = Some(pubDate)
    )
  val withContentItem = mbContents.fold(simpleItem)(contents => simpleItem.withExtra(Element.Content.Encoded(contents)))
  val extraAtomLinks : Seq[Elem] = (entryElem \ "link").map( node => stripScopes(node).asInstanceOf[Elem].copy(prefix="atom") )
  val withLinksItem = withContentItem.withExtras( extraAtomLinks )
  withLinksItem.toElem

def rssElemFromAtomFeedElem( atomFeedElem : Elem ) : Elem =
  val topTitle = uniqueText( atomFeedElem, "title" )
  val topLink = (atomFeedElem \ "link").filter( node => node \@ "rel" == "alternate" || node \@ "rel" == "").head \@ "href"
  val defaultAuthor =
    val mbNames = (atomFeedElem \ "author").map( authorElem => mbUniqueText(authorElem.asInstanceOf[Elem], "name" ) )
    val names = mbNames.collect { case Some(name) => name }
    namesToNameStr(names)
  val description = mbUniqueText(atomFeedElem, "subtitle").getOrElse(s"RSS feed converstion of atom feed '${topTitle}'")
  val items : Seq[Elem] = (atomFeedElem \ "entry").map ( node => itemElemFromEntryElem( node.asInstanceOf[Elem], defaultAuthor ) )
  val channel = Element.Channel( title = Element.Title(topTitle), link = Element.Link(topLink), description = Element.Description(description),items=Nil).withExtras(items)
  Element.Rss( channel ).overNamespaces( Namespace.RdfContent :: Namespace.Atom :: Nil ).toElem


