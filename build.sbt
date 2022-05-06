import sbt.Keys._

lazy val projectName = "stasis"

name     := projectName
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/sndnv/stasis"))

ThisBuild / scalaVersion := "2.13.8"

lazy val akkaVersion         = "2.6.19"
lazy val akkaHttpVersion     = "10.2.9"
lazy val akkaHttpCorsVersion = "1.1.3"
lazy val geodeVersion        = "1.14.4"
lazy val slickVersion        = "3.3.3"
lazy val h2Version           = "2.1.212"
lazy val postgresVersion     = "42.3.5"
lazy val mariadbVersion      = "3.0.4"
lazy val sqliteVersion       = "3.36.0.3"
lazy val logbackVersion      = "1.2.11"

lazy val jdkDockerImage = "openjdk:11"

lazy val server = (project in file("./server"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-slf4j"          % akkaVersion,
      "ch.qos.logback"      % "logback-classic"     % logbackVersion,
      "com.typesafe.slick" %% "slick"               % slickVersion,
      "com.h2database"      % "h2"                  % h2Version,
      "org.postgresql"      % "postgresql"          % postgresVersion,
      "org.mariadb.jdbc"    % "mariadb-java-client" % mariadbVersion,
      "org.xerial"          % "sqlite-jdbc"         % sqliteVersion,
      "ch.megard"          %% "akka-http-cors"      % akkaHttpCorsVersion
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
      "at.favre.lib"       % "hkdf"              % "1.1.0",
      "net.harawata"       % "appdirs"           % "1.2.1",
      "com.typesafe.akka" %% "akka-slf4j"        % akkaVersion,
      "com.typesafe.akka" %% "akka-http-caching" % akkaHttpVersion,
      "ch.qos.logback"     % "logback-classic"   % logbackVersion,
      "com.github.scopt"  %% "scopt"             % "4.0.1",
      "com.google.jimfs"   % "jimfs"             % "1.2" % Test
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
      "com.typesafe.akka"  %% "akka-slf4j"          % akkaVersion,
      "ch.qos.logback"      % "logback-classic"     % logbackVersion,
      "com.typesafe.slick" %% "slick"               % slickVersion,
      "com.h2database"      % "h2"                  % h2Version,
      "org.postgresql"      % "postgresql"          % postgresVersion,
      "org.mariadb.jdbc"    % "mariadb-java-client" % mariadbVersion,
      "org.xerial"          % "sqlite-jdbc"         % sqliteVersion,
      "ch.megard"          %% "akka-http-cors"      % akkaHttpCorsVersion
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
      "com.typesafe.akka"     %% "akka-actor"              % akkaVersion,
      "com.typesafe.akka"     %% "akka-actor-typed"        % akkaVersion,
      "com.typesafe.akka"     %% "akka-stream"             % akkaVersion,
      "com.typesafe.akka"     %% "akka-discovery"          % akkaVersion,
      "com.typesafe.akka"     %% "akka-http"               % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-http-core"          % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-http2-support"      % akkaHttpVersion,
      "com.typesafe.play"     %% "play-json"               % "2.9.2",
      "de.heikoseeberger"     %% "akka-http-play-json"     % "1.39.2",
      "org.bitbucket.b_c"      % "jose4j"                  % "0.7.12",
      "org.apache.geode"       % "geode-core"              % geodeVersion    % Provided,
      "com.typesafe.slick"    %% "slick"                   % slickVersion    % Provided,
      "com.h2database"         % "h2"                      % h2Version       % Test,
      "org.scalacheck"        %% "scalacheck"              % "1.16.0"        % Test,
      "org.scalatest"         %% "scalatest"               % "3.2.12"        % Test,
      "com.typesafe.akka"     %% "akka-testkit"            % akkaVersion     % Test,
      "com.typesafe.akka"     %% "akka-stream-testkit"     % akkaVersion     % Test,
      "com.typesafe.akka"     %% "akka-http-testkit"       % akkaHttpVersion % Test,
      "com.github.tomakehurst" % "wiremock-jre8"           % "2.33.2"        % Test,
      "org.mockito"           %% "mockito-scala"           % "1.17.5"        % Test,
      "org.mockito"           %% "mockito-scala-scalatest" % "1.17.5"        % Test,
      "org.mockito"            % "mockito-inline"          % "4.5.1"         % Test
    )
  )
  .dependsOn(proto)

lazy val proto = (project in file("./proto"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream"        % akkaVersion,
      "com.typesafe.akka" %% "akka-http"          % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-core"     % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion
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
