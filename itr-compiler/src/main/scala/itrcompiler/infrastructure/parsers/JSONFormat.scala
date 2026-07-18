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
      val (content, _) = extractJsonString(trimmed, afterContentKey)

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
