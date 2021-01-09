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

// CI config
val PrimaryJava = "adopt@1.8"

inThisBuild(
  Seq(
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches ++= Seq(
      RefPredicate.Equals(Ref.Branch("main")),
      RefPredicate.StartsWith(Ref.Tag("v")),
    ),
    githubWorkflowJavaVersions := Seq(
      "adopt@1.8",
      "adopt@1.11",
      "adopt@1.15",
      "graalvm-ce-java8@20.2.0",
    ),
    githubWorkflowBuild := Seq(
      WorkflowStep.Sbt(List("compile", "test:compile"), name = Some("Compile")),
    ),
    githubWorkflowAddedJobs ++= Seq(
      WorkflowJob(
        "test",
        "Test",
        githubWorkflowJobSetup.value.toList ::: List(
          WorkflowStep.Sbt(List("test"), name = Some("Test")),
        ),
        javas = githubWorkflowJavaVersions.value.toList,
        scalas = crossScalaVersions.value.toList,
        matrixFailFast = Some(false),
      ),
      WorkflowJob(
        "bincompat",
        "Binary Compatibility",
        githubWorkflowJobSetup.value.toList ::: List(
          WorkflowStep.Sbt(List("clean", "mimaReportBinaryIssues"), name = Some("MiMa")),
        ),
        javas = List(PrimaryJava),
        scalas = crossScalaVersions.value.toList,
        matrixFailFast = Some(false),
      ),
      WorkflowJob(
        "coverage",
        "Coverage",
        githubWorkflowJobSetup.value.toList ::: List(
          WorkflowStep.Sbt(
            List("coverage", "test", "coverageAggregate", "coveralls"), name = Some("Coverage"),
          ),
        ),
        env = Map("COVERALLS_REPO_TOKEN" -> "${{ secrets.COVERALLS_REPO_TOKEN }}"),
        cond = Some("env.COVERALLS_REPO_TOKEN != ''"),
        javas = List(PrimaryJava),
        scalas = crossScalaVersions.value.toList,
        matrixFailFast = Some(false),
      ),
      WorkflowJob(
        "formatting",
        "Formatting",
        githubWorkflowJobSetup.value.toList ::: List(
          WorkflowStep.Sbt(List("scalafmtCheckAll", "scalafmtSbtCheck"), name = Some("Formatting")),
        ),
        javas = List(PrimaryJava),
        scalas = List(scalaVersion.value),
        matrixFailFast = Some(false),
      ),
      WorkflowJob(
        "check-docs",
        "Check Docs",
        githubWorkflowJobSetup.value.toList ::: List(
          WorkflowStep.Sbt(List("doc"), name = Some("Check Scaladocs")),
        ),
        javas = List(PrimaryJava),
        scalas = List(scalaVersion.value),
        matrixFailFast = Some(false),
      ),
    ),
    githubWorkflowPublishPreamble += WorkflowStep.Use("olafurpg", "setup-gpg", "v3"),
    githubWorkflowPublish := Seq(
      WorkflowStep.Sbt(
        List("ci-release"),
        env = Map(
          "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
        ),
      ),
    ),
  ),
)

val akkaVersion = "2.6.10"
val sharedSettings = Seq(
  mimaPreviousArtifacts := Set().map(organization.value %% name.value % _),
  mimaFailOnNoPrevious := true,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "org.scalatest"     %% "scalatest"   % "3.2.2" % Test,
  ),
  scalacOptions ++= Seq(
    "-Xlint",
    "-feature",
    "-Werror",
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
