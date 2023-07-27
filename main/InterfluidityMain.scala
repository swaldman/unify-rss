import com.mchange.unifyrss.*

import java.net.URL
import scala.collection.*
import unstatic.UrlPath.*
import scala.xml.Elem

object InterfluidityMain extends AbstractMain {

  val allBlogs = immutable.Seq(
    new URL("https://drafts.interfluidity.com/feed/index.rss"),
    new URL("https://tech.interfluidity.com/feed/index.rss"),
    new URL("https://www.interfluidity.com/feed"),
  )

  val allBlogsAndMicroblogs = allBlogs ++ immutable.Seq(
    new URL("https://econtwitter.net/@interfluidity.rss"),
    new URL("https://fosstodon.org/@interfluidity.rss")
  )

  val AllBlogsFeed = new MergedFeed.Default(sourceUrls = allBlogs, baseName = "all-blogs", itemLimit = 25):
    override def title(rootElems: immutable.Seq[Elem]): String = "All interfluidity blog posts"
    override def description(rootElems: immutable.Seq[Elem]): String = "Collects posts to all blogs (including the main interfluidity.com, as well as drafts.interfluidity.com and tech.interfluiduty.com) by Steve Randy Waldman"

  val AllBlogsAndMicroblogsFeed = new MergedFeed.Default(sourceUrls = allBlogsAndMicroblogs, baseName = "all-blogs-and-microblogs", itemLimit = 100):
    override def title(rootElems: immutable.Seq[Elem]): String = "All interfluidity blog posts and microblog entries"
    override def description(rootElems: immutable.Seq[Elem]): String = "Collects posts to all blogs by Steve Randy Waldman (interfludity), as well as posts to microblogs that syndicate by RSS."

  override val appConfig: AppConfig = AppConfig(
    serverUrl = Abs("https://www.interfluidity.com/"),
    proxiedPort = Some(8123),
    appPathServerRooted = Rooted("/rss-extra"),
    mergedFeeds = immutable.Set(AllBlogsFeed, AllBlogsAndMicroblogsFeed),
    verbose = false
  )
}
