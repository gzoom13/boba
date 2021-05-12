import Dependencies._
import com.typesafe.sbt.packager.Keys._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys.{scalacOptions, _}
import sbt._

object Settings {

  val commonSettings = {
    Seq(
      scalaVersion := "2.13.5",
      scalacOptions := Seq(
        "-Xsource:3",
        "-Ymacro-annotations",
        "-deprecation",
        "-encoding", "utf-8",
        "-explaintypes",
        "-feature",
        "-unchecked",
        "-language:postfixOps",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Xcheckinit",
        "-Xfatal-warnings"
      ),
      version := (version in ThisBuild).value,
      scalafmtOnCompile := true,

      cancelable in Global := true,
      fork in Global := true,
      resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",

      addCompilerPlugin(contextApplied),
      addCompilerPlugin(kindProjector),

      dockerBaseImage := "openjdk:jre-alpine",
      dockerUpdateLatest := true
    )
  }

  val routerMockDependencies = List(slf4j, cats, catsEffect, h2, ciris, newtype) ++ doobie ++ http4s ++ circe
  val converterMockDependencies = List(slf4j, cats, catsEffect, h2, ciris, newtype) ++ doobie ++ http4s ++ circe
}
