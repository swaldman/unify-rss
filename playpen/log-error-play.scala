//> using scala "3.3.0"
//> using dep "dev.zio::zio:2.0.15"

import zio.*

object App extends ZIOAppDefault:
  override def run = ZIO.die(new Throwable("Oops!")).logError




