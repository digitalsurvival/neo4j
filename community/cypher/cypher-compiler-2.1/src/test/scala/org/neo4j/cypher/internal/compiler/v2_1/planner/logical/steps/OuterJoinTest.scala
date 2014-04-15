package org.neo4j.cypher.internal.compiler.v2_1.planner.logical.steps

import org.neo4j.graphdb.Direction
import org.neo4j.cypher.internal.commons.CypherFunSuite
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans._
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.CandidateList
import org.neo4j.cypher.internal.compiler.v2_1.planner._
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.PlanTable
import org.mockito.Mockito._
import org.mockito.Matchers._

class OuterJoinTest extends CypherFunSuite with LogicalPlanningTestSupport {

  val aNode = IdName("a")
  val bNode = IdName("b")
  val cNode = IdName("c")
  val dNode = IdName("d")
  val r1Name = IdName("r1")
  val r2Name = IdName("r2")
  val r3Name = IdName("r3")
  val r1Rel = PatternRelationship(r1Name, (aNode, bNode), Direction.OUTGOING, Seq.empty, SimplePatternLength)
  val r2Rel = PatternRelationship(r2Name, (bNode, cNode), Direction.OUTGOING, Seq.empty, SimplePatternLength)
  val r3Rel = PatternRelationship(r3Name, (cNode, dNode), Direction.OUTGOING, Seq.empty, SimplePatternLength)

  test("does not try to join anything if optional pattern is present") {
    // MATCH (a)-->(b)
    implicit val context = newMockedLogicalPlanContext(
      planContext = newMockedPlanContext,
      queryGraph = MainQueryGraph(Map.empty, Selections(), Set(aNode, bNode), Set(r1Rel), Seq.empty)
    )
    val left: LogicalPlan = newMockedLogicalPlanWithPatterns(Set(aNode, bNode))
    val planTable = PlanTable(Map(Set(aNode, bNode) -> left))

    outerJoin(planTable) should equal(CandidateList(Seq.empty))
  }

  test("outer join solves things. please?") {
    // MATCH a OPTIONAL MATCH a-->b
    val optionalQg: OptionalQueryGraph = OptionalQueryGraph(Selections(), Set(aNode, bNode), Set(r1Rel), Set(aNode))

    val factory = newMockedMetricsFactory
    when(factory.newCardinalityEstimator(any(), any())).thenReturn((plan: LogicalPlan) => plan match {
      case AllNodesScan(IdName("b")) => 1 // Make sure we start the inner plan using b
      case _                         => 1000.0
    })

    implicit val context = newMockedLogicalPlanContext(
      planContext = newMockedPlanContext,
      queryGraph = MainQueryGraph(Map.empty, Selections(), Set(aNode), Set.empty, Seq(optionalQg)),
      metrics = factory.newMetrics(newMockedStatistics)
    )
    val left: LogicalPlan = newMockedLogicalPlanWithPatterns(Set(aNode))
    val planTable = PlanTable(Map(Set(aNode) -> left))

    val expectedPlan = OuterHashJoin(aNode,
      left,
      Expand(
        AllNodesScan(bNode), bNode, Direction.INCOMING, Seq.empty, aNode, r1Name, SimplePatternLength
      )(r1Rel)
    )

    outerJoin(planTable) should equal(CandidateList(Seq(expectedPlan)))
  }
}
