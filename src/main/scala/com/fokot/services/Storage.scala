package com.fokot.services

import com.fokot.exceptions.InputDataException
import com.fokot.services.model.{Author, Book, User}
import zio.{RIO, Task}
import zio.console.Console

trait Storage {
  def storage: Storage.Service[Console]
}

object Storage {

  trait Service[R] {

    type F[A] = RIO[Console, A]

    def getAllBooks(): F[List[Book]]

    def getBook(id: Long): F[Book]

    def createBook(b: Book): F[Book]

    def updateBook(b: Book): F[Book]

    def deleteBook(id: Long): F[Unit]

    def getAuthor(id: Long): F[Author]

    def getAuthors(ids: List[Long]): F[List[Author]]

    def getBooksForAuthor(id: Long): F[List[Book]]

    def getBooksForUser(u: User): F[List[Book]]

    def borrowBook(u: User, bookId: Long): F[Unit]
  }
}

/**
 * This component is stateful so we can't use module pattern :(
 */
class FakeStorage extends Storage.Service[Console] {

  import zio.console._

  var books: List[Book] = List(
    Book(1, "Harry potter", 2000, 1),
    Book(2, "Lord of the Rings I.", 1990, 2),
    Book(3, "Lord of the Rings II.", 1991, 2),
    Book(4, "Lord of the Rings III.", 1992, 2),
    Book(5, "Tribal Leadership", 2012, 3)
  )

  def booksById: Map[Long, Book] = books.map(a => (a.id, a)).toMap

  val authors: List[Author] = List(
    Author(1, "Joanne", "Rowling"),
    Author(2, "J.R.R", "Tolkien"),
    Author(3, "Dave", "Logan")
  )

  var userBooks: Map[User, List[Long]] = Map.empty

  def authorsById: Map[Long, Author] = authors.map(a => (a.id, a)).toMap

  override def getAllBooks(): F[List[Book]] =
    putStrLn("getAllBooks") as books

  override def getBook(id: Long): F[Book] =
    putStrLn("getBook") *> Task.effect(booksById(id))

  override def createBook(b: Book): F[Book] =
    putStrLn("createBook") *>
      Task.succeed(books).filterOrFail(_.forall(_.id != b.id))(InputDataException(s"Book with ${b.id} already exists")) *>
      Task.effect{ books = b :: books } as b

  override def updateBook(b: Book): F[Book] =
    putStrLn("updateBook") *> Task.effect{
      books = books.map { case x if x.id == b.id => b; case x => x }
    } as b

  override def deleteBook(id: Long): F[Unit] =
    putStrLn("deleteBook") *> Task.effect {
      books = books.filterNot(_.id == id)
    }

  override def getAuthor(id: Long): F[Author] =
    putStrLn(s"getAuthor $id") as authorsById(id)

  override def getAuthors(ids: List[Long]): F[List[Author]] =
    putStrLn(s"getAuthors $ids") as ids.map(authorsById)

  override def getBooksForAuthor(id: Long): F[List[Book]] =
    putStrLn(s"getBooksForAuthor $id") as books.filter(_.authorId == id)

  override def getBooksForUser(u: User): F[List[Book]] =
    putStrLn(s"getBooksForUser ${u.login}") as userBooks.getOrElse(u, Nil).map(booksById)

  override def borrowBook(u: User, bookId: Long): F[Unit] =
    putStrLn(s"borrowBook ${bookId} by ${u.login}") *> Task.effect {
      userBooks = userBooks.updatedWith(u)(ids => Some(bookId :: ids.getOrElse(Nil)))
    }
}


