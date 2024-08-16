import sbt.Keys._

lazy val projectName = "stasis"

name     := projectName
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/sndnv/stasis"))

ThisBuild / scalaVersion := "2.13.14"

lazy val versions = new {
  // pekko
  val pekko         = "1.0.2"
  val pekkoHttp     = "1.0.1"
  val pekkoHttpCors = "1.0.1"
  val pekkoJson     = "2.5.0"

  // persistence
  val geode    = "1.15.1"
  val slick    = "3.5.1"
  val postgres = "42.7.3"
  val mariadb  = "3.3.3"
  val sqlite   = "3.45.3.0"
  val h2       = "2.2.224"

  // telemetry
  val openTelemetry           = "1.37.0"
  val openTelemetryPrometheus = "1.37.0-alpha"
  val prometheus              = "0.16.0"

  // testing
  val scalaCheck    = "1.18.0"
  val scalaTest     = "3.2.18"
  val wiremock      = "3.0.1"
  val mockito       = "1.17.31"
  val mockitoInline = "5.2.0"
  val jimfs         = "1.3.0"

  // misc
  val playJson   = "2.10.5"
  val jose4j     = "0.9.6"
  val hkdf       = "2.0.0"
  val appdirs    = "1.2.2"
  val scopt      = "4.1.0"
  val logback    = "1.5.6"
  val systemTray = "4.4"
}

lazy val jdkDockerImage = "openjdk:17-slim-bullseye"
lazy val dockerRegistry = "ghcr.io/sndnv/stasis"

lazy val server = (project in file("./server"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick"                             % versions.slick,
      "com.h2database"      % "h2"                                % versions.h2,
      "org.postgresql"      % "postgresql"                        % versions.postgres,
      "org.mariadb.jdbc"    % "mariadb-java-client"               % versions.mariadb,
      "org.xerial"          % "sqlite-jdbc"                       % versions.sqlite,
      "org.apache.pekko"   %% "pekko-http-cors"                   % versions.pekkoHttpCors,
      "io.opentelemetry"    % "opentelemetry-sdk"                 % versions.openTelemetry,
      "io.opentelemetry"    % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus,
      "io.prometheus"       % "simpleclient_hotspot"              % versions.prometheus
    ),
    dockerBaseImage := jdkDockerImage
  )
  .enablePlugins(JavaAppPackaging, BuildInfoPlugin)
  .dependsOn(shared % "compile->compile;test->test")

lazy val client = (project in file("./client"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      "at.favre.lib"      % "hkdf"                              % versions.hkdf,
      "net.harawata"      % "appdirs"                           % versions.appdirs,
      "org.apache.pekko" %% "pekko-http-caching"                % versions.pekkoHttp,
      "com.github.scopt" %% "scopt"                             % versions.scopt,
      "io.opentelemetry"  % "opentelemetry-sdk"                 % versions.openTelemetry,
      "io.opentelemetry"  % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus,
      "io.prometheus"     % "simpleclient_hotspot"              % versions.prometheus,
      "com.dorkbox"       % "SystemTray"                        % versions.systemTray
    ),
    dockerBaseImage          := jdkDockerImage,
    Universal / javaOptions ++= Seq("-J--add-opens=java.base/sun.security.x509=ALL-UNNAMED"),
    Compile / PB.targets     := Seq(
      scalapb.gen(singleLineToProtoString = true) -> (Compile / sourceManaged).value
    ),
    coverageExcludedPackages := "stasis.client.model.proto.*"
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(shared % "compile->compile;test->test")

lazy val identity = (project in file("./identity"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick"                             % versions.slick,
      "com.h2database"      % "h2"                                % versions.h2,
      "org.postgresql"      % "postgresql"                        % versions.postgres,
      "org.mariadb.jdbc"    % "mariadb-java-client"               % versions.mariadb,
      "org.xerial"          % "sqlite-jdbc"                       % versions.sqlite,
      "org.apache.pekko"   %% "pekko-http-cors"                   % versions.pekkoHttpCors,
      "io.opentelemetry"    % "opentelemetry-sdk"                 % versions.openTelemetry,
      "io.opentelemetry"    % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus,
      "io.prometheus"       % "simpleclient_hotspot"              % versions.prometheus
    ),
    dockerBaseImage := jdkDockerImage
  )
  .dependsOn(core % "compile->compile;test->test")
  .enablePlugins(JavaAppPackaging, BuildInfoPlugin)

lazy val shared = (project in file("./shared"))
  .settings(commonSettings)
  .dependsOn(core % "compile->compile;test->test")

lazy val core = (project in file("./core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.pekko"      %% "pekko-actor"                       % versions.pekko,
      "org.apache.pekko"      %% "pekko-actor-typed"                 % versions.pekko,
      "org.apache.pekko"      %% "pekko-stream"                      % versions.pekko,
      "org.apache.pekko"      %% "pekko-discovery"                   % versions.pekko,
      "org.apache.pekko"      %% "pekko-slf4j"                       % versions.pekko,
      "org.apache.pekko"      %% "pekko-http"                        % versions.pekkoHttp,
      "org.apache.pekko"      %% "pekko-http-core"                   % versions.pekkoHttp,
      "com.typesafe.play"     %% "play-json"                         % versions.playJson,
      "com.github.pjfanning"  %% "pekko-http-play-json"              % versions.pekkoJson,
      "org.bitbucket.b_c"      % "jose4j"                            % versions.jose4j,
      "io.opentelemetry"       % "opentelemetry-api"                 % versions.openTelemetry,
      "ch.qos.logback"         % "logback-classic"                   % versions.logback,
      "io.opentelemetry"       % "opentelemetry-sdk"                 % versions.openTelemetry           % Provided,
      "io.opentelemetry"       % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus % Provided,
      "io.prometheus"          % "simpleclient"                      % versions.prometheus              % Provided,
      "org.apache.geode"       % "geode-core"                        % versions.geode                   % Provided,
      "com.typesafe.slick"    %% "slick"                             % versions.slick                   % Provided,
      "com.h2database"         % "h2"                                % versions.h2                      % Test,
      "org.scalacheck"        %% "scalacheck"                        % versions.scalaCheck              % Test,
      "org.scalatest"         %% "scalatest"                         % versions.scalaTest               % Test,
      "org.apache.pekko"      %% "pekko-testkit"                     % versions.pekko                   % Test,
      "org.apache.pekko"      %% "pekko-stream-testkit"              % versions.pekko                   % Test,
      "org.apache.pekko"      %% "pekko-http-testkit"                % versions.pekkoHttp               % Test,
      "com.github.tomakehurst" % "wiremock-jre8"                     % versions.wiremock                % Test,
      "org.mockito"           %% "mockito-scala"                     % versions.mockito                 % Test,
      "org.mockito"           %% "mockito-scala-scalatest"           % versions.mockito                 % Test,
      "org.mockito"            % "mockito-inline"                    % versions.mockitoInline           % Test,
      "com.google.jimfs"       % "jimfs"                             % versions.jimfs                   % Test
    )
  )
  .dependsOn(proto)

lazy val proto = (project in file("./proto"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-stream"    % versions.pekko,
      "org.apache.pekko" %% "pekko-http"      % versions.pekkoHttp,
      "org.apache.pekko" %% "pekko-http-core" % versions.pekkoHttp
    ),
    coverageEnabled := false,
    pekkoGrpcCodeGeneratorSettings += "single_line_to_proto_string",
    dependencyUpdatesFilter -= moduleFilter(organization = "org.apache.pekko")
  )
  .enablePlugins(PekkoGrpcPlugin)

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
  ),
  dependencyUpdatesFilter -= moduleFilter(organization = "org.apache.pekko")
)

lazy val dockerSettings = Seq(
  packageName          := s"$projectName-${name.value}",
  executableScriptName := s"$projectName-${name.value}",
  dockerRepository     := Some(dockerRegistry),
  dockerExecCommand    := Seq(findDockerExecCommand())
)

def findDockerExecCommand(): String = {
  import scala.util.Try
  import java.io.File

  val pathEntries = Try(Option(System.getenv("PATH"))).toOption.flatten
    .getOrElse("")
    .split(File.pathSeparator)
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSeq

  def exists(command: String): Boolean = pathEntries.exists { path =>
    new File(path, command).isFile
  }

  Seq("podman").find(exists).getOrElse("docker")
}

lazy val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](
    name,
    version,
    BuildInfoKey.action("time") { System.currentTimeMillis }
  ),
  buildInfoPackage := s"stasis.${name.value}"
)

Global / concurrentRestrictions += Tags.limit(
  Tags.Test,
  sys.env.getOrElse("CI", "").trim.toLowerCase match {
    case "true" => 1
    case _      => 2
  }
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
