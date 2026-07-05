package itrcompiler.infrastructure.database

import itrcompiler.application.ports.Port
import itrcompiler.domain.models.Model

/** Repository — infrastructure adapter that implements Port.
  *
  * In a full implementation this would provide persistence for domain models.
  * Currently serves as a structural placeholder fulfilling the DTR contract.
  */
class Repository extends Port {
  // Repository implementation (future: database-backed storage)
}
