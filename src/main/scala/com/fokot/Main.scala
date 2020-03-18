package com.fokot

import caliban.CalibanError.ExecutionError
import caliban.{GraphQLInterpreter, GraphQLRequest, GraphQLResponse}
import cats.data.Kleisli
import com.fokot.config.AppConfig
import com.fokot.exceptions.AuthException
import com.fokot.graphql.{Env, GQL}
import com.fokot.services.JWT.JWTConfig
import com.fokot.services.model.User
import com.fokot.services.{JWT, auth, storage}
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s._
import zio._
import zio.blocking.effectBlocking
import zio.clock.Clock
import zio.console.putStrLn
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

  import caliban.Value._

  //  implicit val graphQLResponseEncoder: Encoder[GraphQLResponse[Any]] = Encoder
  //    .instance[GraphQLResponse[Any]] {
  //      case GraphQLResponse(data, Nil, None) => Json.obj("data" -> data.asJson)
  //      case GraphQLResponse(data, Nil, Some(extensions)) =>
  //        Json.obj("data" -> data.asJson, "extensions" -> extensions.asInstanceOf[ResponseValue].asJson)
  //      case GraphQLResponse(data, errors, None) =>
  //        Json.obj("data" -> data.asJson, "errors" -> Json.fromValues(errors.map(handleError)))
  //      case GraphQLResponse(data, errors, Some(extensions)) =>
  //        Json.obj(
  //          "data"       -> data.asJson,
  //          "errors"     -> Json.fromValues(errors.map(handleError)),
  //          "extensions" -> extensions.asInstanceOf[ResponseValue].asJson
  //          )
  //    }

  def addErrorCause(err: Any): Any = err match {
    case e: ExecutionError if e.innerThrowable.isDefined =>
      e.copy(msg = s"${e.msg} (${e.innerThrowable.map(t => t.getClass.getSimpleName).getOrElse("")})")
    case e => e
  }

  def executeRequest[R0, R, E](
    interpreter: GraphQLInterpreter[R, E],
    provideEnv: R0 => R,
  ): HttpApp[RIO[R0, *]] =
    Kleisli { req =>
      object dsl extends Http4sDsl[RIO[R0, *]]
      import dsl._
      for {
        query <- req.attemptAs[GraphQLRequest].value.absolve
        result <-
          interpreter.execute(query.query, query.operationName, query.variables.getOrElse(Map()), false)
            .foldCause(
              cause => GraphQLResponse(NullValue, cause.defects.map(addErrorCause)).asJson,
              res => res.copy(errors = res.errors.map(addErrorCause)).asJson)
            .provideSome[R0](provideEnv)
        response <- Ok(result)
      } yield response
    }

  override def run(args: List[String]): URIO[ZEnv, Int] =
    (for {
      _appConfig <- config.loadConfig
      _interpreter <- GQL.interpreter
      _ <- BlazeServerBuilder[RIO[ZEnv, *]]
        .bindHttp(8000, "localhost")
        .withHttpApp(
          HttpRoutes
            .of[RIO[ZEnv, *]] {
              case req@POST -> Root / "api" / "graphql" =>
                //                Http4sAdapter.executeRequest(_interpreter, provideEnv(extractToken(req), _appConfig))(req)
                executeRequest(_interpreter, provideEnv(extractToken(req), _appConfig))(req)
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

  def extractToken(request: Request[RIO[ZEnv, *]]): ZIO[Clock with Has[JWTConfig], AuthException, User] =
    ZIO
      .fromEither(request.headers.get(Authorization).map(_.value).toRight("No `Authorization` header"))
      .flatMap(JWT.removeBearerAndDecodeTokenPayload)
      .mapError(AuthException)

  def provideEnv(token: RIO[Clock with Has[JWTConfig], User], appConfig: AppConfig): ZEnv => Env =
    (env: ZEnv) =>
      env ++ storage.fakeStorage ++ Has(appConfig) ++
        Has(auth.SimpleService(token.provide(env ++ Has(appConfig.jwt))))
}
