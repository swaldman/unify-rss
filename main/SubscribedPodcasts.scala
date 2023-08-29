import com.mchange.unifyrss.*

import java.net.URL
import scala.collection.*
import unstatic.UrlPath.*
import scala.xml.*
import scala.xml.transform.*
import audiofluidity.rss.Element

object SubscribedPodcasts:

  private val FeedCoverUrl = "https://www.interfluidity.com/uploads/2023/08/ripply-river-midjourney-smaller.png"

  private def feedCoverCoverImageElement =
    val urlElement = Element.Url(location=FeedCoverUrl)
    val titleElement = Element.Title(text=InterfluidityMain.SubscribedPodcastsFeed.title(Nil))
    val linkElement = Element.Link(location="https://www.interfluidity.com/")
    val descElement = Element.Description(text=InterfluidityMain.SubscribedPodcastsFeed.description(Nil))
    Element.Image(urlElement,titleElement,linkElement,None,None,Some(descElement))

  private def prefixTitlesOfItemElem(prefix: String, itemElem: Elem): Elem =
    val oldTitleElems = (itemElem \ "title")
    if oldTitleElems.nonEmpty then
      val newChildren = itemElem.child.map: node =>
        if !oldTitleElems.contains(node) then node
        else
          val oldTitleElem = node.asInstanceOf[Elem]
          oldTitleElem.copy(child = Seq(Text(prefix + oldTitleElem.text)))
      itemElem.copy(child = newChildren)
    else
      itemElem.copy(child = itemElem.child :+ Elem("", "title", Null, TopScope, true, Text(prefix + "Untitled Item")))

  private val PrefixTransformations = Map("Podcasts" -> "TAP")

  private def prependFeedTitleToItemTitles(rssElem: Elem): Elem =
    val feedPrefix =
      val queryResult = (rssElem \ "channel").map(_ \ "title")
      val rawPrefix = queryResult.head.text.trim
      val goodPrefix = PrefixTransformations.getOrElse(rawPrefix, rawPrefix)
      if queryResult.nonEmpty then (goodPrefix + ": ") else ""
    val rule = new RewriteRule:
      override def transform(n: Node): Seq[Node] = n match
        case elem: Elem if elem.label == "item" => prefixTitlesOfItemElem(feedPrefix, elem)
        case other => other
    val transform = new RuleTransformer(rule)
    transform(rssElem).asInstanceOf[Elem]

  private def copyItunesImageElementsToItems(rssElem: Elem): Elem =
    val mbItunesFeedImage =
      val queryResult = (rssElem \ "channel").flatMap(_ \ "image").filter(_.asInstanceOf[Elem].prefix == "itunes")
      if queryResult.nonEmpty then Some(queryResult.head) else None
    val mbRegularFeedImage =
      val queryResult = (rssElem \ "channel").flatMap(_ \ "image").filter(_.asInstanceOf[Elem].prefix == null)
      if queryResult.nonEmpty then Some(queryResult.head) else None
    val mbFeedImage = mbItunesFeedImage orElse mbRegularFeedImage.map: regularImageElem =>
      val url = (regularImageElem \ "url").head.text.trim
      Element.Itunes.Image(href = url).toElem
    mbFeedImage.fold(rssElem): feedImage =>
      val rule = new RewriteRule:
        override def transform(n: Node): Seq[Node] = n match
          case elem: Elem if elem.label == "item" =>
            if (elem \ "image").isEmpty then
              elem.copy(child = elem.child :+ feedImage.asInstanceOf[Elem])
            else
              elem
          case other => other
      val transform = new RuleTransformer(rule)
      transform(rssElem).asInstanceOf[Elem]

  private def stripItunesSeason( rssElem : Elem ) : Elem =
    val rule = new RewriteRule:
      override def transform(n: Node): Seq[Node] = n match
        case elem: Elem if elem.label == "season" && elem.prefix == "itunes" => NodeSeq.Empty
        case other => other
    val transform = new RuleTransformer(rule)
    transform(rssElem).asInstanceOf[Elem]

  private val embellishFeed : Elem => Elem =
    stripItunesSeason andThen prependFeedTitleToItemTitles andThen copyItunesImageElementsToItems

  def bestAttemptEmbellish(anyTopElem: Elem): Elem =
    val rssElem: Option[Elem] = anyTopElem.label match
      case "rss" => Some(anyTopElem)
      case "feed" if scopeContains(null, "http://www.w3.org/2005/Atom", anyTopElem.scope) => Some(rssElemFromAtomFeedElem(anyTopElem))
      case _ => None
    rssElem.fold(anyTopElem)(embellishFeed)

  def addFeedImageElement(rssElem : Elem) : Elem =
    val rule = new RewriteRule:
      override def transform(n: Node): Seq[Node] = n match
        case elem: Elem if elem.label == "channel" => elem.copy( child = feedCoverCoverImageElement.toElem +: elem.child )
        case other => other
    val transform = new RuleTransformer(rule)
    transform(rssElem).asInstanceOf[Elem]


  // there is no need or point to this. NPR helpfully keeps only one news headline item in their feed.
  // i see a zillion copies in Inoreader only because inoreader retains everything it has seen
  /*
  def onlyMostRecentNPRNewsInOutput( outputElem : Elem ) : Elem =
    val allNprNewsItems = (outputElem \\ "item").filter( item => (item \ "title").foldLeft("")( (accum, next) => accum + next.text ).indexOf("NPR News:") >= 0 )
    if allNprNewsItems.nonEmpty then
      val topNprNewsItem = allNprNewsItems.head
      val deleteAllNprNewsButTopRule = new RewriteRule:
        override def transform(n: Node): Seq[Node] = n match
          case elem: Elem if allNprNewsItems.contains(elem) && elem != topNprNewsItem => NodeSeq.Empty
          case other => other
      val transform = new RuleTransformer(deleteAllNprNewsButTopRule)
      transform( outputElem ).asInstanceOf[Elem]
    else
      outputElem
  */
