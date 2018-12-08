import sbt.Keys._

name in ThisBuild := "stasis"
licenses in ThisBuild := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage in ThisBuild := Some(url("https://github.com/sndnv/stasis"))

scalaVersion in ThisBuild := "2.12.7"

lazy val akkaVersion = "2.5.17"
lazy val akkaHttpVersion = "10.1.5"

lazy val commonSettings = Seq(
  crossScalaVersions := Seq("2.12.7"),
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor"           % akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed"     % akkaVersion,
    "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
    "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
    "com.typesafe.play" %% "play-json"            % "2.6.10",
    "de.heikoseeberger" %% "akka-http-play-json"  % "1.22.0",
    "org.scalacheck"    %% "scalacheck"           % "1.14.0"          % Test,
    "org.scalatest"     %% "scalatest"            % "3.0.5"           % Test,
    "com.typesafe.akka" %% "akka-testkit"         % akkaVersion       % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion       % Test,
    "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion   % Test
  ),
  logBuffered in Test := false,
  parallelExecution in Test := false,
  wartremoverWarnings in (Compile, compile) ++= Warts.unsafe,
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature")
)

lazy val stasis = (project in file("."))
  .settings(commonSettings)

addCommandAlias("qa", "; clean; coverage; test; coverageReport")
