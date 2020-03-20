package com.fokot.services

import com.fokot.exceptions.InputDataException
import com.fokot.services.model.{Author, Book, BookCount, User}
import com.fokot.services.storage.StorageService
import zio.{Has, RIO, Task, UIO, ZIO, ZLayer}
import zio.console.Console
import zio.stm.{STM, TRef}

object storage {

  type Storage = Has[StorageService[Console]]

  trait StorageService[R] {

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

    def getBookCount(u: User): F[BookCount]

    def borrowBook(u: User, bookId: Long): F[Unit]
  }

  type En = Storage with Console
  type F[A] = RIO[En, A]
  def getAllBooks(): F[List[Book]] = ZIO.accessM[En](_.get.getAllBooks())
  def getBook(id: Long): F[Book] = ZIO.accessM[En](_.get.getBook(id))
  def createBook(b: Book): F[Book] = ZIO.accessM[En](_.get.createBook(b))
  def updateBook(b: Book): F[Book] = ZIO.accessM[En](_.get.updateBook(b))
  def deleteBook(id: Long): F[Unit] = ZIO.accessM[En](_.get.deleteBook(id))
  def getAuthor(id: Long): F[Author] = ZIO.accessM[En](_.get.getAuthor(id))
  def getAuthors(ids: List[Long]): F[List[Author]] = ZIO.accessM[En](_.get.getAuthors(ids))
  def getBooksForAuthor(id: Long): F[List[Book]] = ZIO.accessM[En](_.get.getBooksForAuthor(id))
  def getBooksForUser(u: User): F[List[Book]] = ZIO.accessM[En](_.get.getBooksForUser(u))
  def getBookCount(u: User): F[BookCount] = ZIO.accessM[En](_.get.getBookCount(u))
  def borrowBook(u: User, bookId: Long): F[Unit] = ZIO.accessM[En](_.get.borrowBook(u, bookId))

  val inMemoryStorage: ZLayer[Any, Nothing, Storage] = ZLayer.fromEffect(InMemoryStorage.create)
}

object InMemoryStorage {

  var books: List[Book] = List(
    Book(1, "Harry potter", 2000, 1),
    Book(2, "Lord of the Rings I.", 1990, 2),
    Book(3, "Lord of the Rings II.", 1991, 2),
    Book(4, "Lord of the Rings III.", 1992, 2),
    Book(5, "Tribal Leadership", 2012, 3)
  )

  val authors: List[Author] = List(
    Author(1, "Joanne", "Rowling"),
    Author(2, "J.R.R", "Tolkien"),
    Author(3, "Dave", "Logan")
  )

  val userBooks: Map[User, List[Long]] = Map.empty

  def create: UIO[InMemoryStorage] = (for {
    _books <- TRef.make(books)
    _authors <- TRef.make(authors)
    _userBooks <- TRef.make(userBooks)
  } yield new InMemoryStorage(_books, _authors, _userBooks)).commit
}

class InMemoryStorage(
  books: TRef[List[Book]],
  authors: TRef[List[Author]],
  userBooks: TRef[Map[User, List[Long]]]
) extends StorageService[Console] {

  import zio.console._

  def bookById(id: Long): Task[Book] =
    books.get.commit.map(_.find(_.id == id)).someOrFail(new Exception(s"No book #$id"))

  def authorById(id: Long): Task[Author] =
    authors.get.commit.map(_.find(_.id == id)).someOrFail(new Exception(s"No author #$id"))

  override def getAllBooks(): F[List[Book]] =
    putStrLn("getAllBooks") *> books.get.commit

  override def getBook(id: Long): F[Book] =
    putStrLn("getBook") *> bookById(id)

  override def createBook(b: Book): F[Book] =
    putStrLn("createBook") *>
      books.get.commit.filterOrFail(_.forall(_.id != b.id))(InputDataException(s"Book with ${b.id} already exists")) *>
      books.update(b :: _).commit as b

  override def updateBook(b: Book): F[Book] =
    putStrLn("updateBook") *>
      books.modify(books => (b, books.map { case x if x.id == b.id => b; case x => x })).commit

  override def deleteBook(id: Long): F[Unit] =
    putStrLn("deleteBook") *> books.update(_.filterNot(_.id == id)).commit.unit

  override def getAuthor(id: Long): F[Author] =
    putStrLn(s"getAuthor $id") *> authorById(id)

  override def getAuthors(ids: List[Long]): F[List[Author]] =
    putStrLn(s"getAuthors $ids") *> ZIO.collectAll(ids.map(authorById))

  override def getBooksForAuthor(id: Long): F[List[Book]] =
    putStrLn(s"getBooksForAuthor $id") *> books.get.commit.map(_.filter(_.authorId == id))

  override def getBooksForUser(u: User): F[List[Book]] =
    putStrLn(s"getBooksForUser ${u.login}") *>
      userBooks.get.commit.flatMap(ub => ZIO.collectAll(ub.getOrElse(u, Nil).map(bookById)))

  override def borrowBook(u: User, bookId: Long): F[Unit] =
    putStrLn(s"borrowBook ${bookId} by ${u.login}") *>
      userBooks.update(
        _.updatedWith(u)(ids => Some(bookId :: ids.getOrElse(Nil)))
      ).commit.unit

  override def getBookCount(u: User): F[BookCount] =
    putStrLn(s"getBookCount by ${u.login}") *>
      (
        for {
          total <- books.get.map(_.length)
          borrowedNow <- userBooks.get.map(_.getOrElse(u, Nil).length)
        } yield BookCount(total, borrowedNow)
      ).commit
}


