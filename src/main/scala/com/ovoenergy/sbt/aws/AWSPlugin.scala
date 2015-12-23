package com.ovoenergy.sbt.aws

import com.amazonaws.regions.{Regions, Region}
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
import com.amazonaws.services.elasticbeanstalk.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.sbt.packager.NativePackagerKeys
import com.typesafe.sbt.packager.docker.{DockerKeys, DockerPlugin}
import sbt.Keys._
import sbt._

import scala.collection.JavaConversions._

trait AWSKeys {
  lazy val awsBucket = settingKey[String]("Name of the S3 bucket to publish docker configurations to")
  lazy val awsRegion = settingKey[Regions]("Region for the elastic beanstalk application and S3 bucket")
  lazy val awsStage = taskKey[File]("Creates the Dockerrun.aws.json file")
  lazy val awsPublishVersion = taskKey[String]("Publishes the docker configuration to S3")
  lazy val awsCreateVersion = taskKey[CreateApplicationVersionResult]("Creates an elastic beanstalk application version")
}

object AWSPlugin extends AutoPlugin with NativePackagerKeys with DockerKeys with AWSKeys {
  override def trigger = allRequirements

  override def requires = DockerPlugin

  object autoImport extends AWSKeys

  override lazy val projectSettings = Seq(
    awsStage := {
      val jsonFile = target.value / "aws" / "Dockerrun.aws.json"
      val zipFile = target.value / "aws" / s"${packageName.value}-${version.value}.zip"
      jsonFile.delete()
      zipFile.delete()
      IO.write(jsonFile, dockerRunFile.value)
      IO.zip(Seq(jsonFile -> "Dockerrun.aws.json"), zipFile)
      zipFile
    },

    awsPublishVersion := {
      val key = s"${packageName.value}/${version.value}.zip"
      val zipFile = awsStage.value

      val bucket = awsBucket.value
      val client = new AmazonS3Client()
      client.setRegion(Region.getRegion(awsRegion.value))

      if (!client.doesBucketExist(bucket))
        println(s"Bucket $bucket does not exist. Aborting")
      else {
        client.putObject(bucket, key, zipFile)
      }

      key
    },

    awsCreateVersion := {
      val key = awsPublishVersion.value

      val client = new AWSElasticBeanstalkClient()
      client.setRegion(Region.getRegion(awsRegion.value))
      val applicationDescriptions = client.describeApplications(new DescribeApplicationsRequest().withApplicationNames(packageName.value))

      if (applicationDescriptions.getApplications.exists(_.getVersions contains version.value)) {
        println("Version already exists in Elastic Beanstalk. Removing first...")
        client.deleteApplicationVersion(new DeleteApplicationVersionRequest(packageName.value, version.value))
      }
      val createRequest = new CreateApplicationVersionRequest()
        .withApplicationName(packageName.value)
        .withAutoCreateApplication(true)
        .withVersionLabel(version.value)
        .withProcess(true)
        .withSourceBundle(new S3Location(awsBucket.value, key))
      client.createApplicationVersion(createRequest)
    }
  )

  lazy val dockerRunFile: Def.Initialize[String] = Def.setting {
    val imageName = dockerRepository.value match {
      case Some(repository) => s"$repository/${
        packageName.value
      }:${
        version.value
      }"
      case None => packageName.value
    }

    s"""|{
       |  "AWSEBDockerrunVersion": "1",
       |  "Image": {
       |    "Name": "$imageName"
       |  },
       |  "Ports": [
       |    {
       |      "ContainerPort": "8080"
       |    }
       |  ]
       |}""".stripMargin
  }
}