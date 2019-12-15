package com.fokot.graphql

import com.fokot.exceptions.AuthException
import com.fokot.services.Auth
import com.fokot.services.model.{Role, User}
import zio.{RIO, ZIO}

object auth {

  type Authorized = RIO[Auth, User]

  val isAuthenticated: Authorized = ZIO.accessM[Auth](_.auth.currentUser)

  /**
   * Will succeed if user has at least one of specified roles
   */
  def hasRole(r: Role*): Authorized = isAuthenticated.filterOrFail(t => r.contains(t.role))(AuthException("Permission denied"))

  val isEditor: Authorized = hasRole(Role.Editor)

  val isViewer: Authorized = hasRole(Role.Editor, Role.Viewer)


}
