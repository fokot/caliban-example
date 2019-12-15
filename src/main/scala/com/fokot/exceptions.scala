package com.fokot

object exceptions {

  // TODO if we don't want our exceptions don't need to inherit from java.lang.Exception
  // in that case we can't user Task alias tough
  case class AuthException(message: String) extends Exception

  case class InputDataException(message: String) extends Exception
}
