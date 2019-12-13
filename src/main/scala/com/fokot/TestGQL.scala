//package com.fokot
//
//import caliban.GraphQL.graphQL
//import caliban.RootResolver
//import caliban.schema.GenericSchema
//import zio.console.Console
//import zio.{RIO, ZIO}
//import zquery.{CompletedRequestMap, DataSource, Request, ZQuery}
//
//object TestGQL {
//
//  case class Author(id: Long, firstName: String, lastName: String)
//
//  case class Book(id: Long, name: String, publishedYear: Int, authorId: Long)
//
//  val allBooks: List[Book] = List(
//    Book(1, "Harry potter", 2000, 1),
//    Book(2, "Lord of the Rings I.", 1990, 2),
//    Book(3, "Lord of the Rings II.", 1991, 2),
//    Book(4, "Lord of the Rings III.", 1992, 2),
//    Book(5, "Tribal Leadership", 2012, 3)
//  )
//
//  val authors: Map[Long, Author] =
//    List(Author(1, "Joanne", "Rowling"), Author(2, "J.R.R", "Tolkien"), Author(3, "Dave", "Logan")).map(a => (a.id, a)).toMap
//
//  trait Storage {
//    def storage: Storage.Service[Console]
//  }
//
//  object Storage {
//    trait Service[R] {
//
//      def getAllBooks(): RIO[R, List[Book]]
//
//      def getAuthor(id: Long): RIO[R, Author]
//
//      def getAuthors(ids: List[Long]): RIO[R, List[Author]]
//    }
//  }
//
//  class FakeStorage extends Storage.Service[Console] {
//    import zio.console._
//
//    def getAllBooks(): RIO[Console, List[Book]] = putStrLn("getAllBooks") as allBooks
//
//    def getAuthor(id: Long): RIO[Console, Author] = putStrLn(s"getAuthor $id") as authors(id)
//
//    def getAuthors(ids: List[Long]): RIO[Console, List[Author]] = putStrLn(s"getAuthors $ids") as ids.map(authors)
//  }
//
//  type Context = Storage with Console
//  type Z[A] = RIO[Context, A]
//  type Query[A] = ZQuery[Context, Throwable, A]
//
//  case class BookGQL(id: Long, name: String, publishedYear: Int, author: Z[Author])
//
//  case class BookGQL2(id: Long, name: String, publishedYear: Int, author: Query[Author])
//
//  case class Queries(books: Z[List[BookGQL]], books2: Z[List[BookGQL2]])
//
//  val books: Z[List[BookGQL]] = ZIO
//    .access[Storage](_.storage)
//    .flatMap(s => s.getAllBooks().map(_.map(b => BookGQL(b.id, b.name, b.publishedYear, s.getAuthor(b.authorId)))))
//
//  case class GetAuthor(id: Long) extends Request[Throwable, Author]
//  val AuthorDataSource: DataSource.Service[Context, GetAuthor] =
//    DataSource.Service("AuthorDataSource") { requests =>
//      requests.toList match {
//        case head :: Nil =>
//          ZIO.accessM[Context](_.storage.getAuthor(head.id)).map(a => CompletedRequestMap.empty.insert(head)(Right(a))).orDie
//        case list =>
//          ZIO
//            .accessM[Context](_.storage.getAuthors(list.map(_.id)))
//            .map(
//              as =>
//                as.foldLeft(CompletedRequestMap.empty) {
//                  case (map, a) => map.insert(GetAuthor(a.id))(Right(a))
//                }
//            )
//            .orDie
//      }
//    }
//
//  def getAuthor(id: Long): Query[Author] =
//    ZQuery.fromRequestWith(GetAuthor(id))(AuthorDataSource)
//
//  val books2: Z[List[BookGQL2]] = ZIO
//    .access[Storage](_.storage)
//    .flatMap(s => s.getAllBooks().map(_.map(b => BookGQL2(b.id, b.name, b.publishedYear, getAuthor(b.authorId)))))
//
//  object schema extends GenericSchema[Context]
//  import schema._
//
//  val interpreter = graphQL[Context, Queries, Unit, Unit](RootResolver(Queries(books, books2)))
//}
