package com.fokot

import com.fokot.services.Storage
import zio.RIO
import zio.console.Console
import zquery.ZQuery

/**
 * Object model for GraphQL
 */
package object graphql {

  // environment used to resolve the schema
  type Env = Storage[Console] with Console
  // read value lazily
  type Z[A] = RIO[Env, A]
  // read value lazily but batch requests
  type Q[A] = ZQuery[Env, Throwable, A]

  case class Book(
    id: Long,
    name: String,
    publishedYear: Int,
    author: Q[Author]
  )

  case class Author(
    id: Long,
    firstName: String,
    lastName: String,
    books: Z[List[Book]]
  )

  case class Queries(
    books: Z[List[Book]]
  )
}
