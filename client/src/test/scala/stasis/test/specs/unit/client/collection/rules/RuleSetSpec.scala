package stasis.test.specs.unit.client.collection.rules

import java.nio.file.Path

import scala.concurrent.Future

import com.google.common.jimfs.Jimfs

import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.RuleSet
import stasis.client.service.ApplicationDirectory
import stasis.layers.UnitSpec
import stasis.shared.model.datasets.DatasetDefinition
import stasis.test.specs.unit.client.ResourceHelpers

class RuleSetSpec extends UnitSpec with ResourceHelpers {
  "A RuleSet" should "support loading from a list of rules files" in {
    RuleSet
      .fromFiles(files =
        Seq(
          "/ops/scheduling/test.rules".asTestResource,
          "/ops/scheduling/extra.rules".asTestResource
        )
      )
      .map { rules =>
        rules.definitions.toList.sortBy(_._1) match {
          case (None, actualDefaultRules) :: (Some(`expectedDefinition`), actualDefinitionRules) :: Nil =>
            actualDefaultRules should be(expectedDefaultRules)
            actualDefinitionRules should be(expectedDefinitionRules)

          case result =>
            fail(s"Unexpected result received: [$result]")
        }
      }
  }

  it should "support providing default rules" in {
    RuleSet
      .fromFiles(files =
        Seq(
          "/ops/scheduling/test.rules".asTestResource,
          "/ops/scheduling/extra.rules".asTestResource
        )
      )
      .map { rules =>
        rules.default() should be(expectedDefaultRules)
      }
  }

  it should "support providing rules for a definition" in {
    RuleSet
      .fromFiles(files =
        Seq(
          "/ops/scheduling/test.rules".asTestResource,
          "/ops/scheduling/extra.rules".asTestResource
        )
      )
      .map { rules =>
        rules.forDefinitionOrDefault(expectedDefinition) should be(expectedDefinitionRules)
      }
  }

  it should "support providing default rules when none could be found for a specific definition" in {
    RuleSet
      .fromFiles(files =
        Seq(
          "/ops/scheduling/test.rules".asTestResource,
          "/ops/scheduling/extra.rules".asTestResource
        )
      )
      .map { rules =>
        rules.forDefinitionOrDefault(DatasetDefinition.generateId()) should be(expectedDefaultRules)
      }
  }

  it should "fail to provide default rules if none are available" in {
    RuleSet
      .fromFiles(files = Seq("/ops/scheduling/extra.rules".asTestResource))
      .map(_.default())
      .failed
      .map { e =>
        e should be(an[IllegalStateException])
        e.getMessage should be("No default rules were found")
      }
  }

  it should "fail to provide missing rules for a definition and no defaults are available" in {
    RuleSet
      .fromFiles(files = Seq.empty)
      .map(_.forDefinitionOrDefault(expectedDefinition))
      .failed
      .map { e =>
        e should be(an[IllegalStateException])
        e.getMessage should be(s"No default rules or rules for definition [${expectedDefinition.toString}] were found")
      }
  }

  "A RuleSet Factory" should "support loading latest rules based on a pattern" in {
    val filesystem = Jimfs.newFileSystem()

    val expectedPattern = "**/*.rules"

    val directory = new ApplicationDirectory.Default(
      applicationName = "test-app",
      filesystem = filesystem
    ) {
      override def requireFiles(pattern: String): Future[Seq[Path]] = Future {
        pattern should be(expectedPattern)
        Seq(
          "/ops/scheduling/test.rules".asTestResource
        )
      }
    }

    val factory = RuleSet.Factory(directory = directory, pattern = expectedPattern)

    factory
      .latest()
      .map { rules =>
        rules.definitions.toList match {
          case (None, actualDefaultRules) :: Nil =>
            actualDefaultRules should be(expectedDefaultRules)

          case result =>
            fail(s"Unexpected result received: [$result]")
        }
      }
  }

  private val expectedDefaultRules = Seq(
    Rule(
      operation = Rule.Operation.Include,
      directory = "/home__/stasis",
      pattern = "**",
      comment = Some("include all files for the current user"),
      original = Rule.Original(
        line = "+ /home__/stasis           **                  # include all files for the current user",
        lineNumber = 0
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis/.m2",
      pattern = "repository/**",
      comment = Some("exclude maven artifacts"),
      original = Rule.Original(
        line = "- /home__/stasis/.m2       repository/**       # exclude maven artifacts",
        lineNumber = 1
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis/.ivy2",
      pattern = "{cache|local}/**",
      comment = Some("exclude ivy artifacts"),
      original = Rule.Original(
        line = "- /home__/stasis/.ivy2     {cache|local}/**    # exclude ivy artifacts",
        lineNumber = 2
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis",
      pattern = "**/*.{class|obj}",
      comment = None,
      original = Rule.Original(
        line = "- /home__/stasis           **/*.{class|obj}",
        lineNumber = 5
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis",
      pattern = "**/lost+found/*",
      comment = None,
      original = Rule.Original(
        line = "- /home__/stasis           **/lost+found/*",
        lineNumber = 6
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis",
      pattern = "**/*cache*/*",
      comment = None,
      original = Rule.Original(
        line = "- /home__/stasis           **/*cache*/*",
        lineNumber = 7
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis",
      pattern = "**/*log*/*",
      comment = None,
      original = Rule.Original(
        line = "- /home__/stasis           **/*log*/*",
        lineNumber = 8
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis",
      pattern = "**/*.{tmp|temp|part|bak|~}",
      comment = None,
      original = Rule.Original(
        line = "- /home__/stasis           **/*.{tmp|temp|part|bak|~}",
        lineNumber = 9
      )
    )
  )

  private val expectedDefinition = java.util.UUID.fromString("c0378fd9-9f77-45b7-99e6-9428743b6a91")

  private val expectedDefinitionRules = Seq(
    Rule(
      operation = Rule.Operation.Include,
      directory = "/home__/stasis",
      pattern = "**",
      comment = Some("include all files for the current user"),
      original = Rule.Original(
        line = "+ /home__/stasis           **                       # include all files for the current user",
        lineNumber = 4
      )
    ),
    Rule(
      operation = Rule.Operation.Exclude,
      directory = "/home__/stasis/test",
      pattern = "**",
      comment = Some("exclude test directory"),
      original = Rule.Original(
        line = "- /home__/stasis/test      **                       # exclude test directory",
        lineNumber = 5
      )
    )
  )
}
