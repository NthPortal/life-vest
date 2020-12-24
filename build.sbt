ThisBuild / scalaVersion := "2.13.4"
ThisBuild / autoAPIMappings := true

// publishing info
inThisBuild(
  Seq(
    organization := "lgbt.princess",
    homepage := Some(url("https://github.com/NthPortal/life-vest")),
    licenses := Seq("The Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    developers := List(
      Developer(
        "NthPortal",
        "April | Princess",
        "dev@princess.lgbt",
        url("https://nthportal.com"),
      ),
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/NthPortal/life-vest"),
        "scm:git:git@github.com:NthPortal/life-vest.git",
        "scm:git:git@github.com:NthPortal/life-vest.git",
      ),
    ),
  ),
)

val akkaVersion = "2.6.10"
val sharedSettings = Seq(
  mimaPreviousArtifacts := Set().map(organization.value %% name.value % _),
  mimaFailOnNoPrevious := true,
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.2" % Test,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  ),
  scalacOptions ++= {
    if (isSnapshot.value) Nil
    else
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Seq("-opt:l:inline", "-opt-inline-from:lgbt.princess.lifevest.**")
        case _             => Nil
      }
  },
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "life-vest",
  )
  .settings(sharedSettings)
