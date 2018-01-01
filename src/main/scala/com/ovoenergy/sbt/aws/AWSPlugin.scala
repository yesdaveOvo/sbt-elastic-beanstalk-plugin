package com.ovoenergy.sbt.aws

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder
import com.amazonaws.services.elasticbeanstalk.model._
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.typesafe.sbt.packager.NativePackagerKeys
import com.typesafe.sbt.packager.docker.{DockerKeys, DockerPlugin}
import sbt.Keys._
import sbt._

import scala.collection.JavaConverters._

trait AWSKeys {
  lazy val awsVersion = settingKey[String]("Version number to tag release with in elastic beanstalk")
  lazy val awsBucket = settingKey[String]("Name of the S3 bucket to publish docker configurations to")
  lazy val awsAuthBucket = settingKey[String]("Name of the S3 bucket containing the docker auth config")
  lazy val awsAuthKey = settingKey[String]("Name of the S3 file containing the docker auth config")
  lazy val awsRegion = settingKey[Regions]("Region for the elastic beanstalk application and S3 bucket")
  lazy val awsStage = taskKey[File]("Creates the Dockerrun.aws.json file")
  lazy val awsPublish = taskKey[Option[CreateApplicationVersionResult]]("Publishes the docker configuration to S3")
}

object AWSPlugin extends AutoPlugin with NativePackagerKeys with DockerKeys with AWSKeys {
  override def trigger = allRequirements

  override def requires: Plugins = DockerPlugin

  object autoImport extends AWSKeys

  override lazy val projectSettings = Seq(
    awsVersion := version.value,

    awsAuthBucket := awsBucket.value,

    awsAuthKey := ".dockercfg",

    awsStage := {
      val jsonFile = target.value / "aws" / "Dockerrun.aws.json"
      val zipFile = target.value / "aws" / s"${packageName.value}-${version.value}.zip"
      jsonFile.delete()
      zipFile.delete()
      IO.write(jsonFile, dockerRunFile.value)
      IO.zip(Seq(jsonFile -> "Dockerrun.aws.json"), zipFile)
      zipFile
    },

    awsPublish := {
      val key = s"${packageName.value}/${version.value}.zip"
      val zipFile = awsStage.value

      val bucket = awsBucket.value
      val s3Client = AmazonS3ClientBuilder.defaultClient()
      s3Client.setRegion(Region.getRegion(awsRegion.value))

      if (!s3Client.doesBucketExistV2(bucket)) {
        println(s"Bucket $bucket does not exist. Aborting")
        None
      }
      else {
        s3Client.putObject(bucket, key, zipFile)

        val ebClient = AWSElasticBeanstalkClientBuilder.standard().withRegion(awsRegion.value).build()
        val applicationDescriptions = ebClient.describeApplications(new DescribeApplicationsRequest().withApplicationNames(packageName.value))

        if (applicationDescriptions.getApplications.asScala.exists(_.getVersions contains version.value)) {
          println("Version already exists in Elastic Beanstalk. Removing first...")
          ebClient.deleteApplicationVersion(new DeleteApplicationVersionRequest(packageName.value, version.value))
        }

        val createRequest = new CreateApplicationVersionRequest()
          .withApplicationName(packageName.value)
          .withDescription(version.value)
          .withVersionLabel(awsVersion.value)
          .withSourceBundle(new S3Location(awsBucket.value, key))
        Some(ebClient.createApplicationVersion(createRequest))
      }
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
        |  "Authentication": {
        |    "Bucket": "${awsAuthBucket.value}",
        |    "Key": "${awsAuthKey.value}"
        |  },
        |  "Ports": [
        |    {
        |      "ContainerPort": "8080"
        |    }
        |  ]
        |}""".stripMargin
  }
}