package stasis.test.specs.unit.client.collection.rules

import scala.util.Failure
import scala.util.Success

import stasis.client.collection.rules.Rule
import stasis.test.specs.unit.UnitSpec

class RuleSpec extends UnitSpec {
  "A Rule" should "support rendering itself as a string" in {
    val original = Rule.Original(line = "", lineNumber = 0)

    val rule1 = Rule(
      operation = Rule.Operation.Include,
      source = "/work",
      pattern = "?",
      options = Map.empty,
      comment = None,
      original = original
    )

    val rule2 = Rule(
      operation = Rule.Operation.Exclude,
      source = "/work",
      pattern = "[a-z]",
      options = Map.empty,
      comment = None,
      original = original
    )

    val rule3 = Rule(
      operation = Rule.Operation.Include,
      source = "/work",
      pattern = "{0|1}",
      options = Map.empty,
      comment = None,
      original = original
    )

    val rule4 = Rule(
      operation = Rule.Operation.Exclude,
      source = "/work/root",
      pattern = "**/q",
      options = Map.empty,
      comment = None,
      original = original
    )

    val rule5 = Rule(
      operation = Rule.Operation.Include,
      source = "photos:/test",
      pattern = "*",
      options = Map("recursive" -> "true", "album" -> "test a"),
      comment = None,
      original = original
    )

    rule1.asString should be("+ /work ?")
    rule2.asString should be("- /work [a-z]")
    rule3.asString should be("+ /work {0|1}")
    rule4.asString should be("- /work/root **/q")
    rule5.asString should be("""+ photos:/test * @album="test a" @recursive=true""")
  }

  it should "support extracting options from rule markers" in {
    Rule.extractOptions("photos:/test *", lineNumber = 0) should be(
      Success(("photos:/test *", Map.empty[String, String]))
    )

    Rule.extractOptions("""photos:/test * @recursive=true @album="test a"""", lineNumber = 0) should be(
      Success(("photos:/test *", Map("recursive" -> "true", "album" -> "test a")))
    )

    Rule.extractOptions("photos:/test * @recursive=true @recursive=false", lineNumber = 0) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("Duplicate option [recursive] provided on line [0]")
    }
  }

  it should "support trimming quoted strings" in {
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

  it should "support extracting sources and patterns from a raw source/pattern string" in {
    val validSourcePatternRules = Map(
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
      "\"/directory name with whitespace\" pattern with whitespace" -> (
        "/directory name with whitespace",
        "pattern with whitespace"
      )
    )

    val invalidSourcePatternRules = Seq(
      "/source-without-pattern",
      "/",
      ".*",
      ""
    )

    validSourcePatternRules.foreach { case (rule, (source, pattern)) =>
      val actualSourcePattern = Rule.extractSourcePattern(rule, lineNumber = 0)
      withClue(s"Extracted source/pattern rule [$rule] to [$actualSourcePattern]") {
        actualSourcePattern should be(Success((source, pattern)))
      }
    }

    invalidSourcePatternRules.foreach { rule =>
      withClue(s"Attempting to extract source/pattern from invalid rule [$rule]") {
        Rule.extractSourcePattern(rule, lineNumber = 0) match {
          case Success(result) =>
            fail(s"Unexpected result received: [$result]")

          case Failure(e) =>
            e.getMessage should be(s"Invalid rule source and/or pattern provided on line [0]: [$rule]")
        }
      }
    }
  }

  it should "support extracting rules from a raw rule string" in {
    val validRules = Map(
      "+ /home/user   *         #  include all user files " -> Rule(
        operation = Rule.Operation.Include,
        source = "/home/user",
        pattern = "*",
        options = Map.empty,
        comment = Some("include all user files"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "- /home/user   .ssh      #  exclude ssh directory  " -> Rule(
        operation = Rule.Operation.Exclude,
        source = "/home/user",
        pattern = ".ssh",
        options = Map.empty,
        comment = Some("exclude ssh directory"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "+ /etc         *.conf    // include all conf files " -> Rule(
        operation = Rule.Operation.Include,
        source = "/etc",
        pattern = "*.conf",
        options = Map.empty,
        comment = Some("include all conf files"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "- /etc/test    *cache*   // exclude all cache files" -> Rule(
        operation = Rule.Operation.Exclude,
        source = "/etc/test",
        pattern = "*cache*",
        options = Map.empty,
        comment = Some("exclude all cache files"),
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "+   \"/var/log/some service\" *" -> Rule(
        operation = Rule.Operation.Include,
        source = "/var/log/some service",
        pattern = "*",
        options = Map.empty,
        comment = None,
        original = Rule.Original(line = "", lineNumber = 0)
      ),
      "+ photos:/test * @recursive=true @album=\"test a\" # include test album" -> Rule(
        operation = Rule.Operation.Include,
        source = "photos:/test",
        pattern = "*",
        options = Map("recursive" -> "true", "album" -> "test a"),
        comment = Some("include test album"),
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
