package stasis.test.specs.unit.client.ops.scheduling

import java.nio.file.Paths
import java.util.UUID

import scala.util.Success

import stasis.client.collection.rules.Rule
import stasis.client.ops.scheduling.OperationScheduleAssignment
import stasis.client.ops.scheduling.SchedulingConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class SchedulingConfigSpec extends AsyncUnitSpec with ResourceHelpers {
  "SchedulingConfig" should "only keep non-empty, non-comment lines" in {
    SchedulingConfig.keepLine(line = "") should be(false)
    SchedulingConfig.keepLine(line = "# comment") should be(false)
    SchedulingConfig.keepLine(line = "// comment") should be(false)
    SchedulingConfig.keepLine(line = "not a comment") should be(true)
  }

  it should "support loading entries from a file" in {
    val expectedLines = Seq(
      "line-01",
      "line-02",
      "line-03"
    )

    SchedulingConfig
      .parseEntries(file = "/ops/scheduling/test.file".asTestResource)(parse = (line, _) => Success(line))
      .map { actualLines =>
        actualLines should be(expectedLines)
      }
  }

  it should "support loading rules from a file" in {
    val expectedRules = Seq(
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

    SchedulingConfig
      .rules(file = "/ops/scheduling/test.rules".asTestResource)
      .map { actualRules =>
        actualRules should be(expectedRules)
      }
  }

  it should "support loading schedule assignments from a file" in {
    val schedule1 = UUID.fromString("c7a1a7c4-94e1-44a8-8365-9fc44c8f51ea")
    val schedule2 = UUID.fromString("b859d9fb-1962-4a29-b18d-2c7c835cf18b")
    val schedule3 = UUID.fromString("4bb8ab15-e8ba-40dd-b814-b8b112a79147")
    val schedule4 = UUID.fromString("ce849f2c-e170-427c-8e9d-0e3d8cdcf700")
    val schedule5 = UUID.fromString("2f059ab6-3db2-4a34-8082-780d673f6519")

    val definition1 = UUID.fromString("ea371971-adc5-44ba-9d93-6f65507d6967")
    val definition2 = UUID.fromString("a1a55d45-b3b2-48df-b628-2fb2e7e022a4")

    val expectedSchedules = Seq(
      OperationScheduleAssignment.Backup(
        schedule = schedule1,
        definition = definition1,
        entities = Seq.empty
      ),
      OperationScheduleAssignment.Backup(
        schedule = schedule2,
        definition = definition2,
        entities = Seq(Paths.get("/work/file-01"), Paths.get("/work/file-02"))
      ),
      OperationScheduleAssignment.Expiration(schedule = schedule3),
      OperationScheduleAssignment.Validation(schedule = schedule4),
      OperationScheduleAssignment.KeyRotation(schedule = schedule5)
    )

    SchedulingConfig
      .schedules(file = "/ops/scheduling/test.schedules".asTestResource)
      .map { actualSchedules =>
        actualSchedules should be(expectedSchedules)
      }
  }
}
