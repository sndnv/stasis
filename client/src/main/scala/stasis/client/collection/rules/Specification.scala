package stasis.client.collection.rules

import java.nio.file._

import stasis.client.collection.rules.exceptions.RuleMatchingFailure

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

final case class Specification(
  entries: Map[Path, Specification.Entry],
  unmatched: Seq[(Rule, Throwable)]
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
}

object Specification {

  def empty: Specification = Specification(entries = Map.empty, unmatched = Seq.empty)

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

  def apply(rules: Seq[Rule]): Specification =
    apply(rules, filesystem = FileSystems.getDefault)

  def apply(rules: Seq[Rule], filesystem: FileSystem): Specification =
    rules.foldLeft(Specification.empty) {
      case (spec, rule) =>
        val ruleDirectory = if (rule.directory.endsWith(filesystem.getSeparator)) {
          rule.directory
        } else {
          s"${rule.directory}${filesystem.getSeparator}"
        }

        val directory = filesystem.getPath(ruleDirectory)

        val matcher = filesystem.getPathMatcher(s"glob:$ruleDirectory${rule.pattern}")

        try {
          val matchesStream = Files
            .walk(directory, Seq.empty[FileVisitOption]: _*)
            .filter(path => matcher.matches(path))

          val matches =
            try {
              matchesStream.iterator.asScala.toList
            } finally {
              matchesStream.close()
            }

          if (matches.isEmpty) {
            spec.copy(unmatched = spec.unmatched :+ (rule, new RuleMatchingFailure("Rule matched no files")))
          } else {
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
        } catch {
          case NonFatal(e) =>
            spec.copy(unmatched = spec.unmatched :+ (rule, e))
        }
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
}
