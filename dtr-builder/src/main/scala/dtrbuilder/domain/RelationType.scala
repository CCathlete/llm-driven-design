package dtrbuilder.domain

/** Types of dependency/relation edges. */
sealed trait RelationType {
  def name: String
}

object RelationType {
  case object EXTENDS     extends RelationType { val name = "EXTENDS" }
  case object IMPLEMENTS  extends RelationType { val name = "IMPLEMENTS" }
  case object HOLDS       extends RelationType { val name = "HOLDS" }
  case object USES        extends RelationType { val name = "USES" }
  case object CTOR_DEP    extends RelationType { val name = "CTOR_DEP" }
  case object IMPORT_DEP  extends RelationType { val name = "IMPORT_DEP" }
  case object RETURN_DEP  extends RelationType { val name = "RETURN_DEP" }

  val values: Seq[RelationType] = Seq(
    EXTENDS, IMPLEMENTS, HOLDS, USES, CTOR_DEP, IMPORT_DEP, RETURN_DEP
  )

  def fromString(s: String): Option[RelationType] =
    values.find(_.name.equalsIgnoreCase(s))
}
