package stasis.client.collection.rules

import stasis.client.collection.rules.exceptions.RuleMatchingFailure
import stasis.client.collection.rules.internal.FilesWalker

import java.nio.file._
import scala.util.control.NonFatal

final case class Specification(
  entries: Map[Path, Specification.Entry],
  failures: Seq[Specification.FailedMatch]
) {
  lazy val explanation: Map[Path, Seq[Specification.Entry.Explanation]] =
    entries.map { case (path, entry) => (path, entry.reason) }

  lazy val (includedEntries: Seq[Path], includedParents: Seq[Path], excludedEntries: Seq[Path]) =
    entries.values.partition(_.operation == Rule.Operation.Include) match {
      case (included, excluded) =>
        val includedParents = included
          .flatMap { entry =>
            Specification.collectRelativeParents(from = entry.directory, to = entry.file)
          }
          .toSeq
          .distinct

        (included.map(_.file), includedParents, excluded.map(_.file))
    }

  lazy val included: Seq[Path] = (includedEntries ++ includedParents).distinct

  lazy val excluded: Seq[Path] = excludedEntries

  lazy val unmatched: Seq[(Rule, Throwable)] = failures.map(m => m.rule -> m.failure)
}

object Specification {

  def empty: Specification = Specification(entries = Map.empty, failures = Seq.empty)

  final case class Entry(
    file: Path,
    directory: Path,
    operation: Rule.Operation,
    reason: Seq[Entry.Explanation]
  )

  object Entry {
    final case class Explanation(
      operation: Rule.Operation,
      original: Rule.Original
    )
  }

  final case class RuleMatcher(
    rule: Rule,
    matcher: PathMatcher
  )

  final case class FailedMatch(
    rule: Rule,
    path: Path,
    failure: Throwable
  )

  def apply(rules: Seq[Rule]): Specification =
    apply(rules, filesystem = FileSystems.getDefault)

  def apply(rules: Seq[Rule], filesystem: FileSystem): Specification = {
    val (matchers, spec) = rules.foldLeft(Seq.empty[RuleMatcher] -> Specification.empty) { case ((matchers, spec), rule) =>
      val ruleDirectory = if (rule.directory.endsWith(filesystem.getSeparator)) {
        rule.directory
      } else {
        s"${rule.directory}${filesystem.getSeparator}"
      }

      val directory = filesystem.getPath(ruleDirectory)

      val matcher = filesystem.getPathMatcher(s"glob:$ruleDirectory${rule.pattern}")

      try {
        val result = FilesWalker.filter(start = directory, matcher = matcher)

        val updated = if (result.isEmpty) {
          spec.copy(
            failures = spec.failures :+ FailedMatch(
              rule = rule,
              path = directory,
              failure = new RuleMatchingFailure("Rule matched no files")
            )
          )
        } else {
          spec
            .withMatches(result.matches, rule, directory)
            .withFailures(result.failures, rule)
        }

        (matchers :+ RuleMatcher(rule, matcher)) -> updated
      } catch {
        case NonFatal(e) =>
          val updated = spec.copy(
            failures = spec.failures :+ FailedMatch(
              rule = rule,
              path = directory,
              failure = e
            )
          )

          (matchers :+ RuleMatcher(rule, matcher)) -> updated
      }
    }

    spec.dropExcludedFailures(matchers)
  }

  def collectRelativeParents(from: Path, to: Path): Seq[Path] = {
    @scala.annotation.tailrec
    def collect(current: Path, collected: Seq[Path]): Seq[Path] =
      if (current == from) {
        collected
      } else {
        val parent = current.getParent
        collect(
          current = parent,
          collected = collected :+ parent
        )
      }

    if (to.startsWith(from)) {
      collect(current = to, collected = Seq.empty)
    } else {
      Seq.empty
    }
  }

  implicit class ExtendedSpecification(spec: Specification) {
    def withMatches(matches: Seq[Path], rule: Rule, directory: Path): Specification = {
      val updatedFiles = matches
        .map { file =>
          val entry = spec.entries.get(file) match {
            case Some(existing) =>
              existing.copy(
                operation = rule.operation,
                reason = existing.reason :+ Entry.Explanation(rule.operation, rule.original)
              )

            case None =>
              Entry(
                file = file,
                directory = directory,
                operation = rule.operation,
                reason = Seq(Entry.Explanation(rule.operation, rule.original))
              )
          }

          file -> entry
        }

      spec.copy(entries = spec.entries ++ updatedFiles)
    }

    def withFailures(failures: Map[Path, Throwable], rule: Rule): Specification =
      failures.foldLeft(spec) { case (current, (path, failure)) =>
        current.copy(
          failures = current.failures :+ FailedMatch(
            rule = rule,
            path = path,
            failure = failure
          )
        )
      }

    def dropExcludedFailures(matchers: Seq[RuleMatcher]): Specification = {
      val exclusionMatchers = matchers.filter(_.rule.operation == Rule.Operation.Exclude)

      spec.copy(
        failures = spec.failures.filterNot { failure =>
          exclusionMatchers.exists(_.matcher.matches(failure.path))
        }
      )
    }
  }
}
