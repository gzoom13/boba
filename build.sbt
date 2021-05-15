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

lazy val domain = (project in file("domain"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= converterMockDependencies)

lazy val pump = (project in file("pump"))
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(Compile / mainClass := Some("net.golikov.boba.pump.Pump"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= pumpDependencies)
  .dependsOn(domain)

lazy val `trace-engine` = (project in file("trace-engine"))
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(Compile / mainClass := Some("net.golikov.boba.traceengine.TraceEngine"))
  .settings(commonSettings)
  .settings(libraryDependencies ++= traceEngineDependencies)
  .dependsOn(domain)
