name := "commit-tool"
organization := "commitool"
scalaVersion := "2.13.15"

mainClass := Some("commitool.control.entrypoint.CommitToolApp")

assembly / assemblyOutputPath := baseDirectory.value / "commit-tool"

assembly / assemblyJarName := "commit-tool"
assembly / assemblyOutputPath := baseDirectory.value / "commit-tool"
assembly / assemblyPrependShellScript := Some(Seq(
  "#!/usr/bin/env sh",
  """exec java -jar "$0" "$@""""
))

resolvers ++= Seq(
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Maven Central" at "https://repo1.maven.org/maven2/"
)

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-encoding", "UTF-8"
)

Test / fork := true

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.scalamock" %% "scalamock" % "6.0.0" % Test
)