package stasis.client.collection.rules

import java.nio.file._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import stasis.client.collection.rules.exceptions.RuleMatchingFailure
import stasis.client.collection.rules.internal.FilesWalker
import stasis.client.collection.rules.internal.IndexedRule
import stasis.client.tracking.BackupTracker
import stasis.shared.ops.Operation

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

  lazy val unmatched: Seq[(Rule, Throwable)] = failures.sortBy(_.rule.index).map(m => m.rule.underlying -> m.failure)
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
    rule: IndexedRule,
    matcher: PathMatcher
  )

  final case class FailedMatch(
    rule: IndexedRule,
    path: Path,
    failure: Throwable
  )

  def untracked(
    rules: Seq[Rule]
  )(implicit ec: ExecutionContext): Future[Specification] =
    apply(rules, onMatchIncluded = { _ => () }, filesystem = FileSystems.getDefault)

  def tracked(
    rules: Seq[Rule],
    tracker: BackupTracker
  )(implicit ec: ExecutionContext, operation: Operation.Id): Future[Specification] =
    apply(rules, onMatchIncluded = { path => tracker.entityDiscovered(path) }, filesystem = FileSystems.getDefault)

  def apply(
    rules: Seq[Rule],
    onMatchIncluded: Path => Unit
  )(implicit ec: ExecutionContext): Future[Specification] =
    apply(rules, onMatchIncluded = onMatchIncluded, filesystem = FileSystems.getDefault)

  def apply(
    rules: Seq[Rule],
    onMatchIncluded: Path => Unit,
    filesystem: FileSystem
  )(implicit ec: ExecutionContext): Future[Specification] = Future {
    val (matchers, spec) = rules.zipWithIndex
      .map(e => IndexedRule(index = e._2, underlying = e._1))
      .groupBy(_.underlying.directory)
      .foldLeft(Seq.empty[RuleMatcher] -> Specification.empty) { case ((collected, spec), (groupedDirectory, rules)) =>
        val (directory, matchers) = rules.asMatchers(groupedDirectory, filesystem)

        try {
          val result = FilesWalker.filter(start = directory, matchers = matchers, onMatchIncluded = onMatchIncluded)

          val updated = if (result.isEmpty) {
            spec.copy(
              failures = spec.failures :++ rules.map { rule =>
                FailedMatch(
                  rule = rule,
                  path = directory,
                  failure = new RuleMatchingFailure("Rule matched no files")
                )
              }
            )
          } else {
            spec
              .withMatches(result.matches, directory)
              .withFailures(rules.first, result.failures)
          }

          (collected :++ matchers.map(m => RuleMatcher(m._1, m._2))) -> updated
        } catch {
          case NonFatal(e) =>
            val updated = spec.copy(
              failures = spec.failures :+ FailedMatch(
                rule = rules.first,
                path = directory,
                failure = e
              )
            )

            (collected :++ matchers.map(m => RuleMatcher(m._1, m._2))) -> updated
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

  implicit class ExtendedRules(rules: Seq[IndexedRule]) {
    def asMatchers(groupedDirectory: String, filesystem: FileSystem): (Path, Seq[(IndexedRule, PathMatcher)]) = {
      val ruleDirectory = if (groupedDirectory.endsWith(filesystem.getSeparator)) {
        groupedDirectory
      } else {
        s"$groupedDirectory${filesystem.getSeparator}"
      }

      val directory = filesystem.getPath(ruleDirectory)

      val matchers = rules.map { rule =>
        rule -> filesystem.getPathMatcher(s"glob:$ruleDirectory${rule.underlying.pattern}")
      }

      directory -> matchers
    }

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def first: IndexedRule =
      rules.headOption match {
        case Some(rule) => rule
        case None       => throw new IllegalStateException("At least one rule must be provided")
      }
  }

  implicit class ExtendedSpecification(spec: Specification) {
    def withMatches(matches: Map[IndexedRule, Seq[Path]], directory: Path): Specification = {
      val updatedFiles = matches.toSeq
        .sortBy(_._1.index)
        .foldLeft(Seq.empty[(IndexedRule, Path)]) { case (collected, (rule, files)) =>
          collected :++ files.map(file => rule -> file)
        }
        .foldLeft(spec.entries) { case (collected, (rule, file)) =>
          val entry = collected.get(file) match {
            case Some(existing) =>
              existing.copy(
                operation = rule.underlying.operation,
                reason = existing.reason :+ Entry.Explanation(rule.underlying.operation, rule.underlying.original)
              )

            case None =>
              Entry(
                file = file,
                directory = directory,
                operation = rule.underlying.operation,
                reason = Seq(Entry.Explanation(rule.underlying.operation, rule.underlying.original))
              )
          }

          collected + (file -> entry)
        }

      spec.copy(entries = updatedFiles)
    }

    def withFailures(rule: IndexedRule, failures: Map[Path, Throwable]): Specification =
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
      val exclusionMatchers = matchers.filter(_.rule.underlying.operation == Rule.Operation.Exclude)

      spec.copy(
        failures = spec.failures.filterNot { failure =>
          exclusionMatchers.exists(_.matcher.matches(failure.path))
        }
      )
    }
  }
}
