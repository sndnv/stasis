package stasis.test.specs.unit.client.collection.rules

import stasis.client.collection.rules.Rule
import stasis.test.specs.unit.UnitSpec

import scala.util.{Failure, Success}

class RuleSpec extends UnitSpec {
  "A Rule" should "support trimming quoted strings" in {
    val expectedString = "test 42"

    val strings = Seq(
      s"""$expectedString""",
      s""" $expectedString""",
      s"""$expectedString """,
      s""" $expectedString """,
      s"""    $expectedString    """,
      s"""\"$expectedString\"""",
      s"""\" $expectedString\"""",
      s"""\"$expectedString\" """,
      s""" \"$expectedString\" """,
      s"""   \"$expectedString\"   """,
      s""" \"$expectedString\" """,
      s""" \" $expectedString\" """,
      s""" \"$expectedString \" """,
      s""" \" $expectedString \" """,
      s""" \"   $expectedString   \" """
    )

    strings.foreach { string =>
      val actualString = Rule.trimQuotedString(string)
      withClue(s"Trimmed string [$string] to [$actualString]") {
        actualString should be(expectedString)
      }
    }
  }

  it should "support extracting operations from a raw operation string" in {
    Rule.extractOperation(operation = "+", lineNumber = 0) should be(Success(Rule.Operation.Include))

    Rule.extractOperation(operation = "-", lineNumber = 0) should be(Success(Rule.Operation.Exclude))

    Rule.extractOperation(operation = "?", lineNumber = 0) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("Invalid rule operation provided on line [0]: [?]")
    }
  }

  it should "support extracting directories and patterns from a raw directory/pattern string" in {
    val validDirectoryPatternRules = Map(
      "/home/user   *               " -> ("/home/user", "*"),
      "/home/user   .*/             " -> ("/home/user", ".*/"),
      "/home/user   .sbt/*.sbt      " -> ("/home/user", ".sbt/*.sbt"),
      "/home/user   *.{conf,config} " -> ("/home/user", "*.{conf,config}"),
      "/home/user   .*rc            " -> ("/home/user", ".*rc"),
      "/etc         *               " -> ("/etc", "*"),
      " /etc        *               " -> ("/etc", "*"),
      "   /etc      *               " -> ("/etc", "*"),
      "/            *               " -> ("/", "*"),
      "\"/home/user/some directory\" *.conf" -> ("/home/user/some directory", "*.conf"),
      "\"/directory name with whitespace\" pattern with whitespace" -> ("/directory name with whitespace", "pattern with whitespace")
    )

    val invalidDirectoryPatternsRules = Seq(
      "/directory-without-pattern",
      "/",
      ".*",
      ""
    )

    validDirectoryPatternRules.foreach { case (rule, (directory, pattern)) =>
      val actualDirectoryPattern = Rule.extractDirectoryPattern(rule, lineNumber = 0)
      withClue(s"Extracted directory/pattern rule [$rule] to [$actualDirectoryPattern]") {
        actualDirectoryPattern should be(Success((directory, pattern)))
      }
    }

    invalidDirectoryPatternsRules.foreach { rule =>
      withClue(s"Attempting to extract directory/pattern from invalid rule [$rule]") {
        Rule.extractDirectoryPattern(rule, lineNumber = 0) match {
          case Success(result) =>
            fail(s"Unexpected result received: [$result]")

          case Failure(e) =>
            e.getMessage should be(s"Invalid rule directory and/or pattern provided on line [0]: [$rule]")
        }
      }
    }
  }

  it should "support extracting rules from a raw rule string" in {
    val validRules = Map(
      "+ /home/user   *         #  include all user files " -> Rule(
        operation = Rule.Operation.Include,
        directory = "/home/user",
        pattern = "*",
        comment = Some("include all user files"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "- /home/user   .ssh      #  exclude ssh directory  " -> Rule(
        operation = Rule.Operation.Exclude,
        directory = "/home/user",
        pattern = ".ssh",
        comment = Some("exclude ssh directory"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "+ /etc         *.conf    // include all conf files " -> Rule(
        operation = Rule.Operation.Include,
        directory = "/etc",
        pattern = "*.conf",
        comment = Some("include all conf files"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "- /etc/test    *cache*   // exclude all cache files" -> Rule(
        operation = Rule.Operation.Exclude,
        directory = "/etc/test",
        pattern = "*cache*",
        comment = Some("exclude all cache files"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "+   \"/var/log/some service\" *" -> Rule(
        operation = Rule.Operation.Include,
        directory = "/var/log/some service",
        pattern = "*",
        comment = None,
        original = Rule.Original(line = "", lineNumber = 0)
      )
    )

    val invalidRules = Seq(
      "/home/user * # include all user files",
      "? /home/user * # include all user files",
      "/home/user *",
      "/home/user",
      ""
    )

    validRules.foreach { case (ruleString, expectedRule) =>
      withClue(s"Parsing valid rule [$ruleString]") {
        Rule(line = ruleString, lineNumber = 0) should be(
          Success(expectedRule.copy(original = Rule.Original(line = ruleString, lineNumber = 0)))
        )
      }
    }

    invalidRules.foreach { ruleString =>
      withClue(s"Parsing invalid rule [$ruleString]") {
        Rule(line = ruleString, lineNumber = 0) match {
          case Success(result) =>
            fail(s"Unexpected result received: [$result]")

          case Failure(e) =>
            e.getMessage should be(s"Invalid rule definition found on line [0]: [$ruleString]")
        }
      }
    }
  }
}
