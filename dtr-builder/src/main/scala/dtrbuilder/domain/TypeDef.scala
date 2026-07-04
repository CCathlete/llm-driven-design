package dtrbuilder.domain

/** Flat type index entry. */
final case class TypeDef(
    fullyQualifiedName: String,
    kind: String,
    sourceFile: String
)
