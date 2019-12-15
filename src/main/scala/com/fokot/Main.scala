package com.fokot

import caliban.Http4sAdapter
import cats.effect.Blocker
import com.fokot.exceptions.AuthException
import com.fokot.graphql.{Env, GQL}
import com.fokot.services.JWT.JWTConfig
import com.fokot.services.model.User
import com.fokot.services.{Auth, FakeStorage, JWT, Storage}
import com.fokot.utils.{Config, WC}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{EntityEncoder, HttpRoutes, Request, Response, Status}
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.error.ConfigReaderException
import zio._
import zio.blocking.{Blocking, effectBlocking}
import zio.clock.Clock
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
      conf <- ZIO.fromEither(ConfigSource.default.load[Config]).mapError(ConfigReaderException.apply)
      _storage = new FakeStorage()
      _ <- BlazeServerBuilder[RIO[ZEnv, *]]
        .bindHttp(8000, "localhost")
        .withHttpApp(
          HttpRoutes
            .of[RIO[ZEnv, *]] {
              case req@POST -> Root / "api" / "graphql" =>
                Http4sAdapter.executeRequest(GQL.interpreter, provideEnv(_storage, conf, extractToken(req)))(req)
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

  def extractToken(request: Request[RIO[ZEnv, *]]): ZIO[Clock with WC[JWTConfig], AuthException, User] =
    ZIO
      .fromEither(request.headers.get(Authorization).map(_.value).toRight("No `Authorization` header"))
      .flatMap(JWT.removeBearerAndDecodeTokenPayload)
      .mapError(AuthException)


  def provideEnv(_storage: Storage.Service[Console], conf: Config, token: RIO[Clock with WC[JWTConfig], User]): ZEnv => Env =
    (env: ZEnv) =>
      new Storage with Auth with Console with WC[Config] with Clock {
        override def auth: Auth.Service[Any] = Auth.SimpleService(token.provide(new Clock with WC[JWTConfig] {
          override val clock: Clock.Service[Any] = env.clock
          override def config: JWTConfig = conf.jwt
        }))
        override val console: Console.Service[Any] = env.console
        override val clock: Clock.Service[Any] = env.clock
        override def config: Config = conf
        override def storage: Storage.Service[Console] = _storage
      }

}
