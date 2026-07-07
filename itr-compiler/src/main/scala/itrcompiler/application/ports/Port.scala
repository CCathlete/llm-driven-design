package itrcompiler.application.ports

/** Base trait for all ports in the hexagonal architecture.
  *
  * Ports define the boundary between application core and outside world.
  * All dependencies point inward — infrastructure implements ports.
  */
trait Port
