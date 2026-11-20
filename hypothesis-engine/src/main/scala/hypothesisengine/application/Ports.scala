package hypothesisengine.application

import hypothesisengine.domain.*
import java.time.Instant

trait ProcessRunner:
  def run(definition: ExperimentDefinition): RawResult

trait Clock:
  def now(): Instant

trait Ledger:
  def saveRaw(result: RawResult): Either[String, Unit]
  def saveCalculation(calculation: ProbabilityCalculation): Either[String, Unit]
  def saveDecision(hypothesisId: HypothesisId, decision: Decision): Either[String, Unit]
  def missingFor(hypothesis: Hypothesis, experiments: Vector[ExperimentDefinition]): Vector[String]
  def resultsFor(hypothesisId: HypothesisId): Vector[(RawResult, NormalizedResult)]
  def saveNormalized(result: NormalizedResult): Either[String, Unit]