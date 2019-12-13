package com.fokot

import caliban.Http4sAdapter
import cats.effect.Blocker
import com.fokot.graphql.{Env, GQL}
import com.fokot.services.{FakeStorage, Storage}
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{EntityEncoder, HttpRoutes, Response, Status}
import zio._
import zio.blocking.{Blocking, effectBlocking}
import zio.console.{Console, putStrLn}
import zio.interop.catz._

import scala.io.Source

object Main extends CatsApp {

  object dsl extends Http4sDsl[RIO[ZEnv, *]]

  import dsl._

  def staticResource(path: String): RIO[ZEnv, Response[RIO[ZEnv, *]]] =
    effectBlocking(Source.fromResource(path).getLines.mkString("\n")).map(
      content =>
        Response[RIO[ZEnv, *]](Status.Ok, body = EntityEncoder[RIO[ZEnv, *], String](EntityEncoder.stringEncoder).toEntity(content).body)
    )


  override def run(args: List[String]): URIO[ZEnv, Int] =
    (for {
      //      appConfig <- Configuration.Live.config.load // TODO add all configs to the Config case class and load them together
      blocker <- ZIO
        .accessM[Blocking](_.blocking.blockingExecutor.map(_.asEC))
        .map(Blocker.liftExecutionContext)
      _storage = new FakeStorage()
      _ <- BlazeServerBuilder[RIO[ZEnv, *]]
        .bindHttp(8000, "localhost")
        .withHttpApp(
          HttpRoutes
            .of[RIO[ZEnv, *]] {
              case req@POST -> Root / "api" / "graphql" => Http4sAdapter.executeRequest(GQL.interpreter, provideEnvironment(_storage))(req)
              case GET -> Root / "graphiql" =>
                staticResource("static/graphiql.html")
              case GET -> Root / "login.html" =>
                staticResource("static/login.html")
            }
            .orNotFound
        )
        .resource
        .toManaged
        .useForever

    } yield 0)
      .catchAll(err => putStrLn(err.toString).as(1))


  def provideEnvironment(_storage: Storage.Service[Console]): ZEnv => Env =
    (env: ZEnv) =>
      new Storage[Console] with Console {

        override def storage: Storage.Service[Console] = _storage

        override val console: Console.Service[Any] = env.console
        //        override def auth: Auth.Service = AuthServiceFromToken(token.provide(wc))
      }

}
