import sbt._

object Dependencies {
  val zioCore = "dev.zio"  %% "zio"              % Version.zio
  val zioCats = ("dev.zio" %% "zio-interop-cats" % Version.zioCats).excludeAll(ExclusionRule("dev.zio"))
  val zio     = List(zioCore, zioCats)

  val cats       = "org.typelevel" %% "cats-core"   % Version.cats
  val catsEffect = "org.typelevel" %% "cats-effect" % Version.catsEffect

  val zioTestCore = "dev.zio" %% "zio-test"     % Version.zio % Test
  val zioTestSbt  = "dev.zio" %% "zio-test-sbt" % Version.zio % Test
  val zioTest     = List(zioTestCore, zioTestSbt)

  val http4s = Seq(
    "org.http4s" %% "http4s-dsl"          % Version.http4s,
    "org.http4s" %% "http4s-circe"        % Version.http4s,
    "org.http4s" %% "http4s-blaze-server" % Version.http4s,
    "org.http4s" %% "http4s-blaze-client" % Version.http4s
  )

  val fs2Core = "co.fs2" %% "fs2-core" % Version.fs2Core

  val doobie = Seq(
    "org.tpolecat" %% "doobie-core"  % Version.doobie,
    "org.tpolecat" %% "doobie-h2"    % Version.doobie,
    "org.tpolecat" %% "doobie-quill" % Version.doobie
  )

  val quill = "io.getquill" %% "quill-jdbc" % Version.quill

  val h2 = "com.h2database" % "h2" % "1.4.200"

  val newtype = "io.estatico" %% "newtype" % Version.newtype

  val circeGeneric = "io.circe" %% "circe-generic" % Version.circe
  val circeCore    = "io.circe" %% "circe-core"    % Version.circe
  val circeParser  = "io.circe" %% "circe-parser"  % Version.circe
  val circe        = List(circeGeneric, circeCore, circeParser)

  val slf4j = "org.slf4j" % "slf4j-simple" % Version.slf4j

  val neutronCore  = "com.chatroulette" %% "neutron-core"  % Version.neutron
  val neutronCirce = "com.chatroulette" %% "neutron-circe" % Version.neutron

  val ciris = "is.cir" %% "ciris" % Version.ciris

  val contextApplied = "org.augustjune" %% "context-applied" % Version.contextApplied
  val kindProjector  = "org.typelevel"  %% "kind-projector"  % Version.kindProjector cross CrossVersion.full
}

object Version {
  val cats           = "2.3.1"
  val catsEffect     = "3.1.0"
  val zio            = "1.0.6"
  val zioCats        = "2.4.0.0"
  val slf4j          = "1.7.30"
  val fs2Core        = "3.0.2"
  val doobie         = "1.0.0-M2"
  val quill          = "3.7.1"
  val http4s         = "1.0.0-M21"
  val kindProjector  = "0.11.3"
  val ciris          = "2.0.0-RC2"
  val circe          = "0.13.0"
  val newtype        = "0.4.4"
  val neutron        = "0.0.4"
  val contextApplied = "0.1.4"
}
