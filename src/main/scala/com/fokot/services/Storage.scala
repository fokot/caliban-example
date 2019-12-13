package com.fokot.services

import com.fokot.services.model.{Author, Book}
import zio.{RIO, ZIO}
import zio.console.Console

trait Storage[R] {
  def storage: Storage.Service[R]
}

object Storage {

  trait Service[R] {

    def getAllBooks(): RIO[R, List[Book]]

    def getAuthor(id: Long): RIO[R, Author]

    def getAuthors(ids: List[Long]): RIO[R, List[Author]]

    def getBooksForAuthor(id: Long): RIO[R, List[Book]]
  }
}

class FakeStorage extends Storage.Service[Console] {

  import zio.console._

  val allBooks: List[Book] = List(
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

  val authorsById: Map[Long, Author] = authors.map(a => (a.id, a)).toMap

  def getAllBooks(): RIO[Console, List[Book]] =
    putStrLn("getAllBooks") as allBooks

  def getAuthor(id: Long): RIO[Console, Author] =
    putStrLn(s"getAuthor $id") as authorsById(id)

  def getAuthors(ids: List[Long]): RIO[Console, List[Author]] =
    putStrLn(s"getAuthors $ids") as ids.map(authorsById)

  def getBooksForAuthor(id: Long): RIO[Console, List[Book]] =
    putStrLn(s"getBooksForAuthor $id") as allBooks.filter(_.authorId == id)
}


