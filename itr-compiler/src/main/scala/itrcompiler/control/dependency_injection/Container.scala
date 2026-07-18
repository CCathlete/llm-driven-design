package itrcompiler.control.dependency_injection

import itrcompiler.application.ports.{ContentRead, CUWrite, DTRLoad}
import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore, RequiredPartsValidation}
import itrcompiler.infrastructure.filesystem.FileSystem
import itrcompiler.infrastructure.parsers.{JSONFormat, YAMLFormat}

/** Dependency injection container — wires all layers together.
  *
  * Following ARCH.DI and ARCH.DIP:
  *   - Application layer owns the ports (traits)
  *   - Infrastructure provides concrete implementations
  *   - Control layer wires them together here
  *
  * All new wiring per cu-005:
  *   Container → DTRLoad, CUWrite, ContentRead, Compile,
  *               CoordinateRules, ContentDeserialize, CUStore,
  *               FileSystem, JSONFormat, YAMLFormat,
  *               RequiredPartsValidation
  */
final class Container {

  // ── Infrastructure ────────────────────────────────────────
  val jsonFormat: JSONFormat = new JSONFormat
  val yamlFormat: YAMLFormat = new YAMLFormat
  val fileSystem: FileSystem = new FileSystem(jsonFormat, yamlFormat)

  // ── Port bindings (infrastructure → port) ─────────────────
  val dtrLoad: DTRLoad           = fileSystem
  val cuWrite: CUWrite           = fileSystem
  val contentRead: ContentRead   = fileSystem

  // ── Services ──────────────────────────────────────────────
  val coordinateRules: CoordinateRules              = new CoordinateRules
  val contentDeserialize: ContentDeserialize        = new ContentDeserialize(contentRead)
  val cuStore: CUStore                              = new CUStore(cuWrite)
  val requiredPartsValidation: RequiredPartsValidation = new RequiredPartsValidation
  val compile: Compile = new Compile(
    dtrLoad, coordinateRules, cuStore, contentDeserialize, requiredPartsValidation
  )
}
