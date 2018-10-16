import sbt.Keys._

name in ThisBuild := "stasis"
licenses in ThisBuild := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage in ThisBuild := Some(url("https://github.com/sndnv/stasis"))

scalaVersion in ThisBuild := "2.12.7"

lazy val akkaVersion = "2.5.17"

lazy val commonSettings = Seq(
  crossScalaVersions := Seq("2.12.7"),
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
    "org.scalacheck"    %% "scalacheck"   % "1.14.0"    % Test,
    "org.scalatest"     %% "scalatest"    % "3.0.5"     % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
  ),
  logBuffered in Test := false,
  parallelExecution in Test := false,
  wartremoverWarnings in (Compile, compile) ++= Warts.unsafe,
  scalacOptions := Seq("-unchecked", "-deprecation")
)

lazy val stasis = (project in file("."))
  .settings(commonSettings)

addCommandAlias("qa", "; clean; coverage; test; coverageReport")
