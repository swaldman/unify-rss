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

### library + script

_unify_rss_ is most easily run in [library + script](https://tech.interfluidity.com/2023/11/14/library--script-vs-application--config-file/index.html) style.

You'll [find the library on Maven Central](https://central.sonatype.com/artifact/com.mchange/unify-rss_3).

In a [scala-cli](https://scala-cli.virtuslab.org/) script, just configure your application just by building
either a `StaticGenConfig` or `DaemonConfig` object.

In either case, the heart of your definition will be a `Set` of `MergedFeed` objects.
Each merged feed can be constituted of any number of individual RSS feeds, or sources of RSS feeds
like OPML feeds.

Once you have defined your config object, your script simply runs a method of the `ScriptEntry`.

And that's it!

#### serve feeds as a daemon

You can periodically merge feeds in memory and have a daemon serve them. Just call `ScriptEntry.startupDaemon( daemonConfig : DaemonCofig )`.

You'll usually deploy your script with _systemd_, a `Type=simple` daemon.

#### static feed generation

You can generate merged feeds as static files, to be served as static files. Just call `ScriptEntry.performStaticGen( sgc: StaticGenConfig )`.

You'll usually deploy your script with _systemd_, a `Type=simple` service that will run, regenerate your feeds, then simply die. 
Use a _systemd_ timer to periodically rerun and regenerate your feeds.

### examples

You can find a (rather complicated, alas) example
[script](https://github.com/swaldman/unify-rss-interfluidity/blob/main/unify-rss-interfluidity) with
_systemd_ [unit](https://github.com/swaldman/unify-rss-interfluidity/blob/main/unify-rss.service) and
[timer](https://github.com/swaldman/unify-rss-interfluidity/blob/main/unify-rss.timer)
files [here](https://github.com/swaldman/unify-rss-interfluidity).

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

