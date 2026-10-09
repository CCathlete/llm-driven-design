package itrcompiler.infrastructure.parsers

import itrcompiler.domain.models.{CU, CUBatch, CUSynopsis, CUType, RegularCU}

/** YAML format parser/serializer for CU batch data.
  *
  * YAML structure:
  *   cu-id:
  *     cu-type: regular
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

    // Batch-level synopsis travels as a top-level `synopsis:` sibling block,
    // never nested under a CU id.
    val synopsis: Option[CUSynopsis] = entryBlocks
      .find(b => b.takeWhile(_ != ':').trim == "synopsis")
      .map(parseSynopsisBlock)

    val cus = entryBlocks.flatMap { block =>
      val lines = block.split("\n")
      val firstLine = lines.headOption.getOrElse("")
      if (!firstLine.contains(":")) None
      else {
        val id = firstLine.takeWhile(_ != ':').trim
        if (id == "synopsis") None
        else {
          val props = lines.tail.mkString("\n")

          val cuTypePattern = """cu-type:\s*"?([^"\s]*)"?""".r
          val coordsPattern = """dtr-coordinates:\s*\[([^\]]*)\]""".r
          // Try quoted content first, then fallback to unquoted
          val contentQuoted = """content:\s*"([^"]*(?:\\.[^"]*)*)"""".r
          val contentUnquoted = """content:\s*(.+)""".r

          val cuType = cuTypePattern.findFirstMatchIn(props).map(m =>
            CUType.fromString(m.group(1).trim)
          ).getOrElse(RegularCU)

          val coords = coordsPattern.findFirstMatchIn(props).map { m =>
            val str = m.group(1).trim
            if (str.isEmpty) Seq.empty
            else str.split(",").toSeq.map(_.trim.replaceAll("^\"|\"$", ""))
          }.getOrElse(Seq.empty)

          val content = contentQuoted.findFirstMatchIn(props)
            .map(_.group(1).trim)
            .orElse(contentUnquoted.findFirstMatchIn(props).map(_.group(1).trim))
            .getOrElse("")

          val components = parseComponentsBlock(lines.tail.toSeq)

          Some(CU(id = id, dtrCoordinates = coords, content = content, cuType = cuType, components = components))
        }
      }
    }

    CUBatch(cus.toSeq, synopsis = synopsis)
  }

  /** Extract the optional indented `components:` mapping from a CU's property lines.
    * Absent block -> Map.empty. Only the four known keys are collected;
    * unknown keys are ignored.
    */
  private def parseComponentsBlock(propLines: Seq[String]): Map[String, String] = {
    val compIdx = propLines.indexWhere(l => l.trim.startsWith("components:"))
    if (compIdx == -1) return Map.empty
    // Components keys are indented deeper than the `components:` line itself.
    // Collect following lines that are more indented (at least 4 spaces).
    val compLines = propLines.drop(compIdx + 1).takeWhile(l => l.trim.isEmpty || l.startsWith("    ") || l.startsWith("\t"))
    val compText = compLines.mkString("\n")
    val allowed = Seq("coordinates", "requirements", "implementation-steps", "acceptance")
    allowed.flatMap { key =>
      val quoted = (key + """:\s*"([^"]*(?:\\.[^"]*)*)"""").r
      val unquoted = (key + """:\s*(.+)""").r
      quoted.findFirstMatchIn(compText)
        .map(m => key -> unescapeYaml(m.group(1).trim))
        .orElse(unquoted.findFirstMatchIn(compText).map(m => key -> m.group(1).trim))
    }.toMap
  }

  private def unescapeYaml(s: String): String =
    s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

  /** Parse a top-level `synopsis:` block into a CUSynopsis.
    * `maxAttempts` defaults to 3 when absent.
    */
  private def parseSynopsisBlock(block: String): CUSynopsis = {
    val lines = block.split("\n")
    val props = lines.tail.mkString("\n")
    val featuresPattern = """features:\s*\[([^\]]*)\]""".r
    val maxPattern = """maxAttempts:\s*(\d+)""".r
    val summaryQuoted = """summary:\s*"([^"]*(?:\\.[^"]*)*)"""".r
    val summaryUnquoted = """summary:\s*(.+)""".r

    val features: Seq[String] = featuresPattern.findFirstMatchIn(props) match {
      case None => Seq.empty
      case Some(m) =>
        val str = m.group(1).trim
        if (str.isEmpty) Seq.empty
        else str.split(",").toSeq.map(_.trim.replaceAll("^\"|\"$", ""))
    }
    val maxAttempts: Int = maxPattern.findFirstMatchIn(props).map(_.group(1).toInt).getOrElse(3)
    val summary: String = summaryQuoted.findFirstMatchIn(props)
      .map(m => unescapeYaml(m.group(1).trim))
      .orElse(summaryUnquoted.findFirstMatchIn(props).map(_.group(1).trim))
      .getOrElse("")

    CUSynopsis(features = features, maxAttempts = maxAttempts, summary = summary)
  }

  private def escapeYaml(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

  private def serializeCU(cu: CU): String = {
    val coords = cu.dtrCoordinates.map(c => s""""$c"""").mkString("[", ", ", "]")
    val base = s"""${cu.id}:\n  cu-type: ${cu.cuType}\n  dtr-coordinates: $coords\n  content: \"${escapeYaml(cu.content)}\""""
    if (cu.components.isEmpty) base
    else {
      val order = Seq("coordinates", "requirements", "implementation-steps", "acceptance")
      val keys = order.filter(cu.components.contains) ++ cu.components.keySet.filterNot(order.contains).toSeq.sorted
      val compLines = keys.map(k => s"""    $k: \"${escapeYaml(cu.components(k))}\"""").mkString("\n")
      base + s"\n  components:\n$compLines"
    }
  }

  private def serializeSynopsisBlock(syn: CUSynopsis): String = {
    val feats = syn.features.map(f => s""""$f"""").mkString("[", ", ", "]")
    s"""synopsis:\n  features: $feats\n  maxAttempts: ${syn.maxAttempts}\n  summary: \"${escapeYaml(syn.summary)}\""""
  }

  /** Serialize a CUBatch to a YAML string. */
  def serialize(batch: CUBatch): String = {
    val cuParts = batch.cus.map(serializeCU)
    batch.synopsis match {
      case None => cuParts.mkString("\n")
      case Some(syn) => (serializeSynopsisBlock(syn) +: cuParts).mkString("\n")
    }
  }
}
