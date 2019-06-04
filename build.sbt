import sbt.Keys._

name in ThisBuild := "stasis"
licenses in ThisBuild := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage in ThisBuild := Some(url("https://github.com/sndnv/stasis"))

lazy val defaultScalaVersion = "2.12.8"
lazy val akkaVersion = "2.5.20"
lazy val akkaHttpVersion = "10.1.7"

lazy val crossVersions = Seq(defaultScalaVersion)

scalaVersion in ThisBuild := defaultScalaVersion

lazy val server = (project in file("./server"))
  .settings(commonSettings)
  .dependsOn(shared % "compile->compile;test->test")

lazy val client = (project in file("./client"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.pathikrit"  %%  "better-files"  % "3.7.1",
      "at.favre.lib"          %   "hkdf"          % "1.0.2"
    ),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
    coverageExcludedPackages := "stasis.client.model.proto.metadata.*"
  )
  .dependsOn(shared % "compile->compile;test->test")

lazy val shared = (project in file("./shared"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.julienrf" %% "play-json-derived-codecs" % "5.0.0"
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val core = (project in file("./core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"       %% "akka-actor"           % akkaVersion,
      "com.typesafe.akka"       %% "akka-actor-typed"     % akkaVersion,
      "com.typesafe.akka"       %% "akka-stream"          % akkaVersion,
      "com.typesafe.akka"       %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka"       %% "akka-http-core"       % akkaHttpVersion,
      "com.typesafe.akka"       %% "akka-http2-support"   % akkaHttpVersion,
      "com.typesafe.play"       %% "play-json"            % "2.7.0",
      "de.heikoseeberger"       %% "akka-http-play-json"  % "1.23.0",
      "org.bitbucket.b_c"       %  "jose4j"               % "0.6.4",
      "org.apache.geode"        %  "geode-core"           % "1.8.0"           % Provided,
      "com.typesafe.slick"      %% "slick"                % "3.2.3"           % Provided,
      "com.h2database"          %  "h2"                   % "1.4.197"         % Test,
      "org.scalacheck"          %% "scalacheck"           % "1.14.0"          % Test,
      "org.scalatest"           %% "scalatest"            % "3.0.5"           % Test,
      "com.typesafe.akka"       %% "akka-testkit"         % akkaVersion       % Test,
      "com.typesafe.akka"       %% "akka-stream-testkit"  % akkaVersion       % Test,
      "com.typesafe.akka"       %% "akka-http-testkit"    % akkaHttpVersion   % Test,
      "com.github.tomakehurst"  %  "wiremock"             % "2.20.0"          % Test
    )
  )
  .dependsOn(proto)

lazy val proto = (project in file("./proto"))
  .settings(
    crossScalaVersions := crossVersions,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream"        % akkaVersion,
      "com.typesafe.akka" %% "akka-http"          % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-core"     % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion
    ),
    coverageEnabled := false
  )
 .enablePlugins(AkkaGrpcPlugin)

lazy val commonSettings = Seq(
  crossScalaVersions := crossVersions,
  logBuffered in Test := false,
  parallelExecution in Test := false,
  wartremoverWarnings in(Compile, compile) ++= Warts.unsafe,
  wartremoverExcluded in(Compile, compile) += sourceManaged.value,
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature")
)

addCommandAlias("qa", "; clean; compile; coverage; test; coverageReport; coverageAggregate")
