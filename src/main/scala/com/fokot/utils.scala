package com.fokot

import com.fokot.services.JWT.JWTConfig
import pureconfig.ConfigReader

object utils {

  // WC means WithConfig :-)
  trait WC[A] {
    def config: A
  }

  case class Config(jwt: JWTConfig)

  object Config {
    implicit val reader: ConfigReader[Config] =
      pureconfig.generic.semiauto.deriveReader[Config]
  }
}
