# SBT Elastic Beanstalk Plugin
[![CircleCI](https://img.shields.io/circleci/project/github/ovotech/sbt-elastic-beanstalk-plugin.svg)](https://circleci.com/gh/ovotech/sbt-elastic-beanstalk-plugin)
[![Download](https://api.bintray.com/packages/ovotech/sbt-plugins/sbt-elastic-beanstalk-plugin/images/download.svg) ](https://bintray.com/ovotech/sbt-plugins/sbt-elastic-beanstalk-plugin/_latestVersion)

SBT Elastic Beanstalk Plugin lets you create AWS Elastic Beanstalk `Dockerrun.aws.json` files and publish them to S3 from within your SBT build.

## Installation
Add the following to your project/plugins.sbt file:
```
resolvers += Resolver.bintrayIvyRepo("ovotech", "sbt-plugins")
addSbtPlugin("com.ovoenergy" % "sbt-elastic-beanstalk-plugin" % "2.0.0")
```
This plugin depends on [sbt-native-packager](https://github.com/sbt/sbt-native-packager) so you do not need to add that plugin explicitly. However you will need to enable the packaging format you want in your `build.sbt`
```
enablePlugins(JavaServerAppPackaging, DockerPlugin)
```

## Run
The plugin exposes two SBT tasks:
- `awsStage` creates the Dockerrun.aws.json file (without publishing)
- `awsPublish` publishes the docker configuration to S3

Note you do not need to explicitly call `awsStage`. Calling `awsPublish` on its own will create the file before publishing to S3.

## Configuration
SBT Elastic Beanstalk Plugin exposes the following settings:

| Name                  | Description                                                     | Notes                         |
| ------------------    | --------------------------------------------------------------- | ----------------------------- |
| awsBucket             | Name of the S3 bucket to publish docker configurations to       | *mandatory*                   |
| awsRegion             | Region for the elastic beanstalk application and S3 bucket      | *mandatory*                   |
| awsVersion            | Version number to tag release with in elastic beanstalk         | defaults to the __version__   |
| awsDockerAuthLocation | Name of the S3 bucket + key containing a docker auth config     | defaults to None              |
| awsEbextensionsDir    | Directory containing \*.config files for advanced customisation | defaults to __ebextensions__  |
