package dtrbuilder.domain.models

import scala.util.matching.Regex

/** Profile for a programming/markup language with regex-based extraction patterns. */
final case class Language(
    name: String,
    extensions: Seq[String],
    shebangs: Seq[String],
    regexPatterns: Seq[RegexPattern]
)

/** A regex pattern that extracts a specific signature type from source code. */
final case class RegexPattern(
    sigType: SigType,
    pattern: Regex,
    description: String = ""
)
