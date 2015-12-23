name := "sbt-aws-plugin"

version := "1.0.0"

organization := "com.ovoenergy"

scalaVersion := "2.10.4"

sbtPlugin := true

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.3")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-elasticbeanstalk" % "1.10.43",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.10.43")