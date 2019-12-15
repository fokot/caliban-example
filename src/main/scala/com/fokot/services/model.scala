package com.fokot.services

/**
 * Flat model with ids as it is in database
 */
object model {

  sealed trait Role
  object Role {
    final case object Editor extends Role
    final case object Viewer extends Role
  }

  case class User(
    login: String,
    role: Role
  )

  case class Author(
    id: Long,
    firstName: String,
    lastName: String
  )

  case class Book(
    id: Long,
    name: String,
    publishedYear: Int,
    authorId: Long
  )

}
