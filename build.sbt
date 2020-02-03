import sbt.Keys._

name in ThisBuild := "stasis"
licenses in ThisBuild := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage in ThisBuild := Some(url("https://github.com/sndnv/stasis"))

lazy val defaultScalaVersion = "2.12.9"
lazy val akkaVersion = "2.5.23"
lazy val akkaHttpVersion = "10.1.9"
lazy val slickVersion = "3.3.1"
lazy val h2Version = "1.4.199"

lazy val crossVersions = Seq(defaultScalaVersion)

scalaVersion in ThisBuild := defaultScalaVersion

lazy val server = (project in file("./server"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.h2database"     %  "h2"    % h2Version
    ),
    dockerBaseImage := "openjdk:11"
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(shared % "compile->compile;test->test")

lazy val client = (project in file("./client"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "at.favre.lib"      % "hkdf"    % "1.1.0",
      "net.harawata"      % "appdirs" % "1.0.3",
      "com.google.jimfs"  % "jimfs"   % "1.1"     % Test
    ),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
    coverageExcludedPackages := "stasis.client.model.proto.metadata.*"
  )
  .dependsOn(shared % "compile->compile;test->test")

lazy val identity = (project in file("./identity"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.h2database"     %  "h2"    % h2Version
    ),
    dockerBaseImage := "openjdk:11"
  )
  .dependsOn(core % "compile->compile;test->test")
  .enablePlugins(JavaAppPackaging)

lazy val shared = (project in file("./shared"))
  .settings(commonSettings)
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
      "com.typesafe.play"       %% "play-json"            % "2.7.4",
      "de.heikoseeberger"       %% "akka-http-play-json"  % "1.27.0",
      "org.bitbucket.b_c"       %  "jose4j"               % "0.6.5",
      "org.apache.geode"        %  "geode-core"           % "1.9.0"           % Provided,
      "com.typesafe.slick"      %% "slick"                % slickVersion      % Provided,
      "com.h2database"          %  "h2"                   % h2Version         % Test,
      "org.scalacheck"          %% "scalacheck"           % "1.14.0"          % Test,
      "org.scalatest"           %% "scalatest"            % "3.0.8"           % Test,
      "com.typesafe.akka"       %% "akka-testkit"         % akkaVersion       % Test,
      "com.typesafe.akka"       %% "akka-stream-testkit"  % akkaVersion       % Test,
      "com.typesafe.akka"       %% "akka-http-testkit"    % akkaHttpVersion   % Test,
      "com.github.tomakehurst"  %  "wiremock-jre8"        % "2.24.1"          % Test
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
