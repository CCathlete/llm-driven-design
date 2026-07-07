package itrcompiler.control.entry_point

import itrcompiler.control.cli.CliParser
import itrcompiler.control.dependency_injection.Container

/** Application entry point for the itr-compiler.
  *
  * CLI usage:
  *   itr-compiler --compile --dtr <path> --out-folder <path> [options]
  *
  * Modes:
  *   Single CU:  --raw-content <text> --cu-id <id>
  *   JSON batch: --json-content <path>
  *   YAML batch: --yaml-content <path>
  *
  * Override:  --force  (overwrite existing CU files)
  */
object App {

  def main(args: Array[String]): Unit = {
    val container = new Container

    CliParser.parse(args) match {
      case Some(cmd) if cmd.compile =>
        val results = container.compile.execute(cmd)
        println(s"Compiled ${results.size} CU(s)")
        results.foreach { cu =>
          val coords = if (cu.dtrCoordinates.isEmpty) "" else s" [${cu.dtrCoordinates.mkString(", ")}]"
          println(s"  ✔ ${cu.id}$coords")
        }

      case Some(_) =>
        System.err.println("Error: --compile flag is required to start compilation.")

      case None =>
        println("""ITR Compiler v0.1.0
                  |Usage: itr-compiler --compile --dtr <path> --out-folder <path> [options]
                  |
                  |Options:
                  |  --compile                  Enable compile mode
                  |  --dtr <path>               Path to DTR file
                  |  --out-folder <path>        Output folder (default: out/)
                  |  --raw-content <string>     Single CU content (requires --cu-id)
                  |  --cu-id <id>               CU identifier
                  |  --json-content <path>      JSON batch input
                  |  --yaml-content <path>      YAML batch input
                  |  --force                    Overwrite existing CU files
                  |""".stripMargin)
    }
  }
}
