package com.fokot.graphql

import zquery.{CompletedRequestMap, DataSource, Request}

object utils {

  case class RequestId[ID, A](id: ID) extends Request[Throwable, A]

  def simpleDataSource[ID, A](name: String, f: List[ID] => Z[List[A]], idf: A => ID): DataSource[Env, RequestId[ID, A]] =
    DataSource(name) { requests =>
      f(requests.map(_.id).toList)
        .map(
          _.foldLeft(CompletedRequestMap.empty) {
            case (map, a) => map.insert(RequestId(idf(a)))(Right(a))
          }
        )
        .orDie
    }

}
