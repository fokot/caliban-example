# Caliban example

This project serves as example of using [Caliban](https://ghostdogpr.github.io/caliban/).

## Running
To run run `sbt run` and open [http://localhost:8000/graphiql]

To login use credentials
```
/-------------------\
| login  | password |
---------------------
| admin  |    a     |
| viewer |    v     |
\-------------------/
```

## Authorization
Authorization is solved nicely in [auth](src/main/scala/com/fokot/graphql/GQL.scala) and [GQL](src/main/scala/com/fokot/graphql/GQL.scala) files
```scala
def book(args: BookInp): Z[Book] =
  isViewer *> ZIO.accessM[Env](_.storage.getBook(args.id).map(bookToGQL))
```

## More
More about Caliban you can read at my blog [http://fokot.github.io/]()

> P.S. If you have any improvement how to do things better leave me an issue or a PR.
