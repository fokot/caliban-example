import Dependencies._

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.fokot"
ThisBuild / organizationName := "fokot"

val caliban = "com.github.ghostdogpr" %% "caliban" % "0.3.1" withSources
val calibanHttp4s = "com.github.ghostdogpr" %% "caliban-http4s" % "0.3.1" withSources
val zio = "dev.zio" %% "zio" % "1.0.0-RC17"
val kindProjector = "org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full
val jwt = "com.pauldijou" %% "jwt-circe" % "4.2.0"
val circeGenericExtras = "io.circe" %% "circe-generic-extras" % "0.12.2"
val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.12.1"

lazy val root = (project in file("."))
  .settings(
    addCompilerPlugin(kindProjector),
    name := "caliban-example",
    libraryDependencies ++= Seq(
      caliban,
      calibanHttp4s,
      zio,
      jwt,
      circeGenericExtras,
      pureconfig,
      scalaTest % Test,
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
