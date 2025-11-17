name := """NotiLytics"""
organization := "com.soen6441"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)
scalaVersion := "3.7.3"
// ==================== JAVA VERSION ====================
javacOptions ++= Seq(
  "-source", "21",
  "-target", "21",
  "-Xlint:unchecked",
  "-Xlint:deprecation",
  "-parameters"
)
// ==================== CORE DEPENDENCIES ====================
libraryDependencies += guice
libraryDependencies += ws

// ==================== PEKKO ACTORS (D2 NEW) ====================
lazy val pekkoVersion = "1.0.3"
libraryDependencies += "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion
libraryDependencies += "org.apache.pekko" %% "pekko-stream" % pekkoVersion
libraryDependencies += "org.apache.pekko" %% "pekko-slf4j" % pekkoVersion

// JUnit 4
libraryDependencies += "junit" % "junit" % "4.13.2" % Test

// Add JUnit 4 runner
libraryDependencies += "com.github.sbt" % "junit-interface" % "0.13.2" % Test

// Pekko TestKit (D2 NEW)
libraryDependencies += "org.apache.pekko" %% "pekko-testkit" % pekkoVersion % Test

// Caffeine Cache for session and data caching
libraryDependencies += "com.github.ben-manes.caffeine" % "caffeine" % "3.1.8"

// Mockito for mocking dependencies
libraryDependencies += "org.mockito" % "mockito-core" % "5.14.2" % Test
libraryDependencies += "org.mockito" % "mockito-junit-jupiter" % "5.14.2" % Test

// Play Test utilities
libraryDependencies += "org.playframework" %% "play-test" % "3.0.0" % Test

// AssertJ for fluent assertions (optional but recommended)
libraryDependencies += "org.assertj" % "assertj-core" % "3.24.2" % Test

// ==================== CODE COVERAGE ====================
// JaCoCo for code coverage
libraryDependencies += "org.jacoco" % "org.jacoco.core" % "0.8.12" % Test

// JaCoCo SBT Plugin settings
jacocoReportSettings := JacocoReportSettings(
  "Jacoco Coverage Report",
  None,
  JacocoThresholds(
    instruction = 0,
    method = 100, // required
    branch = 0,
    complexity = 0,
    line = 100, // required
    clazz = 100 // required
  ),
  Seq(JacocoReportFormats.XML, JacocoReportFormats.HTML),
  "utf-8"
)

// Exclude Play-generated files from coverage
jacocoExcludes := Seq(
  // Reverse routers
  "controllers.Reverse*",
  "controllers.javascript.*",
  "controllers.routes*",
  "controllers.routes$javascript*",
  // Routers
  "router.*",
  "*.routes.*",
  // Twirl templates (generated)
  "views.html.*"
)

// ==================== JAVADOC CONFIGURATION ====================
Compile / doc / javacOptions ++= Seq(
  "-Xdoclint:none",
  "-quiet",
  "-private",     // Include private methods
  "-author",      // Include @author tags
  "-version"      // Include @version tags
)

// Test docs (unit test sources)
Test / doc / javacOptions ++= Seq(
  "-Xdoclint:none",
  "-quiet",
  "-private",
  "-author",
  "-version"
)

// ==================== TEST CONFIGURATION ====================
Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
Test / parallelExecution := false
Test / fork := true

// ==================== RUN CONFIGURATION ====================
run / fork := true
run / javaOptions ++= Seq(
  "-Xms512M",
  "-Xmx2048M",
  "-Dconfig.file=conf/application.conf"
)

lazy val generateJavadoc = taskKey[Unit]("Generate Javadoc")

generateJavadoc := {
  val log = streams.value.log
  val cp = (Compile / dependencyClasspath).value.files.mkString(":")
  val sourcePath = (Compile / javaSource).value
  val outDir = target.value / "javadoc"

  log.info("Generating Javadoc...")

  val javadocCmd = Seq(
    "javadoc",
    "-protected",
    "-splitindex",
    "-d", outDir.getAbsolutePath,
    "-sourcepath", sourcePath.getAbsolutePath,
    "-classpath", cp,
    "-subpackages", "models:controllers:services:modules:actors",
    "-Xdoclint:none"
  )

  import scala.sys.process._
  val result = javadocCmd.!

  if (result == 0) {
    log.info("Javadoc generated successfully in ${outDir.getAbsolutePath}")
  } else {
    log.error("Javadoc generation failed !!!")
  }
}
