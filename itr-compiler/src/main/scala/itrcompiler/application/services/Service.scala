package itrcompiler.application.services

/** Base trait for all application services.
  *
  * Services contain operational logic and business rules.
  * They depend on ports (interfaces owned by the application layer)
  * and are consumed by use cases or directly by controllers.
  */
trait Service
