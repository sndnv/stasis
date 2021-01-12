import sbt.Keys._

lazy val projectName = "stasis"

name := projectName
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/sndnv/stasis"))

scalaVersion in ThisBuild := "2.13.4"

lazy val akkaVersion     = "2.6.10"
lazy val akkaHttpVersion = "10.2.2"
lazy val geodeVersion    = "1.13.1"
lazy val slickVersion    = "3.3.3"
lazy val h2Version       = "1.4.200"
lazy val postgresVersion = "42.2.18"
lazy val mariadbVersion  = "2.7.1"
lazy val sqliteVersion   = "3.32.3.2"
lazy val logbackVersion  = "1.2.3"

lazy val jdkDockerImage = "openjdk:11"

lazy val server   = (project in file("./server"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-slf4j"          % akkaVersion,
      "ch.qos.logback"      % "logback-classic"     % logbackVersion,
      "com.typesafe.slick" %% "slick"               % slickVersion,
      "com.h2database"      % "h2"                  % h2Version,
      "org.postgresql"      % "postgresql"          % postgresVersion,
      "org.mariadb.jdbc"    % "mariadb-java-client" % mariadbVersion,
      "org.xerial"          % "sqlite-jdbc"         % sqliteVersion
    ),
    dockerBaseImage := jdkDockerImage
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(shared % "compile->compile;test->test")

lazy val client   = (project in file("./client"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "at.favre.lib"       % "hkdf"                    % "1.1.0",
      "net.harawata"       % "appdirs"                 % "1.2.0",
      "com.typesafe.akka" %% "akka-slf4j"              % akkaVersion,
      "com.typesafe.akka" %% "akka-http-caching"       % akkaHttpVersion,
      "ch.qos.logback"     % "logback-classic"         % logbackVersion,
      "com.github.scopt"  %% "scopt"                   % "4.0.0",
      "com.google.jimfs"   % "jimfs"                   % "1.2"     % Test,
      "org.mockito"       %% "mockito-scala"           % "1.16.15" % Test,
      "org.mockito"       %% "mockito-scala-scalatest" % "1.16.15" % Test,
      "org.mockito"        % "mockito-inline"          % "3.7.0"   % Test
    ),
    dockerBaseImage := jdkDockerImage,
    PB.targets in Compile := Seq(
      scalapb.gen(singleLineToProtoString = true) -> (sourceManaged in Compile).value
    ),
    coverageExcludedPackages := "stasis.client.model.proto.metadata.*"
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(shared % "compile->compile;test->test")

lazy val identity = (project in file("./identity"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-slf4j"          % akkaVersion,
      "ch.qos.logback"      % "logback-classic"     % logbackVersion,
      "com.typesafe.slick" %% "slick"               % slickVersion,
      "com.h2database"      % "h2"                  % h2Version,
      "org.postgresql"      % "postgresql"          % postgresVersion,
      "org.mariadb.jdbc"    % "mariadb-java-client" % mariadbVersion,
      "org.xerial"          % "sqlite-jdbc"         % sqliteVersion
    ),
    dockerBaseImage := jdkDockerImage
  )
  .dependsOn(core % "compile->compile;test->test")
  .enablePlugins(JavaAppPackaging)

lazy val shared = (project in file("./shared"))
  .settings(commonSettings)
  .dependsOn(core % "compile->compile;test->test")

lazy val core   = (project in file("./core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"     %% "akka-actor"          % akkaVersion,
      "com.typesafe.akka"     %% "akka-actor-typed"    % akkaVersion,
      "com.typesafe.akka"     %% "akka-stream"         % akkaVersion,
      "com.typesafe.akka"     %% "akka-discovery"      % akkaVersion,
      "com.typesafe.akka"     %% "akka-http"           % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-http-core"      % akkaHttpVersion,
      "com.typesafe.akka"     %% "akka-http2-support"  % akkaHttpVersion,
      "com.typesafe.play"     %% "play-json"           % "2.9.2",
      "de.heikoseeberger"     %% "akka-http-play-json" % "1.35.3",
      "org.bitbucket.b_c"      % "jose4j"              % "0.7.4",
      "org.apache.geode"       % "geode-core"          % geodeVersion    % Provided,
      "com.typesafe.slick"    %% "slick"               % slickVersion    % Provided,
      "com.h2database"         % "h2"                  % h2Version       % Test,
      "org.scalacheck"        %% "scalacheck"          % "1.15.2"        % Test,
      "org.scalatest"         %% "scalatest"           % "3.2.3"         % Test,
      "com.typesafe.akka"     %% "akka-testkit"        % akkaVersion     % Test,
      "com.typesafe.akka"     %% "akka-stream-testkit" % akkaVersion     % Test,
      "com.typesafe.akka"     %% "akka-http-testkit"   % akkaHttpVersion % Test,
      "com.github.tomakehurst" % "wiremock-jre8"       % "2.27.2"        % Test
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
  logBuffered in Test := false,
  parallelExecution in Test := false,
  wartremoverWarnings in (Compile, compile) ++= Warts.unsafe.filterNot(excludedWarts.contains),
  packageName := s"$projectName-${name.value}",
  executableScriptName := s"$projectName-${name.value}",
  artifact := {
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
    s"-P:wartremover:excluded:${(sourceManaged in Compile).value}"
  )
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
