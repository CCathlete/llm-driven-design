package dtrbuilder.domain

/** Types of code signatures that can be extracted. */
sealed trait SigType {
  def name: String
  def entryName: String = name
}

object SigType {
  case object PACKAGE   extends SigType { val name = "PACKAGE" }
  case object IMPORT    extends SigType { val name = "IMPORT" }
  case object CLASS     extends SigType { val name = "CLASS" }
  case object TRAIT     extends SigType { val name = "TRAIT" }
  case object OBJECT    extends SigType { val name = "OBJECT" }
  case object CASE_CLASS    extends SigType { val name = "CASE_CLASS" }
  case object CASE_OBJECT   extends SigType { val name = "CASE_OBJECT" }
  case object INTERFACE     extends SigType { val name = "INTERFACE" }
  case object ENUM      extends SigType { val name = "ENUM" }
  case object ANNOTATION    extends SigType { val name = "ANNOTATION" }
  case object CTOR      extends SigType { val name = "CTOR" }
  case object DEF       extends SigType { val name = "DEF" }
  case object VAL       extends SigType { val name = "VAL" }
  case object VAR       extends SigType { val name = "VAR" }
  case object TYPE      extends SigType { val name = "TYPE" }
  case object FUNCTION  extends SigType { val name = "FUNCTION" }
  case object EXPORT    extends SigType { val name = "EXPORT" }
  case object DECORATOR     extends SigType { val name = "DECORATOR" }
  case object HEADING   extends SigType { val name = "HEADING" }
  case object KEY       extends SigType { val name = "KEY" }
  case object PROPERTY  extends SigType { val name = "PROPERTY" }
  case object CONST     extends SigType { val name = "CONST" }
  case object FROM_IMPORT   extends SigType { val name = "FROM_IMPORT" }
  case object VARIABLE  extends SigType { val name = "VARIABLE" }
  case object ARROW_FN  extends SigType { val name = "ARROW_FN" }
  case object INCLUDE   extends SigType { val name = "INCLUDE" }
  case object STRUCT    extends SigType { val name = "STRUCT" }
  case object TYPEDEF   extends SigType { val name = "TYPEDEF" }
  case object MACRO     extends SigType { val name = "MACRO" }
  case object TEMPLATE  extends SigType { val name = "TEMPLATE" }
  case object NAMESPACE extends SigType { val name = "NAMESPACE" }
  case object FUNC      extends SigType { val name = "FUNC" }
  case object USE       extends SigType { val name = "USE" }
  case object CRATE     extends SigType { val name = "CRATE" }
  case object MOD       extends SigType { val name = "MOD" }
  case object FN        extends SigType { val name = "FN" }
  case object IMPL      extends SigType { val name = "IMPL" }
  case object REQUIRE   extends SigType { val name = "REQUIRE" }
  case object MODULE    extends SigType { val name = "MODULE" }
  case object ATTR      extends SigType { val name = "ATTR" }
  case object COMMAND   extends SigType { val name = "COMMAND" }
  case object CODE_BLOCK    extends SigType { val name = "CODE_BLOCK" }
  case object TOP_KEY   extends SigType { val name = "TOP_KEY" }
  case object NESTED_KEY    extends SigType { val name = "NESTED_KEY" }
  case object UNKNOWN   extends SigType { val name = "UNKNOWN" }

  val values: Seq[SigType] = Seq(
    PACKAGE, IMPORT, CLASS, TRAIT, OBJECT, CASE_CLASS, CASE_OBJECT,
    INTERFACE, ENUM, ANNOTATION, CTOR, DEF, VAL, VAR, TYPE,
    FUNCTION, EXPORT, DECORATOR, HEADING, KEY, PROPERTY, CONST,
    FROM_IMPORT, VARIABLE, ARROW_FN, INCLUDE, STRUCT, TYPEDEF,
    MACRO, TEMPLATE, NAMESPACE, FUNC, USE, CRATE, MOD, FN,
    IMPL, REQUIRE, MODULE, ATTR, COMMAND, CODE_BLOCK, TOP_KEY,
    NESTED_KEY, UNKNOWN
  )

  def fromString(s: String): Option[SigType] =
    values.find(_.name.equalsIgnoreCase(s))
}
