import Settings._

ThisBuild / scalaVersion := "2.13.4"

name := "boba"
organization := "net.golikov"
maintainer := "andrey@golikov.net"
version := "1.0"

lazy val routerMock = (project in file("mocks/router"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= routerDependencies)
