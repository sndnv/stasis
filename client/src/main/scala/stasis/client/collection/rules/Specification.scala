package stasis.client.collection.rules

import java.nio.file._

import stasis.client.collection.rules.exceptions.RuleMatchingFailure

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

final case class Specification(
  files: Map[Path, Specification.Entry],
  unmatched: Seq[(Rule, Throwable)],
) {
  lazy val explanation: Map[Path, Seq[Specification.Entry.Explanation]] =
    files.mapValues(_.reason)

  lazy val (included: Seq[Path], excluded: Seq[Path]) =
    files.values.partition(_.operation == Rule.Operation.Include) match {
      case (included, excluded) => (included.map(_.file), excluded.map(_.file))
    }
}

object Specification {

  def empty: Specification = Specification(files = Map.empty, unmatched = Seq.empty)

  final case class Entry(
    file: Path,
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
            .filter(path => matcher.matches(path) && !Files.isDirectory(path))

          val matches = try {
            matchesStream.iterator.asScala.toList
          } finally {
            matchesStream.close()
          }

          if (matches.isEmpty) {
            spec.copy(unmatched = spec.unmatched :+ (rule, new RuleMatchingFailure("Rule matched no files")))
          } else {
            val updatedFiles = matches
              .map { file =>
                val entry = spec.files.get(file) match {
                  case Some(existing) =>
                    existing.copy(
                      operation = rule.operation,
                      reason = existing.reason :+ Entry.Explanation(rule.operation, rule.original)
                    )

                  case None =>
                    Entry(
                      file = file,
                      operation = rule.operation,
                      reason = Seq(Entry.Explanation(rule.operation, rule.original))
                    )
                }

                file -> entry
              }

            spec.copy(files = spec.files ++ updatedFiles)
          }
        } catch {
          case NonFatal(e) =>
            spec.copy(unmatched = spec.unmatched :+ (rule, e))
        }
    }
}
