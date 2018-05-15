name := "sbt-elastic-beanstalk-plugin"
organization := "com.ovoenergy"
organizationName := "OVO Energy"
version := "2.0.2"
scalaVersion := "2.12.4"
sbtPlugin := true

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.2")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-elasticbeanstalk" % "1.11.256",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.256")

bintrayOrganization := Some("ovotech")
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
