import com.mchange.unifyrss.*

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

  val AllBlogsFeed = new MergedFeed.Default(sourceUrls = allBlogs, baseName = "all-blogs", itemLimit = 25):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, all blogs"
    override def description(rootElems: immutable.Seq[Elem]): String = "Collects posts to all blogs (including the main interfluidity.com, as well as drafts.interfluidity.com and tech.interfluiduty.com) by Steve Randy Waldman"

  val AllBlogsAndMicroblogsFeed = new MergedFeed.Default(sourceUrls = allBlogsAndMicroblogs, baseName = "all-blogs-and-microblogs", itemLimit = 100):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, all blogs and microblogs"
    override def description(rootElems: immutable.Seq[Elem]): String = "Collects posts to all blogs and microblogs by Steve Randy Waldman (interfludity), as well as posts to microblogs that syndicate by RSS."

  val EverythingFeed = new MergedFeed.Default(sourceUrls = everything, baseName = "everything", itemLimit = 100):
    override def title(rootElems: immutable.Seq[Elem]): String = "interfluidity, everything"
    override def description(rootElems: immutable.Seq[Elem]): String = "Tracks posts to all blogs and microblogs, as well as other activity, by Steve Randy Waldman (interfludity), as well as posts to microblogs that syndicate by RSS."

  override val appConfig: AppConfig = AppConfig(
    serverUrl = Abs("https://www.interfluidity.com/"),
    proxiedPort = Some(8123),
    appPathServerRooted = Rooted("/unify-rss"),
    mergedFeeds = immutable.Set(AllBlogsFeed, AllBlogsAndMicroblogsFeed, EverythingFeed),
    verbose = true
  )
}
