import zio._
import zio.console._
import zio.duration._
import zio.random.Random

object MainRepeat extends zio.App {
  def repeat[R, E, A](n: Int)(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1)
      effect
    else
      effect *> repeat(n - 1)(effect)

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    repeat(10)(putStrLn("mert")).exitCode
}

object MainDot extends zio.App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val m = for {
      fiber <- (putStrLn(".") *> ZIO.sleep(1.second)).forever.fork
      _ <- ZIO.sleep(10 seconds)
      _ <- fiber.interrupt
    } yield ()
    m.exitCode
  }
}

object Service1 extends zio.App {
  trait Service {
    def getInt: Task[Int]
  }

  val ServiceLayer: ULayer[Has[Service]] = ZLayer.succeed {
    new Service {
      def getInt: Task[Int] = ZIO.succeed(1)
    }
  }

  def myFunc: RIO[Has[Service], Int] = ZIO.accessM(_.get.getInt.map(_ * 10))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    myFunc.provideCustomLayer(ServiceLayer).flatMap(s => putStrLn(s.toString)).exitCode

}

object Service2 extends zio.App {
  trait Service {
    def getInt: Task[Int]
  }

  val ServiceLayer: URLayer[Has[Random.Service] with Has[Console.Service], Has[Service]] =
    ZLayer.fromServices[random.Random.Service, console.Console.Service, Service2.Service] {
      (rand, console) =>
        new Service {
          override def getInt: Task[Int] =
            for {
              i <- rand.nextIntBounded(100)
              _ <- console.putStrLn(i.toString)
            } yield i
        }
    }

  def myFunc: RIO[Has[Service], Int] = ZIO.accessM(_.get.getInt.map(_ * 10))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    myFunc.provideCustomLayer(ServiceLayer).flatMap(s => putStrLn(s.toString)).exitCode
}

object Resource1 extends zio.App {
  case class DBConfig(url: String = "some-url")
  val configLayer: ULayer[Has[DBConfig]] = ZLayer.succeed(DBConfig())

  case class ConnectionPool(config: DBConfig) {
    println("new connection poll")
    def use()   = ZIO.succeed(println("using connection pool"))
    def close() = println("closing connection pool")
  }
  type HasConnectionPool = Has[ConnectionPool]
  val poolLayer: ZLayer[Has[DBConfig], Throwable, HasConnectionPool] = {
    def managedConnectionPool(config: DBConfig): ZManaged[Any, Throwable, ConnectionPool] = {
      def createConnectionPool(config: DBConfig): ZIO[Any, Throwable, ConnectionPool] =
        ZIO.effect(ConnectionPool(config))
      val closeConnectionPool: ConnectionPool => ZIO[Any, Nothing, Unit] =
        (cp: ConnectionPool) => ZIO.effect(cp.close()).catchAll(_ => ZIO.unit)

      ZManaged.make(createConnectionPool(config))(closeConnectionPool)
    }
    ZLayer.fromServiceManaged(managedConnectionPool)
  }

  trait Service {
    def getInt: Task[Int]
  }

  val ServiceLayer: ZLayer[Any, Throwable, Has[Service]] =
    configLayer >>> poolLayer >>> ZLayer.fromService[ConnectionPool, Service] { pool =>
      new Service {
        def getInt: Task[Int] =
          pool.use() *> ZIO.succeed(1)
      }
    }

  def myFunc: RIO[Has[Service], Int] = ZIO.accessM(_.get.getInt.map(_ * 10))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    myFunc
      .provideCustomLayer(ServiceLayer)
      .flatMap(s => putStrLn(s.toString))
      .exitCode

}

object Dummy extends zio.App {

  val finalizer =
    UIO.effectTotal(println("Finalizing!"))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      start  <- IO.effectTotal(System.currentTimeMillis())
      fiber1 <- IO.effect(Thread.sleep(5000L)).fork
      fiber2 <- IO.effect(Thread.sleep(5000L)).fork
      fiber = fiber1.zip(fiber2)
      tuple <- fiber.join
      end   <- IO.effectTotal(System.currentTimeMillis())
      _     <- putStrLn((end - start).toString)
    } yield ()).exitCode
}
