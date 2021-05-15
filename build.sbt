import Settings._

ThisBuild / scalaVersion := "2.13.4"

name := "boba"
organization := "net.golikov"
maintainer := "andrey@golikov.net"
version := "1.0"

lazy val routerMock = (project in file("mocks/router"))
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(commonSettings)
  .settings(libraryDependencies ++= routerMockDependencies)
  .settings(Compile / mainClass := Some("net.golikov.boba.mock.router.Router"))

lazy val converterMock = (project in file("mocks/converter"))
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(commonSettings)
  .settings(libraryDependencies ++= converterMockDependencies)
  .settings(Compile / mainClass := Some("net.golikov.boba.mock.converter.Converter"))
