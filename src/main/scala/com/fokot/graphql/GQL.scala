package com.fokot.graphql

import caliban.schema.GenericSchema
import caliban.GraphQL.graphQL
import caliban.RootResolver
import com.fokot.services.model
import zio.ZIO
import zquery.{CompletedRequestMap, DataSource, Request, ZQuery}

object GQL {

  case class GetAuthor(id: Long) extends Request[Throwable, model.Author]
  val AuthorDataSource: DataSource.Service[Env, GetAuthor] =
    DataSource.Service("AuthorDataSource") { requests =>
      ZIO
        .accessM[Env](_.storage.getAuthors(requests.toList.map(_.id)))
        .map(
          as =>
            as.foldLeft(CompletedRequestMap.empty) {
              case (map, a) => map.insert(GetAuthor(a.id))(Right(a))
            }
        )
        .orDie
    }

  def getAuthor(id: Long): Q[Author] =
    ZQuery.fromRequestWith(GetAuthor(id))(AuthorDataSource).map(authorToGQL)

  def getBooksForAuthor(id: Long): Z[List[model.Book]] =
    ZIO.accessM[Env](_.storage.getBooksForAuthor(id))

  def bookToGQL(b: model.Book): Book =
    Book(
      b.id,
      b.name,
      b.publishedYear,
      getAuthor(b.authorId)
    )

  def authorToGQL(a: model.Author): Author =
    Author(
      a.id,
      a.firstName,
      a.lastName,
      getBooksForAuthor(a.id).map(_.map(bookToGQL))
    )

  val books: Z[List[Book]] = ZIO
    .accessM[Env](_.storage.getAllBooks().map(_.map(bookToGQL)))

  object schema extends GenericSchema[Env]
  import schema._

  val interpreter = graphQL[Env, Queries, Unit, Unit](
    RootResolver(
      Queries(
        books,
      )
    )
  )

}
