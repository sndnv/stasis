import sbt.Keys._

lazy val projectName = "stasis"

name     := projectName
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/sndnv/stasis"))

ThisBuild / scalaVersion := "2.13.8"

lazy val versions = new {
  // akka
  val akka         = "2.6.19"
  val akkaHttp     = "10.2.9"
  val akkaHttpCors = "1.1.3"
  val akkaJson     = "1.39.2"

  // persistence
  val geode    = "1.14.4"
  val slick    = "3.3.3"
  val postgres = "42.3.6"
  val mariadb  = "3.0.5"
  val sqlite   = "3.36.0.3"
  val h2       = "2.1.212"

  // telemetry
  val openTelemetry           = "1.14.0"
  val openTelemetryPrometheus = "1.14.0-alpha"
  val prometheus              = "0.15.0"

  // testing
  val scalaCheck    = "1.16.0"
  val scalaTest     = "3.2.12"
  val wiremock      = "2.33.2"
  val mockito       = "1.17.7"
  val mockitoInline = "4.6.1"
  val jimfs         = "1.2"

  // misc
  val playJson = "2.9.2"
  val jose4j   = "0.7.12"
  val hkdf     = "1.1.0"
  val appdirs  = "1.2.1"
  val scopt    = "4.0.1"
  val logback  = "1.2.11"
}

lazy val jdkDockerImage = "openjdk:11"

lazy val server = (project in file("./server"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-slf4j"                        % versions.akka,
      "ch.qos.logback"      % "logback-classic"                   % versions.logback,
      "com.typesafe.slick" %% "slick"                             % versions.slick,
      "com.h2database"      % "h2"                                % versions.h2,
      "org.postgresql"      % "postgresql"                        % versions.postgres,
      "org.mariadb.jdbc"    % "mariadb-java-client"               % versions.mariadb,
      "org.xerial"          % "sqlite-jdbc"                       % versions.sqlite,
      "ch.megard"          %% "akka-http-cors"                    % versions.akkaHttpCors,
      "io.opentelemetry"    % "opentelemetry-sdk"                 % versions.openTelemetry,
      "io.opentelemetry"    % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus,
      "io.prometheus"       % "simpleclient_hotspot"              % versions.prometheus
    ),
    dockerBaseImage := jdkDockerImage
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(shared % "compile->compile;test->test")

lazy val client = (project in file("./client"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      "at.favre.lib"       % "hkdf"              % versions.hkdf,
      "net.harawata"       % "appdirs"           % versions.appdirs,
      "com.typesafe.akka" %% "akka-slf4j"        % versions.akka,
      "com.typesafe.akka" %% "akka-http-caching" % versions.akkaHttp,
      "ch.qos.logback"     % "logback-classic"   % versions.logback,
      "com.github.scopt"  %% "scopt"             % versions.scopt,
      "com.google.jimfs"   % "jimfs"             % versions.jimfs % Test
    ),
    dockerBaseImage          := jdkDockerImage,
    Compile / PB.targets     := Seq(
      scalapb.gen(singleLineToProtoString = true) -> (Compile / sourceManaged).value
    ),
    coverageExcludedPackages := "stasis.client.model.proto.metadata.*"
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(shared % "compile->compile;test->test")

lazy val identity = (project in file("./identity"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-slf4j"                        % versions.akka,
      "ch.qos.logback"      % "logback-classic"                   % versions.logback,
      "com.typesafe.slick" %% "slick"                             % versions.slick,
      "com.h2database"      % "h2"                                % versions.h2,
      "org.postgresql"      % "postgresql"                        % versions.postgres,
      "org.mariadb.jdbc"    % "mariadb-java-client"               % versions.mariadb,
      "org.xerial"          % "sqlite-jdbc"                       % versions.sqlite,
      "ch.megard"          %% "akka-http-cors"                    % versions.akkaHttpCors,
      "io.opentelemetry"    % "opentelemetry-sdk"                 % versions.openTelemetry,
      "io.opentelemetry"    % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus,
      "io.prometheus"       % "simpleclient_hotspot"              % versions.prometheus
    ),
    dockerBaseImage := jdkDockerImage
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
      "com.typesafe.akka"     %% "akka-actor"                        % versions.akka,
      "com.typesafe.akka"     %% "akka-actor-typed"                  % versions.akka,
      "com.typesafe.akka"     %% "akka-stream"                       % versions.akka,
      "com.typesafe.akka"     %% "akka-discovery"                    % versions.akka,
      "com.typesafe.akka"     %% "akka-http"                         % versions.akkaHttp,
      "com.typesafe.akka"     %% "akka-http-core"                    % versions.akkaHttp,
      "com.typesafe.akka"     %% "akka-http2-support"                % versions.akkaHttp,
      "com.typesafe.play"     %% "play-json"                         % versions.playJson,
      "de.heikoseeberger"     %% "akka-http-play-json"               % versions.akkaJson,
      "org.bitbucket.b_c"      % "jose4j"                            % versions.jose4j,
      "io.opentelemetry"       % "opentelemetry-api"                 % versions.openTelemetry,
      "io.opentelemetry"       % "opentelemetry-sdk"                 % versions.openTelemetry           % Provided,
      "io.opentelemetry"       % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus % Provided,
      "io.prometheus"          % "simpleclient"                      % versions.prometheus              % Provided,
      "org.apache.geode"       % "geode-core"                        % versions.geode                   % Provided,
      "com.typesafe.slick"    %% "slick"                             % versions.slick                   % Provided,
      "com.h2database"         % "h2"                                % versions.h2                      % Test,
      "org.scalacheck"        %% "scalacheck"                        % versions.scalaCheck              % Test,
      "org.scalatest"         %% "scalatest"                         % versions.scalaTest               % Test,
      "com.typesafe.akka"     %% "akka-testkit"                      % versions.akka                    % Test,
      "com.typesafe.akka"     %% "akka-stream-testkit"               % versions.akka                    % Test,
      "com.typesafe.akka"     %% "akka-http-testkit"                 % versions.akkaHttp                % Test,
      "com.github.tomakehurst" % "wiremock-jre8"                     % versions.wiremock                % Test,
      "org.mockito"           %% "mockito-scala"                     % versions.mockito                 % Test,
      "org.mockito"           %% "mockito-scala-scalatest"           % versions.mockito                 % Test,
      "org.mockito"            % "mockito-inline"                    % versions.mockitoInline           % Test
    )
  )
  .dependsOn(proto)

lazy val proto = (project in file("./proto"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream"        % versions.akka,
      "com.typesafe.akka" %% "akka-http"          % versions.akkaHttp,
      "com.typesafe.akka" %% "akka-http-core"     % versions.akkaHttp,
      "com.typesafe.akka" %% "akka-http2-support" % versions.akkaHttp
    ),
    coverageEnabled := false,
    akkaGrpcCodeGeneratorSettings += "single_line_to_proto_string"
  )
  .enablePlugins(AkkaGrpcPlugin)

lazy val excludedWarts = Seq(
  Wart.Any // too many false positives; more info - https://github.com/wartremover/wartremover/issues/454
)

lazy val commonSettings = Seq(
  Test / logBuffered       := false,
  Test / parallelExecution := false,
  Compile / compile / wartremoverWarnings ++= Warts.unsafe.filterNot(excludedWarts.contains),
  artifact                 := {
    val previous: Artifact = artifact.value
    previous.withName(name = s"$projectName-${previous.name}")
  },
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-extra-implicit",
    "-Ywarn-unused:implicits",
    "-Xlint:constant",
    "-Xlint:delayedinit-select",
    "-Xlint:doc-detached",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    s"-P:wartremover:excluded:${(Compile / sourceManaged).value}"
  )
)

lazy val dockerSettings = Seq(
  packageName          := s"$projectName-${name.value}",
  executableScriptName := s"$projectName-${name.value}"
)

addCommandAlias(
  name = "prepare",
  value = "; clean; compile; test:compile"
)

addCommandAlias(
  name = "check",
  value = "; dependencyUpdates; scalafmtSbtCheck; scalafmtCheck; test:scalafmtCheck"
)

addCommandAlias(
  name = "qa",
  value = "; prepare; check; coverage; test; coverageReport; coverageAggregate"
)
