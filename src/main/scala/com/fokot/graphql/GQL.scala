package com.fokot.graphql

import caliban.schema.GenericSchema
import caliban.GraphQL.graphQL
import caliban.RootResolver
import com.fokot.exceptions.AuthException
import com.fokot.services.{JWT, Storage, model}
import zio.ZIO
import zquery.{CompletedRequestMap, DataSource, Request, ZQuery}
import com.fokot.graphql.auth.{isEditor, isViewer}
import com.fokot.graphql.utils.RequestId
import com.fokot.services.JWT.JWTConfig
import com.fokot.services.model.{Role, User}
import com.fokot.utils.WC
import zio.clock.Clock


/**
 * GraphQL resolvers
 */
object GQL {

  val AuthorDataSource: DataSource.Service[Env, RequestId[Long, model.Author]] =
    utils.simpleDataSource[Long, model.Author]("AuthorDataSource", Storage.>.getAuthors, _.id)

  def getAuthor(id: Long): Q[Author] =
    ZQuery.fromRequestWith(RequestId[Long, model.Author](id))(AuthorDataSource).map(authorToGQL)

  def getBooksForAuthor(id: Long): Z[List[model.Book]] =
    Storage.>.getBooksForAuthor(id)

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
    isViewer *> Storage.>.getAllBooks().map(_.map(bookToGQL))

  def book(args: BookId): Z[Book] =
    isViewer *> Storage.>.getBook(args.id).map(bookToGQL)

  def myBooks: Z[MyBooks] =
    isViewer >>=
      { u =>
        for {
          res <- Storage.>.getBookCount(u).memoize

        } yield
          MyBooks(
            res.map(_.total),
            res.map(_.borrowedNow),
            Storage.>.getBooksForUser(u).map(_.map(bookToGQL))
        )
      }

  def createBook(args: BookInput): Z[Book] =
    isEditor *> Storage.>.createBook(bookInputFromGQL(args)).map(bookToGQL)

  def updateBook(args: BookInput): Z[Book] =
    isEditor *> Storage.>.updateBook(bookInputFromGQL(args)).map(bookToGQL)

  def deleteBook(args: BookId): Z[Unit] =
    isEditor *> Storage.>.deleteBook(args.id)

  def borrowBook(args: BookId): Z[Book] =
    isViewer >>= (u =>
      Storage.>.borrowBook(u, args.id) *>
      Storage.>.getBook(args.id).map(bookToGQL)
    )

  def login(args: Login): Z[Logged] =
    (args match {
      case Login("admin", "a")  => JWT.encodeToken(User("admin", Role.Editor))
      case Login("viewer", "v") => JWT.encodeToken(User("viewer", Role.Viewer))
      case _                       => ZIO.fail(AuthException("Login failed"))
    })
    .map(Logged)
    .provideSome(
    (env: Env) => new WC[JWTConfig] with Clock {
      override def config: JWTConfig = env.config.jwt
      override val clock: Clock.Service[Any] = env.clock
    }
  )

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
  )

}
