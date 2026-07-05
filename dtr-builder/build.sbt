name := "dtr-builder"
organization := "dtrbuilder"
version := "0.1.1"
scalaVersion := "2.13.15"

enablePlugins(Antlr4Plugin)

// ANTLR4 grammar compilation settings
// .g4 files in src/main/antlr4/ are compiled to Java sources under target/
Antlr4 / antlr4Version := "4.13.2"
Antlr4 / antlr4PackageName := Some("dtrbuilder.infrastructure.ast.antlr")
Antlr4 / antlr4GenVisitor := true
Antlr4 / antlr4GenListener := true

mainClass := Some("dtrbuilder.control.DtrApp")

// Dependency resolution
resolvers ++= Seq(
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Maven Central" at "https://repo1.maven.org/maven2/"
)

// Core dependencies
libraryDependencies ++= Seq(
  // AST parsing — Scala
  "org.scalameta" %% "scalameta" % "4.12.6",

  // AST parsing — Java
  "com.github.javaparser" % "javaparser-core" % "3.26.2",

  // AST parsing — ANTLR4 runtime for Python/JS/TS
  "org.antlr" % "antlr4-runtime" % "4.13.2",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test
)

// Compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-encoding", "UTF-8"
)

// Fork for test execution
Test / fork := true

// Assembly settings
assembly / assemblyJarName := s"${name.value}-${version.value}.jar"
assembly / outputPath := baseDirectory.value / s"${name.value}-${version.value}.jar"

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
