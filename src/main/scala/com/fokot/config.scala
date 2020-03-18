package com.fokot

import com.fokot.services.JWT.JWTConfig
import pureconfig.error.ConfigReaderException
import pureconfig.{ConfigReader, ConfigSource}
import zio.{Has, ZIO, ZLayer}

object config {

  type Config[A] = Has[A]

  case class AppConfig(jwt: JWTConfig)

  object AppConfig {
    implicit val reader: ConfigReader[AppConfig] =
      pureconfig.generic.semiauto.deriveReader[AppConfig]
  }

  val loadConfig: ZIO[Any, ConfigReaderException[Nothing], AppConfig] =
    ZIO.fromEither(ConfigSource.default.load[AppConfig]).mapError(ConfigReaderException.apply)

  val appConfig: ZLayer[Any, ConfigReaderException[Nothing], Config[AppConfig]] =
    ZLayer.fromEffect(loadConfig)

  val jwtConfig: ZLayer[AppConfig, Nothing, Config[JWTConfig]] = ZLayer.fromFunction(_.jwt)
}
