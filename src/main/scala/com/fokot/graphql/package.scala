package com.fokot

import com.fokot.services.auth.Auth
import com.fokot.services.storage.Storage
import com.fokot.config.AppConfig
import zio.{Has, RIO}
import zio.clock.Clock
import zio.console.Console
import zquery.ZQuery

/**
 * Object model for GraphQL
 */
package object graphql {

  // environment used to resolve the schema
  type Env = Clock with Console with Auth with Storage with Has[AppConfig]

  // read value lazily
  type Z[A] = RIO[Env, A]

  // read value lazily but batch requests
  type Q[A] = ZQuery[Env, Throwable, A]

  case class BookInput(
    id: Long,
    name: String,
    publishedYear: Int,
    authorId: Long
  )

  case class Book(
    id: Long,
    name: String,
    publishedYear: Int,
    author: Q[Author]
  )

  case class MyBooks(
    total: Z[Int],
    borrowedNow: Z[Int],
    books: Z[List[Book]]
  )

  case class Author(
    id: Long,
    firstName: String,
    lastName: String,
    books: Z[List[Book]]
  )

  case class BookId(id: Long)

  case class Login(login: String, password: String)
  case class Logged(token: String)

  case class Queries(
    books: Z[List[Book]],
    book: BookId => Z[Book],
    myBooks: Z[MyBooks],
  )

  case class Mutations(
    login: Login => Z[Logged],
    createBook: BookInput => Z[Book],
    updateBook: BookInput => Z[Book],
    deleteBook: BookId => Z[Unit],
    borrowBook: BookId => Z[Book],
  )
}
