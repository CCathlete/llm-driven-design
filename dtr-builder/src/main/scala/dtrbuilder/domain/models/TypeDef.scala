package dtrbuilder.domain.models

/** Flat type index entry. */
final case class TypeDef(
    fullyQualifiedName: String,
    kind: String,
    sourceFile: String
)
