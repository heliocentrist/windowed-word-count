name := "windowed-word-count"

version := "0.1"

scalaVersion := "2.13.3"

scalastyleConfig := baseDirectory.value / "project" / "scalastyle_config.xml"

ivyXML :=
  <dependencies>
    <exclude module="log4j"/>
    <exclude module="commons-logging"/>
  </dependencies>

libraryDependencies ++= {
  val http4sVersion = "0.21.5"
  val circeVersion = "0.12.3"
  Seq(
    "org.http4s"                 %% "http4s-blaze-server"    % http4sVersion,
    "org.http4s"                 %% "http4s-dsl"             % http4sVersion,
    "io.circe"                   %% "circe-core"             % circeVersion,
    "io.circe"                   %% "circe-generic"          % circeVersion,
    "io.circe"                   %% "circe-parser"           % circeVersion,
    "io.monix"                   %% "monix"                  % "3.2.2",
    "ch.qos.logback"              % "logback-classic"        % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging"          % "3.9.2",
    "com.typesafe"                % "config"                 % "1.4.0",
    "org.scalatest"              %% "scalatest"              % "3.2.0"          % "test"
  )
}