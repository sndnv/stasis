import sbt.Keys.*

lazy val projectName = "stasis"

name     := projectName
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/sndnv/stasis"))

ThisBuild / scalaVersion := "2.13.15"

lazy val versions = new {
  // pekko
  val pekko         = "1.1.2"
  val pekkoHttp     = "1.1.0"
  val pekkoHttpCors = "1.1.0"
  val pekkoJson     = "3.0.0"

  // persistence
  val slick    = "3.5.2"
  val postgres = "42.7.4"
  val mariadb  = "3.4.1"
  val sqlite   = "3.46.1.3"
  val h2       = "2.3.232"

  // telemetry
  val openTelemetry           = "1.43.0"
  val openTelemetryPrometheus = "1.43.0-alpha"
  val prometheus              = "0.16.0"

  // testing
  val scalaCheck    = "1.18.1"
  val scalaTest     = "3.2.19"
  val wiremock      = "3.0.1"
  val mockito       = "1.17.37"
  val mockitoInline = "5.2.0"
  val jimfs         = "1.3.0"

  // misc
  val playJson     = "2.10.6"
  val jose4j       = "0.9.6"
  val hkdf         = "2.0.0"
  val appdirs      = "1.2.2"
  val scopt        = "4.1.0"
  val logback      = "1.5.11"
  val systemTray   = "4.4"
  val bouncycastle = "1.78.1"
}

lazy val jdkDockerImage = "eclipse-temurin:21-noble"
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
      "com.dorkbox"       % "SystemTray"                        % versions.systemTray,
      "org.bouncycastle"  % "bcprov-jdk18on"                    % versions.bouncycastle,
      "org.bouncycastle"  % "bcpkix-jdk18on"                    % versions.bouncycastle
    ),
    dockerBaseImage          := jdkDockerImage,
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

lazy val layers = (project in file("./layers"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.pekko"      %% "pekko-actor"                       % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-actor-typed"                 % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-stream"                      % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-discovery"                   % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-slf4j"                       % versions.pekko                   % Provided,
      "org.apache.pekko"      %% "pekko-http"                        % versions.pekkoHttp               % Provided,
      "org.apache.pekko"      %% "pekko-http-core"                   % versions.pekkoHttp               % Provided,
      "com.typesafe.play"     %% "play-json"                         % versions.playJson                % Provided,
      "com.github.pjfanning"  %% "pekko-http-play-json"              % versions.pekkoJson               % Provided,
      "org.bitbucket.b_c"      % "jose4j"                            % versions.jose4j                  % Provided,
      "io.opentelemetry"       % "opentelemetry-api"                 % versions.openTelemetry           % Provided,
      "ch.qos.logback"         % "logback-classic"                   % versions.logback                 % Provided,
      "io.opentelemetry"       % "opentelemetry-sdk"                 % versions.openTelemetry           % Provided,
      "io.opentelemetry"       % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus % Provided,
      "io.prometheus"          % "simpleclient"                      % versions.prometheus              % Provided,
      "com.typesafe.slick"    %% "slick"                             % versions.slick                   % Test,
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
      "org.mockito"            % "mockito-inline"                    % versions.mockitoInline           % Test
    )
  )
  .dependsOn(proto, layers % "compile->compile;test->test")

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
  )
)

lazy val dockerSettings = Seq(
  packageName          := s"$projectName-${name.value}",
  executableScriptName := s"$projectName-${name.value}",
  dockerRepository     := Some(dockerRegistry),
  dockerExecCommand    := Seq(findDockerExecCommand())
)

def findDockerExecCommand(): String = {
  import java.io.File

  import scala.util.Try

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
