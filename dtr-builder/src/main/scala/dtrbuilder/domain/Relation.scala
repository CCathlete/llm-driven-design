package dtrbuilder.domain

/** Explicit dependency edge between two fully-qualified names. */
final case class Relation(
    fromFqn: String,
    toFqn: String,
    relationType: RelationType,
    detail: String
)
