package itrcompiler.infrastructure.parsers

import itrcompiler.domain.models.{CU, CUBatch, CUSynopsis, CUType, RegularCU}

/** JSON format parser/serializer for CU batch data.
  *
  * JSON structure: [{cu-id, [cu-type,] dtr-coordinates:[str], content:str}]
  * CU object keys may appear in any order (JSON objects are unordered
  * per RFC 8259); the parser extracts each key independently of order.
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

    // Locate the CU array region: the "cus" array in object form,
    // otherwise the whole top-level array. Bracket matching respects
    // quoted strings so content values never confuse the scan.
    val arrayBounds: Option[(Int, Int)] =
      if (trimmed.startsWith("{")) {
        val cusKeyPattern = """"cus"\s*:\s*\[""".r
        cusKeyPattern.findFirstMatchIn(trimmed).map { m =>
          val arrayStart = m.end - 1
          (arrayStart, findArrayEnd(trimmed, arrayStart))
        }
      } else {
        val arrayStart = trimmed.indexOf('[')
        if (arrayStart == -1) None
        else Some((arrayStart, findArrayEnd(trimmed, arrayStart)))
      }

    val (arrayStart, arrayEnd) = arrayBounds.getOrElse(return CUBatch(Seq.empty, synopsis = synopsis))

    // Per-object scan: walk the array elements, locate each CU object
    // extent via findObjectExtent, then extract cu-id, optional cu-type,
    // dtr-coordinates, and content independently of key order.
    // Content values are extracted iteratively via parseJsonString
    // (no regex backtracking, no StackOverflowError on newlines).
    val idKeyPattern = """"cu-id"\s*:\s*"""".r
    val typeKeyPattern = """"cu-type"\s*:\s*"""".r
    val coordsPattern = """"dtr-coordinates"\s*:\s*\[([^\]]*)\]""".r
    val contentKeyPattern = """"content"\s*:\s*"""".r

    var cus = Vector.empty[CU]
    var i = arrayStart + 1
    while (i <= arrayEnd) {
      val ch = trimmed.charAt(i)
      if (ch.isWhitespace || ch == ',') {
        i += 1
      } else if (ch == '{') {
        val objectStart = i
        val objectEnd = findObjectExtent(trimmed, objectStart)
        val cuJson = trimmed.substring(objectStart, objectEnd + 1)

        idKeyPattern.findFirstMatchIn(cuJson) match {
          case None =>
            // Not a CU object — skip
          case Some(idm) =>
            val (id, _) = parseJsonString(cuJson, idm.end)
            val cuType = typeKeyPattern.findFirstMatchIn(cuJson) match {
              case None => RegularCU
              case Some(tm) =>
                val (typeStr, _) = parseJsonString(cuJson, tm.end)
                CUType.fromString(typeStr)
            }
            val coords: Seq[String] = coordsPattern.findFirstMatchIn(cuJson) match {
              case None => Seq.empty
              case Some(cm) =>
                val coordsStr = cm.group(1).trim
                if (coordsStr.isEmpty) Seq.empty
                else coordsStr.split(",").toSeq.map(_.trim.replaceAll("^\"|\"$", ""))
            }
            contentKeyPattern.findFirstMatchIn(cuJson) match {
              case None =>
                // Required content key absent — skip (same as before: no match, no CU)
              case Some(com) =>
                val (content, _) = parseJsonString(cuJson, com.end)
                val explicit = parseComponentsForCU(trimmed, objectStart, 0)
                val comps =
                  if (explicit.nonEmpty) explicit
                  else if (cuType == RegularCU) splitInlineSections(content)
                  else Map.empty[String, String]
                cus = cus :+ CU(id = id, dtrCoordinates = coords, content = content, cuType = cuType, components = comps)
            }
        }
        i = objectEnd + 1
      } else if (ch == '"') {
        // Unexpected top-level string — skip it iteratively
        val (_, closeIdx) = parseJsonString(trimmed, i + 1)
        i = closeIdx + 1
      } else if (ch == ']') {
        i = arrayEnd + 1
      } else {
        i += 1
      }
    }

    CUBatch(cus.toSeq, synopsis = synopsis)
  }

  /** Find the index of the closing bracket matching the '[' at `arrayStart`.
    * Respects quoted strings (with backslash escapes) so brackets inside
    * string values never affect depth counting.
    */
  private def findArrayEnd(json: String, arrayStart: Int): Int = {
    var depth = 0
    var i = arrayStart
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
        else if (ch == '[') depth += 1
        else if (ch == ']') {
          depth -= 1
          if (depth == 0) return i
        }
      }
      i += 1
    }
    json.length - 1
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

  /** Split inline labeled sections in a regular CU's content string.
    *
    * Scans content for the four ordered labeled headers (REQUIREMENTS:,
    * COORDINATES:, IMPLEMENTATION_STEPS:, ACCEPTANCE: as documented in the
    * compile-itr skill's CU content bar) and cuts the body text between
    * successive headers. Populates the components map with keys coordinates,
    * requirements, implementation-steps, acceptance. If all four sections
    * are absent, returns Map.empty so the legacy path is preserved.
    * An explicit "components" object, when present, wins over splitting
    * (handled by the caller).
    */
  private def splitInlineSections(content: String): Map[String, String] = {
    if (content == null || content.isEmpty) return Map.empty
    // Labels come from CU.sectionLabels — the same list validation uses
    // (domain layer), so parser and enforcement always agree on what a
    // section header is.
    val found = CU.sectionLabels.flatMap { case (label, key) =>
      val idx = content.indexOf(label)
      if (idx == -1) None else Some((idx, label, key))
    }
    if (found.isEmpty) return Map.empty
    val sorted = found.sortBy(_._1)
    val builder = Map.newBuilder[String, String]
    sorted.zipWithIndex.foreach { case ((pos, label, key), i) =>
      val start = pos + label.length
      val end = if (i + 1 < sorted.length) sorted(i + 1)._1 else content.length
      val body = content.substring(start, end).trim
      builder += key -> body
    }
    builder.result()
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
