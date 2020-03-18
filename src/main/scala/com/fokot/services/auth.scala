package com.fokot.services

import java.time
import java.time.{DateTimeException, ZoneOffset}
import java.util.concurrent.TimeUnit

import cats.syntax.either._
import cats.syntax.option._
import com.fokot.services.model.{Role, User}
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import zio.clock.Clock
import zio.{Has, IO, RIO, Task, URIO, ZIO}

import scala.util.{Failure, Success}

object auth {

  type Auth = Has[AuthService[Any]]

  trait AuthService[R] {
    def currentUser: RIO[R, User]
  }

  def currentUser: RIO[Auth, User] = ZIO.accessM[Auth](
    _.get.currentUser
  )

  case class SimpleService(currentUser: Task[User]) extends AuthService[Any]
}

/**
 * Utility methods to encode / decode JWT token
 */
object JWT {

  implicit val RoleDecoder: Decoder[Role] = io.circe.generic.extras.semiauto.deriveEnumerationDecoder
  implicit val RoleEncoder: Encoder[Role] = io.circe.generic.extras.semiauto.deriveEnumerationEncoder
  implicit val TokenDecoder: Decoder[User] = io.circe.generic.semiauto.deriveDecoder
  implicit val TokenEncoder: Encoder[User] = io.circe.generic.semiauto.deriveEncoder

  case class JWTConfig(key: String, hmacAlgorithm: JwtHmacAlgorithm, expirationSeconds: Long)

  object JWTConfig {
    implicit lazy val myHmacAlgorithmReader: ConfigReader[JwtHmacAlgorithm] =
      ConfigReader[String].emap(algName =>
        JwtAlgorithm.allHmac()
          .find(alg => alg.name == algName || alg.fullName == algName)
          .toRight(CannotConvert(algName, "JwtHmacAlgorithm", "no such algorithm" ))
      )
    implicit val reader: ConfigReader[JWTConfig] =
      pureconfig.generic.semiauto.deriveReader[JWTConfig]
  }

  val getClock: ZIO[Clock, String, time.Clock] = ZIO.accessM[Clock](_.get.currentDateTime).map(dt => java.time.Clock.fixed(dt.toInstant, ZoneOffset.UTC)).mapError(e => e.getMessage)

  private def removeBearer(token: String): IO[String, String] =
    IO.succeed(token).filterOrFail(_.startsWith("Bearer "))("Token does not start with 'Bearer '").map(_.substring(7))

  private def decodeTokenToClaim(token: String): ZIO[Has[JWTConfig] with Clock, String, JwtClaim] = {
    for {
      config <- ZIO.access[Has[JWTConfig]](_.get)
      clock <- getClock
      res <- JwtCirce.decode(token, config.key, Seq(config.hmacAlgorithm)) match {
        case Success(s) if s.isValid(clock) => // check validity according to available Clock instance
          ZIO.effectTotal(s)
        case Success(s) =>
          ZIO.fail(s"Expired token: $token, issuedAt: ${s.issuedAt}, expiration: ${s.expiration}")
        case Failure(_) =>
          ZIO.fail(s"Token cannot be decoded: $token")
      }
    } yield res
  }

  def decodeTokenPayload(token: String): ZIO[Has[JWTConfig] with Clock, String, User] =
    for {
      jwtClaim <- decodeTokenToClaim(token)
      json <- ZIO.fromEither(parse(jwtClaim.content).leftMap(_.message))
      token <- ZIO.fromEither(json.as[User].leftMap(_ => "Invalid token."))
    } yield token

  def removeBearerAndDecodeTokenPayload(tokenWithBearer: String): ZIO[Has[JWTConfig] with Clock, String, User] =
    for {
      rawToken <- removeBearer(tokenWithBearer)
      token <- decodeTokenPayload(rawToken)
    } yield token

  def encodeToken(token: User): URIO[Has[JWTConfig] with Clock, String] = {
    for {
      config <- ZIO.access[Has[JWTConfig]](_.get)
      seconds <- ZIO.accessM[Clock](_.get.currentTime(TimeUnit.SECONDS))
    } yield JwtCirce.encode(
      JwtClaim(issuedAt = seconds.some, expiration = (seconds + config.expirationSeconds).some, content = token.asJson.noSpaces),
      config.key,
      config.hmacAlgorithm
    )
  }
}
