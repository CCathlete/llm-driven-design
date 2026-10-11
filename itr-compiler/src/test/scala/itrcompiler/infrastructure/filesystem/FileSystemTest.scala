package itrcompiler.infrastructure.filesystem

import itrcompiler.domain.models.{CU, CUBatch}
import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Path, Paths}

class FileSystemTest extends AnyFunSpec {
  val fs = new FileSystem()
  val tmpDir: Path = Files.createTempDirectory("itr-compiler-test-")

  describe("FileSystem") {
    describe("DTRLoad") {
      it("should load a DTR file") {
        val dtrFile = tmpDir.resolve("test.dtr")
        Files.write(dtrFile, "TYPE.Foo\nFILE.Bar\n".getBytes)
        val content = fs.load(dtrFile)
        assert(content == "TYPE.Foo\nFILE.Bar\n")
      }
    }

    describe("CUWrite") {
      it("should write a CU frame with header and content") {
        val outDir = tmpDir.resolve("cuwrite-out")
        val cu = CU(id = "cu-test", dtrCoordinates = Seq("TYPE.Test"), content = "frame content")
        fs.write(cu, outDir, force = false)

        val written = new String(Files.readAllBytes(outDir.resolve("cu-test/cu-test.itr")))
        assert(written.contains("# CU-ID: cu-test"))
        assert(written.contains("# DTR-COORDINATES: TYPE.Test"))
        assert(written.contains("frame content"))
      }

      it("should skip existing file when force=false") {
        val outDir = tmpDir.resolve("cuwrite-skip")
        Files.createDirectories(outDir)
        val cuFile = outDir.resolve("cu-skip/cu-skip.itr")
        Files.createDirectories(cuFile.getParent)
        Files.write(cuFile, "original".getBytes)

        val cu = CU(id = "cu-skip", dtrCoordinates = Seq.empty, content = "new content")
        fs.write(cu, outDir, force = false)

        val content = new String(Files.readAllBytes(cuFile))
        assert(content == "original") // unchanged
      }

      it("should overwrite existing file when force=true") {
        val outDir = tmpDir.resolve("cuwrite-force")
        Files.createDirectories(outDir)
        val cuFile = outDir.resolve("cu-force/cu-force.itr")
        Files.createDirectories(cuFile.getParent)
        Files.write(cuFile, "original".getBytes)

        val cu = CU(id = "cu-force", dtrCoordinates = Seq.empty, content = "new content")
        fs.write(cu, outDir, force = true)

        val content = new String(Files.readAllBytes(cuFile))
        assert(content.contains("new content"))
      }

      it("should write only component files (no blob) for a component-carrying CU") {
        val outDir = tmpDir.resolve("cuwrite-components")
        val cu = CU(
          id = "cu-comp",
          dtrCoordinates = Seq("TYPE.X"),
          content = "legacy payload",
          components = Map(
            "coordinates" -> "TYPE.X",
            "requirements" -> "build the thing",
            "implementation-steps" -> "1. do it",
            "acceptance" -> "it works"
          )
        )
        fs.write(cu, outDir, force = false)

        val cuDir = outDir.resolve("cu-comp")
        assert(Files.exists(cuDir.resolve("COORDINATES.itr")))
        assert(Files.exists(cuDir.resolve("REQUIREMENTS.itr")))
        assert(Files.exists(cuDir.resolve("IMPLEMENTATION_STEPS.itr")))
        assert(Files.exists(cuDir.resolve("ACCEPTANCE.itr")))
        assert(!Files.exists(cuDir.resolve("cu-comp.itr")))
        assert(!Files.exists(outDir.resolve("cu-comp.itr")))
        val coords = new String(Files.readAllBytes(cuDir.resolve("COORDINATES.itr")))
        assert(coords.contains("# COORDINATES: cu-comp"))
        assert(coords.contains("TYPE.X"))
      }
    }

    describe("ContentRead") {
      it("should read JSON content") {
        val jsonFile = tmpDir.resolve("test.json")
        val json = """[{"cu-id":"cu-json","dtr-coordinates":[],"content":"from json"}]"""
        Files.write(jsonFile, json.getBytes)

        val batch = fs.readJson(jsonFile)
        assert(batch.cus.size == 1)
        assert(batch.cus.head.id == "cu-json")
      }

      it("should read YAML content") {
        val yamlFile = tmpDir.resolve("test.yaml")
        val yaml = """cu-yaml:
                     |  dtr-coordinates: [TYPE.Y]
                     |  content: "from yaml"
                     |""".stripMargin
        Files.write(yamlFile, yaml.getBytes)

        val batch = fs.readYaml(yamlFile)
        assert(batch.cus.size == 1)
        assert(batch.cus.head.id == "cu-yaml")
      }
    }
  }
}
