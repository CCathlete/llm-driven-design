package dtrbuilder.domain.models

/** One code element per file — e.g. a class, def, import, etc. */
final case class CodexEntry(
    relPath: String,
    sigType: SigType,
    signatureText: String
)
