# unify-rss

I'm dividing my writing output among a number of blogs
and microblogging sites these days. I wanted to offer
RSS feeds that would let you subscribe to all of these at
once, in the unlikely and rather discreditable circumstance
that you like to read what I like.

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
Now `scala-cli` takes care of that nicely. So...

The main class of this project is called [`AbstractMain`](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/AbstractMain.scala).
Before you can run the server, create an object that extends
`AbstractMain` and overrides (usually with a `val`) the abstract `appConfig` method.
[`AppConfig`](https://github.com/swaldman/unify-rss/blob/main/main/com/mchange/unifyrss/config.scala) is a `case class`, the heart of which is a set of
`MergedFeed` objects, in which you define the feeds you wish to unify into one.

### examples

Check out the [_interfluidity_](https://github.com/swaldman/unify-rss/tree/interfluidity)
branch, and the object [`InterfluidityMain`](https://github.com/swaldman/unify-rss/blob/interfluidity/main/InterfluidityMain.scala) to see how this works.
You'll see exactly how feeds are configured there.

In the _interfluidity_ branch you can also see the `systemd` service file
by which I am currently running this service. (There are shell-scripts as well,
but I am not using them.)

### shortcomings

RSS feeds are supposed to link to the site that produces them, but since these feeds
are generated from multiple sites, we make up a "stub site" link bank to this service.
I have not yet implemented the serving of those "stub sites" yet though.

---

Let me know what you think!

