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
  lazy val awsVersion = settingKey[String]("Version number to tag release with in elastic beanstalk")
  lazy val awsBucket = settingKey[String]("Name of the S3 bucket to publish docker configurations to")
  lazy val awsAuthBucket = settingKey[String]("Name of the S3 bucket containing the docker auth config")
  lazy val awsAuthKey = settingKey[String]("Name of the S3 file containing the docker auth config")
  lazy val awsRegion = settingKey[Regions]("Region for the elastic beanstalk application and S3 bucket")
  lazy val awsEbextensionsDir = settingKey[File]("Directory containing *.config files for advanced elastic beanstalk environment customisation. It's fine for this directory not to exist.")
  lazy val awsStage = taskKey[File]("Creates the Dockerrun.aws.json file")
  lazy val awsPublish = taskKey[Option[CreateApplicationVersionResult]]("Publishes the docker configuration to S3")
}

object AWSPlugin extends AutoPlugin with NativePackagerKeys with DockerKeys with AWSKeys {
  override def trigger = allRequirements

  override def requires = DockerPlugin

  object autoImport extends AWSKeys

  override lazy val projectSettings = Seq(
    awsVersion := version.value,

    awsAuthBucket := awsBucket.value,

    awsAuthKey := ".dockercfg",

    awsEbextensionsDir := baseDirectory.value / "ebextensions",

    awsStage := {
      val jsonFile = target.value / "aws" / "Dockerrun.aws.json"
      jsonFile.delete()
      IO.write(jsonFile, dockerRunFile.value)
      val jsonFileMapping = jsonFile -> "Dockerrun.aws.json"

      val ebextensionsFiles: Seq[java.io.File] = (awsEbextensionsDir.value * "*.config").get
      val ebextensionsFileMappings = ebextensionsFiles.map(f => (f -> s".ebextensions/${f.name}"))

      val zipFile = target.value / "aws" / s"${packageName.value}-${version.value}.zip"
      zipFile.delete()

      val zipContents = Seq(jsonFileMapping) ++ ebextensionsFileMappings 
      IO.zip(zipContents, zipFile)
      zipFile
    },

    awsPublish := {
      val key = s"${packageName.value}/${version.value}.zip"
      val zipFile = awsStage.value

      val bucket = awsBucket.value
      val s3Client = new AmazonS3Client()
      s3Client.setRegion(Region.getRegion(awsRegion.value))

      if (!s3Client.doesBucketExist(bucket)) {
        println(s"Bucket $bucket does not exist. Aborting")
        None
      }
      else {
        s3Client.putObject(bucket, key, zipFile)

        val ebClient = new AWSElasticBeanstalkClient()
        ebClient.setRegion(Region.getRegion(awsRegion.value))
        val applicationDescriptions = ebClient.describeApplications(new DescribeApplicationsRequest().withApplicationNames(packageName.value))

        if (applicationDescriptions.getApplications.exists(_.getVersions contains version.value)) {
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
