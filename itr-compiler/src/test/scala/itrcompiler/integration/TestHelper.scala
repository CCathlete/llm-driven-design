package itrcompiler.integration

import itrcompiler.application.services.{Compile, ContentDeserialize, CoordinateRules, CUStore, RequiredPartsValidation}
import itrcompiler.application.ports.DTRLoad

/** Shared test helper for integration tests */
object TestHelper {
  def createCompile(
    dtrLoad: DTRLoad,
    fs: itrcompiler.infrastructure.filesystem.FileSystem
  ): Compile = {
    val coordRules = new CoordinateRules
    val cuStore = new CUStore(fs)
    val contentDeser = new ContentDeserialize(fs)
    val requiredPartsValidation = new RequiredPartsValidation
    new Compile(dtrLoad, coordRules, cuStore, contentDeser, requiredPartsValidation)
  }
}
