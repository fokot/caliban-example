package com.fokot.graphql

import caliban.schema.GenericSchema
import caliban.GraphQL.graphQL
import caliban.RootResolver
import com.fokot.config
import com.fokot.config.AppConfig
import com.fokot.exceptions.AuthException
import com.fokot.services.{JWT, model, storage}
import zio.{Has, ZIO, ZLayer}
import zquery.{DataSource, ZQuery}
import com.fokot.graphql.auth.{isEditor, isViewer}
import com.fokot.graphql.utils.RequestId
import com.fokot.services.model.{Role, User}
import zio.clock.Clock


/**
 * GraphQL resolvers
 */
object GQL {

  val AuthorDataSource: DataSource[Env, RequestId[Long, model.Author]] =
    utils.simpleDataSource[Long, model.Author]("AuthorDataSource", storage.getAuthors, _.id)

  def getAuthor(id: Long): Q[Author] =
    ZQuery.fromRequest(RequestId[Long, model.Author](id))(AuthorDataSource).map(authorToGQL)

  def getBooksForAuthor(id: Long): Z[List[model.Book]] =
    storage.getBooksForAuthor(id)

  def bookToGQL(b: model.Book): Book =
    Book(
      b.id,
      b.name,
      b.publishedYear,
      getAuthor(b.authorId)
    )

  def bookInputFromGQL(b: BookInput): model.Book =
    model.Book(
      b.id,
      b.name,
      b.publishedYear,
      b.authorId
    )

  def authorToGQL(a: model.Author): Author =
    Author(
      a.id,
      a.firstName,
      a.lastName,
      getBooksForAuthor(a.id).map(_.map(bookToGQL))
    )

  val books: Z[List[Book]] =
    isViewer *> storage.getAllBooks().map(_.map(bookToGQL))

  def book(args: BookId): Z[Book] =
    isViewer *> storage.getBook(args.id).map(bookToGQL)

  def myBooks: Z[MyBooks] =
    isViewer >>=
      { u =>
        for {
          res <- storage.getBookCount(u).memoize

        } yield
          MyBooks(
            res.map(_.total),
            res.map(_.borrowedNow),
            storage.getBooksForUser(u).map(_.map(bookToGQL))
            )
      }

  def createBook(args: BookInput): Z[Book] =
    isEditor *> storage.createBook(bookInputFromGQL(args)).map(bookToGQL)

  def updateBook(args: BookInput): Z[Book] =
    isEditor *> storage.updateBook(bookInputFromGQL(args)).map(bookToGQL)

  def deleteBook(args: BookId): Z[Unit] =
    isEditor *> storage.deleteBook(args.id)

  def borrowBook(args: BookId): Z[Book] =
    isViewer >>= (u =>
      storage.borrowBook(u, args.id) *>
      storage.getBook(args.id).map(bookToGQL)
    )

  def login(args: Login): Z[Logged] =
    (args match {
      case Login("admin", "a")  => JWT.encodeToken(User("admin", Role.Editor))
      case Login("viewer", "v") => JWT.encodeToken(User("viewer", Role.Viewer))
      case _                    => ZIO.fail(AuthException("Login failed"))
    })
    .map(Logged)
    .provideSome(x => x ++ Has(x.get[AppConfig].jwt))
//    .provideLayer(config.jwtConfig)

  object schema extends GenericSchema[Env]
  import schema._

  val interpreter = graphQL[Env, Queries, Mutations, Unit](
    RootResolver(
      Queries(
        books,
        book,
        myBooks,
      ),
      Mutations(
        login,
        createBook,
        updateBook,
        deleteBook,
        borrowBook,
      )
    )
  ).interpreter.mapError(
    e =>
      e.toString()
  )

}
