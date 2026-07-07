name := "itr-compiler"
organization := "itrcompiler"
version := "0.1.0"
scalaVersion := "2.13.15"

mainClass := Some("itrcompiler.control.entry_point.App")

resolvers ++= Seq(
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Maven Central" at "https://repo1.maven.org/maven2/"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test
)

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-encoding", "UTF-8"
)

Test / fork := true

assembly / assemblyJarName := "itr-compiler"
assembly / assemblyOutputPath := baseDirectory.value / "itr-compiler"
assembly / assemblyPrependShellScript := Some(
  Seq(
    "#!/usr/bin/env sh",
    """exec java -jar "$0" "$@""""
  )
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) =>
    xs match {
      case "MANIFEST.MF" :: Nil       => MergeStrategy.discard
      case "DEPENDENCIES" :: Nil      => MergeStrategy.discard
      case "LICENSE" :: Nil           => MergeStrategy.discard
      case "LICENSE.txt" :: Nil       => MergeStrategy.discard
      case "NOTICE" :: Nil            => MergeStrategy.discard
      case "NOTICE.txt" :: Nil        => MergeStrategy.discard
      case "README.md" :: Nil         => MergeStrategy.discard
      case _                          => MergeStrategy.first
    }
  case _ => MergeStrategy.first
}
