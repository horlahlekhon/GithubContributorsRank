name := "GithubRanks"

version := "0.1"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.6.8"

lazy val akkaHttpVersion = "10.2.3"

lazy val scalaTestVersion = "3.0.5"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature"
)
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "com.typesafe.akka" %% "akka-http-caching" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-http-caching" % akkaHttpVersion,
  "org.mockito" % "mockito-core" % "2.8.47" % Test,


  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,

  "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime


)
