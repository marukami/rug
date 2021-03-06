package com.atomist.rug.kind.properties

import com.atomist.project.SimpleProjectOperationArguments
import com.atomist.project.edit.{ModificationAttempt, SuccessfulModification}
import com.atomist.rug.kind.java.JavaTypeUsageTest
import com.atomist.source.{ArtifactSource, EmptyArtifactSource}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

class PropertiesUsageTest extends FlatSpec with Matchers with LazyLogging {

  import com.atomist.rug.TestUtils._

  it should "update an existing property" in {
    val prog =
      """
        |editor PropertiesEdit
        |
        |with Properties p when path = "src/main/resources/application.properties"
        |do setProperty "server.port" "8181"
      """.stripMargin

    updateWith(prog, JavaTypeUsageTest.NewSpringBootProject) match {
      case success: SuccessfulModification => logger.debug("" + success.impacts)
    }
  }

  it should "create a new property" in {
    val prog =
      """
        |editor PropertiesEdit
        |
        |with Properties p when path = "src/main/resources/application.properties"
        |do setProperty "server.portlet" "8181"
      """.stripMargin

    updateWith(prog, JavaTypeUsageTest.NewSpringBootProject) match {
      case success: SuccessfulModification =>
    }
  }

  // Return new content
  private def updateWith(prog: String, project: ArtifactSource): ModificationAttempt = {
    val filename = "thing.yml"

    val newName = "Foo"
    attemptModification(prog, project, EmptyArtifactSource(""), SimpleProjectOperationArguments("", Map(
      "new_name" -> newName
    )))
  }
}
