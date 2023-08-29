import com.mchange.unifyrss.*

import java.net.URL
import scala.collection.*
import unstatic.UrlPath.*
import scala.xml.*
import scala.xml.transform.*
import audiofluidity.rss.Element

object InterfluidityMain extends AbstractMain {

  val allBlogs = immutable.Seq(
    SourceUrl("https://drafts.interfluidity.com/feed/index.rss"),
    SourceUrl("https://tech.interfluidity.com/feed/index.rss"),
    SourceUrl("https://www.interfluidity.com/feed"),
    SourceUrl("https://www.sbt-ethereum.io/blog/atom.xml"),
  )

  val allBlogsAndMicroblogs = allBlogs ++ immutable.Seq(
    SourceUrl("https://econtwitter.net/@interfluidity.rss"),
    SourceUrl("https://fosstodon.org/@interfluidity.rss"),
  )

  val everything = allBlogs ++ allBlogsAndMicroblogs ++ immutable.Seq(
    SourceUrl("https://github.com/swaldman.atom"),
  )

  def prefixTitlesOfItemElem( prefix : String, itemElem : Elem ) : Elem =
    val oldTitleElems = (itemElem \ "title")
    if oldTitleElems.nonEmpty then
      val newChildren = itemElem.child.map: node =>
        if !oldTitleElems.contains(node) then node
        else
          val oldTitleElem = node.asInstanceOf[Elem]
          oldTitleElem.copy( child = Seq(Text(prefix+oldTitleElem.text)) )
      itemElem.copy( child = newChildren )
    else
      itemElem.copy( child = itemElem.child :+ Elem("","title",Null,TopScope,true, Text(prefix + "Untitled Item")) )

  val PrefixTransformations = Map( "Podcast" -> "TAP")

  def prependFeedTitleToItemTitles( rssElem : Elem ) : Elem =
    val feedPrefix =
      val queryResult = (rssElem \ "channel").map( _ \ "title")
      val rawPrefix = queryResult.head.text.trim
      val goodPrefix = PrefixTransformations.getOrElse(rawPrefix, rawPrefix)
      if queryResult.nonEmpty then (goodPrefix + ": ") else ""
    val rule = new RewriteRule:
      override def transform(n: Node): Seq[Node] = n match
        case elem: Elem if elem.label == "item" => prefixTitlesOfItemElem(feedPrefix, elem)
        case other => other
    val transform = new RuleTransformer(rule)
    transform(rssElem).asInstanceOf[Elem]

  def copyItunesImageElementsToItems( rssElem : Elem ) : Elem =
    val mbItunesFeedImage =
      val queryResult = (rssElem \ "channel").map( _ \ "image").filter( _.asInstanceOf[Elem].prefix == "itunes" )
      if queryResult.nonEmpty then Some(queryResult.head) else None
    val mbRegularFeedImage =
      val queryResult = (rssElem \ "channel").map( _ \ "image").filter( _.asInstanceOf[Elem].prefix == null )
      if queryResult.nonEmpty then Some(queryResult.head) else None
    val mbFeedImage = mbItunesFeedImage orElse mbRegularFeedImage.map: regularImageElem =>
      val url = (regularImageElem \ "url").head.text.trim
      Element.Itunes.Image(href=url).toElem
    mbFeedImage.fold(rssElem): feedImage =>
      val rule = new RewriteRule:
        override def transform(n: Node): Seq[Node] = n match
          case elem: Elem if elem.label == "item" => elem.copy( child = elem.child :+ feedImage.asInstanceOf[Elem])
          case other => other
      val transform = new RuleTransformer(rule)
      transform(rssElem).asInstanceOf[Elem]

  def embellishFeed( rssElem : Elem ) : Elem =
    (prependFeedTitleToItemTitles andThen copyItunesImageElementsToItems)(rssElem)

  def bestAttemptEmbellishFeed( anyTopElem : Elem ) : Elem =
    val rssElem : Option[Elem] = anyTopElem.label match 
      case "rss" => Some(anyTopElem)
      case "feed" if scopeContains( null, "http://www.w3.org/2005/Atom", anyTopElem.scope ) => Some( rssElemFromAtomFeedElem(anyTopElem) )
      case _ => None
    rssElem.fold( anyTopElem )( embellishFeed )

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
   
  val subscribedPodcatsMetaSources = immutable.Seq(
    MetaSource.OPML(URL("https://www.inoreader.com/reader/subscriptions/export/user/1005956602/label/Podcasts"), eachFeedTransformer = bestAttemptEmbellishFeed),
    MetaSource.OPML(URL("https://www.inoreader.com/reader/subscriptions/export/user/1005956602/label/Podcasts+HF"), eachFeedTransformer = bestAttemptEmbellishFeed),
  )

  val AllBlogsFeed = new MergedFeed.Default(baseName = "all-blogs", sourceUrls = allBlogs, itemLimit = 25):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, all blogs"
    override def description(rootElems: immutable.Seq[Elem]): String = "Collects posts to all blogs (including the main interfluidity.com, as well as drafts.interfluidity.com and tech.interfluiduty.com) by Steve Randy Waldman"

  val AllBlogsAndMicroblogsFeed = new MergedFeed.Default(baseName = "all-blogs-and-microblogs", sourceUrls = allBlogsAndMicroblogs, itemLimit = 100):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, all blogs and microblogs"
    override def description(rootElems: immutable.Seq[Elem]): String = "Collects posts to all blogs and microblogs by Steve Randy Waldman (interfludity), as well as posts to microblogs that syndicate by RSS."

  val EverythingFeed = new MergedFeed.Default(baseName = "everything", sourceUrls = everything, itemLimit = 100):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, everything"
    override def description(rootElems: immutable.Seq[Elem]): String = "Tracks posts to all blogs and microblogs, as well as other activity, by Steve Randy Waldman (interfludity), as well as posts to microblogs that syndicate by RSS."

  val SubscribedPodcastsFeed = new MergedFeed.Default(baseName = "subscribed-podcasts", metaSources = subscribedPodcatsMetaSources, itemLimit = 100, refreshSeconds = 1800 /*, outputTransformer = onlyMostRecentNPRNewsInOutput */):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, subscribed podcasts"
    override def description(rootElems: immutable.Seq[Elem]): String = "Tracks the podcasts to Steve Randy Waldman is subscribed by RSS, to avoid siloing subscriptions in some single app."

  override val appConfig: AppConfig = AppConfig(
    serverUrl = Abs("https://www.interfluidity.com/"),
    proxiedPort = Some(8123),
    appPathServerRooted = Rooted("/unify-rss"),
    mergedFeeds = immutable.Set(AllBlogsFeed, AllBlogsAndMicroblogsFeed, EverythingFeed, SubscribedPodcastsFeed),
    verbose = true
  )
}
