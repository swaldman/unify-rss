import com.mchange.unifyrss.*

import java.net.URL
import scala.collection.*
import unstatic.UrlPath.*
import scala.xml.Elem

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

  val subscribedPodcatsMetaSources = immutable.Seq(
    MetaSource.OPML(URL("https://www.inoreader.com/reader/subscriptions/export/user/1005956602/label/Podcasts")),
    MetaSource.OPML(URL("https://www.inoreader.com/reader/subscriptions/export/user/1005956602/label/Podcasts+HF")),
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

  val SubscribedPodcastsFeed = new MergedFeed.Default(baseName = "subscribed-podcasts", metaSources = subscribedPodcatsMetaSources, itemLimit = 100, refreshSeconds = 1800):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, subscribed podcasta"
    override def description(rootElems: immutable.Seq[Elem]): String = "Tracks the podcasts to Steve Randy Waldman is subscribed by RSS, to avoid siloing subscriptions in some single app."

  override val appConfig: AppConfig = AppConfig(
    serverUrl = Abs("https://www.interfluidity.com/"),
    proxiedPort = Some(8123),
    appPathServerRooted = Rooted("/unify-rss"),
    mergedFeeds = immutable.Set(AllBlogsFeed, AllBlogsAndMicroblogsFeed, EverythingFeed, SubscribedPodcastsFeed),
    verbose = true
  )
}
