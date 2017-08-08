name := """play-rest-demo"""
organization := "com.bijansoft"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.3"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.bijansoft.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.bijansoft.binders._"

//scalacOptions ++= Seq(
//  //  "-Xprint:parser",
//  "-deprecation",
//  "-encoding", "UTF-8",
//  "-feature",
//  "-unchecked",
//  "-language:higherKinds",
//  "-language:implicitConversions",
//  "-Xlint",
//  "-Yno-adapted-args",
//  "-Ywarn-dead-code",
//  "-Ywarn-numeric-widen",
//  "-Ywarn-value-discard",
//  "-Xfuture",
//  "-opt:l:classpath",
//  "-Ywarn-unused-import"
//)

