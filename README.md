# unify-rss

I'm dividing my writing output among a number of blogs
and microblogging sites these days. I wanted to offer
RSS feeds that would let you subscribe to all of these at
once, in the unlikely and rather discreditable circumstance
that you like to read what I write. So...

This application let's you configure any number of "synthetic" RSS feeds, each one built
by merging any number of source feeds.

You can specify source feeds directly and/or subscribe to
[OPML feeds](https://indieweb.org/OPML), which the application will follow as the source list changes
dynamically over time.

### buildless

I thought this would be a very casual project, and it has been.
It's scala, but it's written in an almost "buildless" style.
The directory [`main`](https://github.com/swaldman/unify-rss/tree/main/main)
is just a package root, with a top-level file called `scala-cli-build.scala`
that sets options and brings in dependencies.

The idea is you just type

```bash
$ scala-cli main
```

and it runs. But not quite.

### configless

I'm a big fan of just using Scala code (usually case classes)
for config.

Past projects (See [`audiofluidity`](https://github.com/swaldman/audiofluidity-rss))
had handrolled logic to compile changed config scala files on execution.
Now `scala-cli` takes care of that nicely. So. 

We define an abstract class with a main functon ([two](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/AbstractStaticGenMain.scala)
[actually](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/AbstractDaemonMain.scala)!), and with an abstract `appConfig` method
that wants an [AppConfig](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/config.scala) object.

You define a concrete `object` that extends one of these abstract configs, and overrides the abstract `appConfig` method (usually with a `val`).

[`AppConfig`](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/config.scala) is a `case class`, the heart of which is a set of
`MergedFeed` objects, in which you define the feeds you wish to unify into one.

#### serve feeds as a daemon

Originally, this application unified feeds and re-served them as a continually running daemon. You can still run it that way!
Just extend abstract main class [`AbstractDaemonMain`](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/AbstractDaemonMain.scala),
override the `appConfig` method as described above, then run your application as a long-running service. 

[Here](https://github.com/swaldman/unify-rss/blob/interfluidity/unify-rss.service-as-daemon)
for example is a `systemd` unit file I used to use for that purpose.

#### static feed generation

However, if you are using `systemd` anyway, an alternative approach is to generate your feeds into static files, and use a `systemd` timer to periodically
refresh them. This is more economical, as you don't need to occupy a server with a continually running JVM server process, which may have a big memory
footprint.

Extend abstract main class [`AbstractStaticGenMain`](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/AbstractStaticGenMain.scala),
override `appConfig` and also `appStaticDir` method, which will be the directory feeds get generated into.

Define a `systemd` service that runs the app just once, and a `systemd` timer that periodically refreshes it.

Make sure that the `appStaticDir` you specify exists, and is writable by the user your application runs as. Configure your webserver
to serve those files at the URL you desire.

> [!NOTE]
> Some config items, like `proxiedPort` and `refreshSeconds` will be ignored if you are generating static files. Use `systemd` or `cron` to refresh feeds by rerunning the app.


### examples

Check out the [_interfluidity_](https://github.com/swaldman/unify-rss/tree/interfluidity)
branch, and the object [`InterfluidityMain`](https://github.com/swaldman/unify-rss/blob/interfluidity/main/InterfluidityMain.scala) to see how this works.
You'll see exactly how feeds are configured there.

In the _interfluidity_ branch you can also see the [`systemd` service file](https://github.com/swaldman/unify-rss/blob/interfluidity/unify-rss.service)
by which I am currently running this service, and the [`systemd` timer file](https://github.com/swaldman/unify-rss/blob/interfluidity/unify-rss.timer) 
that reruns it every 30 mins. 

(There are shell-scripts as well, but they are obsolete.)

### shortcomings

* RSS feeds are supposed to link to the site that produces them, but since these feeds
are generated from multiple sites, we make up a "stub site" link back to this service.
I have not yet implemented the serving of those "stub sites" yet though.

### elsewhere

See
* [_Building a resilient RSS feed unifier with ZIO_](https://tech.interfluidity.com/2023/07/29/building-a-resilient-rss-feed-unifier-with-zio/index.html)
* [_Taking control of podcasts via RSS_](https://tech.interfluidity.com/2023/09/17/taking-control-of-podcasts-via-rss/index.html)

---

Let me know what you think!

