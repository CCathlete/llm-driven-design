package hypothesisengine.application

import hypothesisengine.domain.*

object StageReporter:
  def build(
    hypothesis: Hypothesis,
    decision: Decision,
    priorHistory: Vector[StageDecision]
  ): LedgerStageReport =
    val stage: Int = priorHistory.length + 1
    val posterior: Double = decision match
      case Decision.Green(_, value, _) => value
      case Decision.Red(_, value, _, _, _) => value
      case Decision.Blocked(_) => 0.0d
    val gate: Double = decision match
      case Decision.Green(_, _, value) => value
      case Decision.Red(_, _, value, _, _) => value
      case Decision.Blocked(_) => hypothesis.gate
    val label: String = decision match
      case Decision.Green(_, _, _) => "GREEN"
      case Decision.Red(_, _, _, _, _) => "RED"
      case Decision.Blocked(_) => "BLOCKED"
    val breakpoints: Vector[BreakpointView] = decision match
      case Decision.Red(_, _, _, values, _) => values.map { breakpoint =>
        BreakpointView(
          coordinate = DtrCoordinate.value(breakpoint.coordinate),
          executionId = ExecutionId.value(breakpoint.executionId),
          detail = breakpoint.detail
        )
      }
      case _ => Vector.empty[BreakpointView]
    val fix: Option[String] = decision match
      case Decision.Red(_, _, _, _, suggestion) => Some(suggestion.description)
      case _ => None
    val current: StageDecision = StageDecision(stage, posterior, gate, label)
    LedgerStageReport(
      stage = stage,
      hypothesisId = HypothesisId.value(hypothesis.id),
      statement = hypothesis.statement,
      dtrCoordinates = hypothesis.coordinates.map(DtrCoordinate.value),
      justification = hypothesis.justification,
      probabilityForCorrectness = posterior,
      gate = gate,
      gap = gate - posterior,
      decision = label,
      breakpoints = breakpoints,
      fix = fix,
      history = priorHistory :+ current
    )