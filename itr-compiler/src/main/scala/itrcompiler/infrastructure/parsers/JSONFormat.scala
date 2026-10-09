package itrcompiler.infrastructure.parsers

import itrcompiler.domain.models.{CU, CUBatch, CUSynopsis, CUType, RegularCU}

/** JSON format parser/serializer for CU batch data.
  *
  * JSON structure: [{cu-id, [cu-type,] dtr-coordinates:[str], content:str}]
  *
  * This is a lightweight, dependency-free parser that handles the
  * expected input structures. For production use, consider a full
  * JSON library (e.g. circe, uPickle, or json4s).
  */
final class JSONFormat {

  /** Parse a JSON string value starting after the opening quote.
    * Iterates character by character (no regex recursion) to avoid
    * StackOverflowError on content with newline characters.
    *
    * @param json the full JSON string
    * @param start index of the first character AFTER the opening quote
    * @return (unescaped content, index of the closing quote)
    */
  private def parseJsonString(json: String, start: Int): (String, Int) = {
    val sb = new StringBuilder
    var i = start
    while (i < json.length) {
      val ch = json.charAt(i)
      if (ch == '"') {
        return (sb.toString, i)
      } else if (ch == '\\' && i + 1 < json.length) {
        json.charAt(i + 1) match {
          case 'n'  => sb.append('\n'); i += 2
          case 't'  => sb.append('\t'); i += 2
          case '"'  => sb.append('"');  i += 2
          case '\\' => sb.append('\\'); i += 2
          case c    => sb.append(c);    i += 2
        }
      } else {
        sb.append(ch)
        i += 1
      }
    }
    // Unterminated string — return what we have
    (sb.toString, i)
  }

  /** Locate the next occurrence of a quoted string in json starting at from.
    * Returns the index of the opening quote, or -1 if not found.
    */
  private def findQuote(json: String, from: Int): Int = {
    var i = from
    while (i < json.length) {
      if (json.charAt(i) == '"') return i
      i += 1
    }
    -1
  }

  /** Parse a JSON string value: find the opening quote at from, extract content. */
  private def extractJsonString(json: String, from: Int): (String, Int) = {
    val openIdx = findQuote(json, from)
    if (openIdx == -1) return ("", -1)
    parseJsonString(json, openIdx + 1)
  }

  /** Parse a JSON string into a CUBatch. */
  def parse(json: String): CUBatch = {
    val trimmed = json.trim
    if (trimmed.isEmpty || trimmed == "[]") return CUBatch(Seq.empty)

    // Top-level object form carries batch synopsis: {"synopsis": {...}, "cus": [...]}.
    // Legacy shape is a top-level array, parsed with synopsis=None.
    val synopsis: Option[CUSynopsis] =
      if (trimmed.startsWith("{")) parseSynopsis(trimmed)
      else None

    // Regex to locate each CU object boundary and extract cu-id + optional cu-type + dtr-coordinates.
    // Content value is intentionally matched greedily with .* to grab the opening quote position,
    // then extracted iteratively via parseJsonString.
    val cuLocator =
      """"cu-id"\s*:\s*"([^"]+)"\s*(?:,\s*"cu-type"\s*:\s*"([^"]+)"\s*)?,\s*"dtr-coordinates"\s*:\s*\[([^\]]*)\]\s*,\s*"content"\s*:\s*""".r

    val cus = cuLocator.findAllMatchIn(trimmed).map { m =>
      val id = m.group(1)
      val cuType = Option(m.group(2)).map(CUType.fromString).getOrElse(RegularCU)
      val coordsStr = m.group(3).trim
      val coords =
        if (coordsStr.isEmpty) Seq.empty
        else coordsStr.split(",").toSeq.map(_.trim.replaceAll("^\"|\"$", ""))

      // Extract the content string iteratively (no regex backtracking)
      val afterContentKey = m.end
      val (content, contentEnd) = extractJsonString(trimmed, afterContentKey)

      val comps = parseComponentsForCU(trimmed, m.start, contentEnd)

      CU(id = id, dtrCoordinates = coords, content = content, cuType = cuType, components = comps)
    }.toSeq

    CUBatch(cus, synopsis = synopsis)
  }

  /** Find the extent (start/end indices) of the JSON object opening at/before `pos`.
    * Scans forward counting braces while respecting quoted strings.
    */
  private def findObjectExtent(json: String, objectStart: Int): Int = {
    var depth = 0
    var i = objectStart
    var inString = false
    var escaped = false
    while (i < json.length) {
      val ch = json.charAt(i)
      if (inString) {
        if (escaped) escaped = false
        else if (ch == '\\') escaped = true
        else if (ch == '"') inString = false
      } else {
        if (ch == '"') inString = true
        else if (ch == '{') depth += 1
        else if (ch == '}') {
          depth -= 1
          if (depth == 0) return i
        }
      }
      i += 1
    }
    json.length - 1
  }

  /** Extract the optional "components" object for the CU whose locator match
    * starts at `matchStart` and whose content string ends at `contentEnd`
    * (index of the closing quote). Unknown keys are ignored; absent object
    * yields Map.empty so old batches are unaffected.
    */
  private def parseComponentsForCU(json: String, matchStart: Int, contentEnd: Int): Map[String, String] = {
    val objectStart = json.lastIndexOf('{', matchStart)
    if (objectStart == -1) return Map.empty
    val objectEnd = findObjectExtent(json, objectStart)
    val cuJson = json.substring(objectStart, objectEnd + 1)

    val compKeyPattern = """"components"\s*:\s*\{""".r
    compKeyPattern.findFirstMatchIn(cuJson) match {
      case None => Map.empty
      case Some(cm) =>
        val compBraceIdx = cuJson.indexOf('{', cm.start)
        if (compBraceIdx == -1) return Map.empty
        val compEndRel = findObjectExtent(cuJson, compBraceIdx)
        val compJson = cuJson.substring(compBraceIdx, compEndRel + 1)
        parseComponentsObject(compJson)
    }
  }

  /** Parse a {"coordinates": "...", "requirements": "...",
    * "implementation-steps": "...", "acceptance": "..."} object.
    * Unknown keys are ignored.
    */
  private def parseComponentsObject(compJson: String): Map[String, String] = {
    val allowed = Set("coordinates", "requirements", "implementation-steps", "acceptance")
    val builder = Map.newBuilder[String, String]
    allowed.foreach { key =>
      val keyPattern = ("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"").r
      keyPattern.findFirstMatchIn(compJson).foreach { km =>
        val (value, _) = parseJsonString(compJson, km.end)
        builder += key -> value
      }
    }
    builder.result()
  }

  /** Parse the top-level "synopsis" object, if present.
    * Expected: "synopsis": {"features": [...], "maxAttempts": N, "summary": "..."}.
    * Absent object -> None. maxAttempts defaults to 3 when absent.
    */
  private def parseSynopsis(json: String): Option[CUSynopsis] = {
    val synKeyPattern = """"synopsis"\s*:\s*\{""".r
    synKeyPattern.findFirstMatchIn(json) match {
      case None => None
      case Some(sm) =>
        val braceIdx = json.indexOf('{', sm.start)
        if (braceIdx == -1) return None
        val endIdx = findObjectExtent(json, braceIdx)
        val synJson = json.substring(braceIdx, endIdx + 1)

        val features: Seq[String] = {
          val featPattern = """"features"\s*:\s*\[([^\]]*)\]""".r
          featPattern.findFirstMatchIn(synJson) match {
            case None => Seq.empty
            case Some(fm) =>
              val inner = fm.group(1)
              // Iteratively extract quoted strings (handles escapes)
              var out = Vector.empty[String]
              var pos = 0
              while (pos < inner.length) {
                val q = inner.indexOf('"', pos)
                if (q == -1) pos = inner.length
                else {
                  val (v, closeIdx) = parseJsonString(inner, q + 1)
                  out = out :+ v
                  pos = closeIdx + 1
                }
              }
              out.toSeq
          }
        }

        val maxAttempts: Int = {
          val maxPattern = """"maxAttempts"\s*:\s*(\d+)""".r
          maxPattern.findFirstMatchIn(synJson).map(_.group(1).toInt).getOrElse(3)
        }

        val summary: String = {
          val sumPattern = """"summary"\s*:\s*"""".r
          sumPattern.findFirstMatchIn(synJson) match {
            case None => ""
            case Some(sum) =>
              val (v, _) = parseJsonString(synJson, sum.end)
              v
          }
        }

        Some(CUSynopsis(features = features, maxAttempts = maxAttempts, summary = summary))
    }
  }

  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\t", "\\t")

  private def serializeCU(cu: CU): String = {
    val coords = cu.dtrCoordinates.map(c => s""""$c"""").mkString("[", ", ", "]")
    val escaped = escapeJson(cu.content)
    val base = s"""{"cu-id":"${cu.id}","cu-type":"${cu.cuType}","dtr-coordinates":$coords,"content":"$escaped"}"""
    if (cu.components.isEmpty) base
    else {
      val compEntries = cu.components.map { case (k, v) => s""""$k":"${escapeJson(v)}"""" }.mkString("{", ", ", "}")
      // Insert components before the final closing brace
      base.dropRight(1) + s""","components":$compEntries}"""
    }
  }

  private def serializeSynopsis(syn: CUSynopsis): String = {
    val feats = syn.features.map(f => s""""${escapeJson(f)}"""").mkString("[", ", ", "]")
    s"""{"features":$feats,"maxAttempts":${syn.maxAttempts},"summary":"${escapeJson(syn.summary)}"}"""
  }

  /** Serialize a CUBatch to a JSON string. */
  def serialize(batch: CUBatch): String = {
    val entries = batch.cus.map(serializeCU)
    batch.synopsis match {
      case None =>
        "[" + entries.mkString(",\n  ") + "]"
      case Some(syn) =>
        s"""{"synopsis":${serializeSynopsis(syn)},"cus":[${entries.mkString(",\n  ")}]}"""
    }
  }
}
