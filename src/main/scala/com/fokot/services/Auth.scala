package com.fokot.services

import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import cats.syntax.either._
import cats.syntax.option._
import com.fokot.services.model.{Role, Token}
import com.fokot.utils.WC
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{JwtCirce, JwtClaim}
import zio.clock.Clock
import zio.{IO, RIO, URIO, ZIO}
import scala.util.{Failure, Success}

trait Auth[R] {
  def auth: Auth.Service[R]
}

object Auth {

  trait Service[R] {
    def token: RIO[R, Token]
  }

  case class SimpleService[R](token: RIO[R, Token]) extends Auth.Service[R]
}

/**
 * Utility methods to encode / decode JWT token
 */
object JWT {

  implicit val decodeRole: Decoder[Role] = io.circe.generic.extras.semiauto.deriveEnumerationDecoder
  implicit val encodeRole: Encoder[Role] = io.circe.generic.extras.semiauto.deriveEnumerationEncoder
  implicit val decodeToken: Decoder[Token] = io.circe.generic.semiauto.deriveDecoder
  implicit val encodeToken: Encoder[Token] = io.circe.generic.semiauto.deriveEncoder

  case class JWTConfig(key: String, hmacAlgorithm: JwtHmacAlgorithm, expirationSeconds: Long)

  val getClock = ZIO.accessM[Clock](_.clock.currentDateTime).map(dt => java.time.Clock.fixed(dt.toInstant, ZoneOffset.UTC))

  private def removeBearer(token: String): IO[String, String] =
    IO.succeed(token).filterOrFail(_.startsWith("Bearer "))("Token does not start with 'Bearer '").map(_.substring(7))

  private def decodeTokenToClaim(token: String): ZIO[WC[JWTConfig] with Clock, String, JwtClaim] = {
    for {
      config <- ZIO.access[WC[JWTConfig]](_.config)
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

  def decodeTokenPayload(token: String): ZIO[WC[JWTConfig] with Clock, String, Token] =
    for {
      jwtClaim <- decodeTokenToClaim(token)
      json <- ZIO.fromEither(parse(jwtClaim.content).leftMap(_.message))
      token <- ZIO.fromEither(json.as[Token].leftMap(_ => "Invalid token."))
    } yield token

  def removeBearerAndDecodeTokenPayload(tokenWithBearer: String): ZIO[WC[JWTConfig] with Clock, String, Token] =
    for {
      rawToken <- removeBearer(tokenWithBearer)
      token <- decodeTokenPayload(rawToken)
    } yield token

  def encodeToken(token: Token): URIO[WC[JWTConfig] with Clock, String] = {
    for {
      config <- ZIO.access[WC[JWTConfig]](_.config)
      seconds <- ZIO.accessM[Clock](_.clock.currentTime(TimeUnit.SECONDS))
    } yield JwtCirce.encode(
      JwtClaim(issuedAt = seconds.some, expiration = (seconds + config.expirationSeconds).some, content = token.asJson.noSpaces),
      config.key,
      config.hmacAlgorithm
    )
  }
}
