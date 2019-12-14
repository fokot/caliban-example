package com.fokot

import caliban.Http4sAdapter
import cats.effect.Blocker
import com.fokot.exceptions.AuthException
import com.fokot.graphql.{Env, GQL}
import com.fokot.services.JWT.JWTConfig
import com.fokot.services.model.Token
import com.fokot.services.{Auth, FakeStorage, JWT, Storage}
import com.fokot.utils.WC
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{EntityEncoder, HttpRoutes, Request, Response, Status}
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
      _storage = new FakeStorage()
      provideEnvSimple = provideEnvironment(_storage, ???) _
      _ <- BlazeServerBuilder[RIO[ZEnv, *]]
        .bindHttp(8000, "localhost")
        .withHttpApp(
          HttpRoutes
            .of[RIO[ZEnv, *]] {
              case req@POST -> Root / "api" / "graphql" =>
                Http4sAdapter.executeRequest(GQL.interpreter, provideEnvSimple(extractToken(req)))(req)
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

  def extractToken(request: Request[RIO[ZEnv, *]]): ZIO[WC[JWTConfig] with Clock, AuthException, Token] =
    ZIO
      .fromEither(request.headers.get(Authorization).map(_.value).toRight("No `Authorization` header"))
      .flatMap(JWT.removeBearerAndDecodeTokenPayload)
      .mapError(AuthException)


  def provideEnvironment(_storage: Storage.Service[Console], jwtConfig: JWTConfig)(token: RIO[WC[JWTConfig] with Clock, Token]): ZEnv => Env =
    (env: ZEnv) =>
      new Storage[Console] with Auth[WC[JWTConfig] with Clock] with WC[JWTConfig] with Console {
        override def storage: Storage.Service[Console] = _storage
        override def auth: Auth.Service[WC[JWTConfig] with Clock] = Auth.SimpleService(token)
        override def config: JWTConfig = jwtConfig
        override val console: Console.Service[Any] = env.console
      }

}
