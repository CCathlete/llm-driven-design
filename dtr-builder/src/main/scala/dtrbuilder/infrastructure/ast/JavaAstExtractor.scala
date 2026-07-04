package dtrbuilder.infrastructure.ast

import dtrbuilder.application.SignatureExtractor
import dtrbuilder.domain.{CodexEntry, FileEntry, SigType}
import dtrbuilder.infrastructure.RegexSignatureExtractor
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body._
import scala.jdk.CollectionConverters._

/** Full AST extractor for Java using JavaParser (com.github.javaparser).
  *
  * Extracts: PACKAGE, IMPORT, CLASS, INTERFACE, ENUM, ANNOTATION, METHOD, CTOR, FIELD.
  * Falls back to regex extraction only on parse failure.
  */
class JavaAstExtractor(regexFallback: RegexSignatureExtractor) extends SignatureExtractor {

  override def extract(entry: FileEntry): Seq[CodexEntry] = {
    try {
      val cu: CompilationUnit = StaticJavaParser.parse(entry.absolutePath.toFile)
      val result = Seq.newBuilder[CodexEntry]

      // Package
      cu.getPackageDeclaration.ifPresent { pkgDecl =>
        result += CodexEntry(entry.relPath, SigType.PACKAGE, pkgDecl.getNameAsString)
      }

      // Imports
      cu.getImports.asScala.foreach { imp =>
        result += CodexEntry(entry.relPath, SigType.IMPORT, imp.getNameAsString)
      }

      // Top-level types
      cu.getTypes.asScala.foreach { typeDecl =>
        extractTypeDeclaration(entry.relPath, typeDecl, result)
      }

      val entries = result.result()
      if (entries.isEmpty) regexFallback.extract(entry) else entries
    } catch {
      case _: Exception => regexFallback.extract(entry)
    }
  }

  private def extractTypeDeclaration(
      relPath: String,
      typeDecl: TypeDeclaration[_],
      result: scala.collection.mutable.Builder[CodexEntry, Seq[CodexEntry]]
  ): Unit = {
    typeDecl match {
      case cls: ClassOrInterfaceDeclaration if cls.isInterface =>
        val name = cls.getNameAsString
        result += CodexEntry(relPath, SigType.INTERFACE, name)
        cls.getExtendedTypes.asScala.foreach { parent =>
          result += CodexEntry(relPath, SigType.EXTENDS, s"$name extends ${parent.getNameAsString}")
        }

      case cls: ClassOrInterfaceDeclaration =>
        val name = cls.getNameAsString
        result += CodexEntry(relPath, SigType.CLASS, name)
        cls.getExtendedTypes.asScala.foreach { parent =>
          result += CodexEntry(relPath, SigType.EXTENDS, s"$name extends ${parent.getNameAsString}")
        }
        cls.getImplementedTypes.asScala.foreach { parent =>
          result += CodexEntry(relPath, SigType.IMPLEMENTS, s"$name implements ${parent.getNameAsString}")
        }

      case enm: EnumDeclaration =>
        result += CodexEntry(relPath, SigType.ENUM, enm.getNameAsString)

      case ann: AnnotationDeclaration =>
        result += CodexEntry(relPath, SigType.ANNOTATION, ann.getNameAsString)

      case _ => ()
    }

    // Extract constructors
    typeDecl.getConstructors.asScala.foreach { ctor =>
      val params = ctor.getParameters.asScala.map(p => p.getNameAsString).mkString(", ")
      result += CodexEntry(relPath, SigType.CTOR, s"${typeDecl.getNameAsString}($params)")
    }

    // Extract methods
    typeDecl.getMethods.asScala.foreach { method =>
      result += CodexEntry(relPath, SigType.DEF, s"${method.getNameAsString}: ${method.getTypeAsString}")
    }

    // Extract fields
    typeDecl.getFields.asScala.foreach { field =>
      field.getVariables.asScala.foreach { varDecl =>
        val sigType = if (field.isFinal) SigType.VAL else SigType.VAR
        result += CodexEntry(relPath, sigType, s"${varDecl.getNameAsString}: ${field.getCommonType}")
      }
    }
  }
}
