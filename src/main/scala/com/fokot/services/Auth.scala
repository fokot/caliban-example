package com.fokot.services

import model.Token
import zio.Task

trait Auth {
  def auth: Auth.Service
}

object Auth {
  trait Service {
    def token: Task[Token]
  }
}

case class AuthServiceFromToken(token: Task[Token]) extends Auth.Service
