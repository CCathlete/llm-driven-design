package itrcompiler.infrastructure.parsers

import itrcompiler.domain.models.{CU, CUBatch}

/** YAML format parser/serializer for CU batch data.
  *
  * YAML structure:
  *   cu-id:
  *     dtr-coordinates: [str, str]
  *     content: "free-form text"
  *
  * This is a lightweight, dependency-free parser. For production use,
  * consider a full YAML library (e.g. SnakeYAML or circe-yaml).
  */
final class YAMLFormat {

  /** Parse a YAML string into a CUBatch. */
  def parse(yaml: String): CUBatch = {
    val trimmed = yaml.trim
    if (trimmed.isEmpty) return CUBatch(Seq.empty)

    // Split top-level entries (lines that start at column 0)
    val entryBlocks = trimmed.split("\n(?=\\S)").filter(_.trim.nonEmpty)

    val cus = entryBlocks.flatMap { block =>
      val lines = block.split("\n")
      val firstLine = lines.headOption.getOrElse("")
      if (!firstLine.contains(":")) None
      else {
        val id = firstLine.takeWhile(_ != ':').trim
        val props = lines.tail.mkString("\n")

        val coordsPattern = """dtr-coordinates:\s*\[([^\]]*)\]""".r
        val contentPattern = """content:\s*"?([^"]*)"?""".r

        val coords = coordsPattern.findFirstMatchIn(props).map { m =>
          val str = m.group(1).trim
          if (str.isEmpty) Seq.empty
          else str.split(",").toSeq.map(_.trim.replaceAll("^\"|\"$", ""))
        }.getOrElse(Seq.empty)

        val content = contentPattern.findFirstMatchIn(props).map(_.group(1).trim).getOrElse("")

        Some(CU(id = id, dtrCoordinates = coords, content = content))
      }
    }

    CUBatch(cus.toSeq)
  }

  /** Serialize a CUBatch to a YAML string. */
  def serialize(batch: CUBatch): String = {
    batch.cus.map { cu =>
      val coords = cu.dtrCoordinates.map(c => s""""$c"""").mkString("[", ", ", "]")
      s"""${cu.id}:
        |  dtr-coordinates: $coords
        |  content: "${cu.content}"""".stripMargin
    }.mkString("\n")
  }
}
