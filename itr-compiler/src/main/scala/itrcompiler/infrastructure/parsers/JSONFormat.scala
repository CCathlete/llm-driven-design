package itrcompiler.infrastructure.parsers

import itrcompiler.domain.models.{CU, CUBatch, CUType, RegularCU}

/** JSON format parser/serializer for CU batch data.
  *
  * JSON structure: [{cu-id, [cu-type,] dtr-coordinates:[str], content:str}]
  *
  * This is a lightweight, dependency-free parser that handles the
  * expected input structures. For production use, consider a full
  * JSON library (e.g. circe, uPickle, or json4s).
  */
final class JSONFormat {

  /** Parse a JSON string into a CUBatch. */
  def parse(json: String): CUBatch = {
    val trimmed = json.trim
    if (trimmed.isEmpty || trimmed == "[]") return CUBatch(Seq.empty)

    // Single pattern with optional cu-type field
    val cuPattern =
      """\{\s*"cu-id"\s*:\s*"([^"]+)"\s*(?:,\s*"cu-type"\s*:\s*"([^"]+)"\s*)?,\s*"dtr-coordinates"\s*:\s*\[([^\]]*)\]\s*,\s*"content"\s*:\s*"([^"]*(?:\\.[^"]*)*)"\s*\}""".r

    val cus = cuPattern.findAllMatchIn(trimmed).map { m =>
      val id = m.group(1)
      val cuType = Option(m.group(2)).map(CUType.fromString).getOrElse(RegularCU)
      val coordsStr = m.group(3).trim
      val coords =
        if (coordsStr.isEmpty) Seq.empty
        else coordsStr.split(",").toSeq.map(_.trim.replaceAll("^\"|\"$", ""))
      val rawContent = m.group(4)
      val content = rawContent
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
      CU(id = id, dtrCoordinates = coords, content = content, cuType = cuType)
    }.toSeq

    CUBatch(cus)
  }

  /** Serialize a CUBatch to a JSON string. */
  def serialize(batch: CUBatch): String = {
    val entries = batch.cus.map { cu =>
      val coords = cu.dtrCoordinates.map(c => s""""$c"""").mkString("[", ", ", "]")
      val escaped = cu.content
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
      s"""{"cu-id":"${cu.id}","cu-type":"${cu.cuType}","dtr-coordinates":$coords,"content":"$escaped"}"""
    }
    "[" + entries.mkString(",\n  ") + "]"
  }
}
