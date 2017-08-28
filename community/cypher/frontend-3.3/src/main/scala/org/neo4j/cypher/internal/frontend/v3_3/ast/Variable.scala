/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.frontend.v3_3.ast

import org.neo4j.cypher.internal.frontend.v3_3.ast.Expression.SemanticContext
import org.neo4j.cypher.internal.frontend.v3_3.symbols._
import org.neo4j.cypher.internal.frontend.v3_3.{InputPosition, SemanticCheck, SemanticCheckResult, SemanticError, SemanticState, SymbolUse}

case class Variable(name: String)(val position: InputPosition) extends Expression {

  def toSymbolUse = SymbolUse(name, position)

  // check the variable is defined and, if not, define it so that later errors are suppressed
  def semanticCheck(ctx: SemanticContext): (SemanticState) => SemanticCheckResult = s => this.ensureDefined()(s) match {
    case Right(ss) => SemanticCheckResult.success(ss)
    case Left(error) => SemanticCheckResult.error(declare(CTAny.covariant)(s).right.get, error)
  }

  // double-dispatch helpers

  def declareGraph: (SemanticState) => Either[SemanticError, SemanticState] =
    (_: SemanticState).declareGraphVariable(this)

  def implicitGraphDeclaration: (SemanticState) => Either[SemanticError, SemanticState] =
    (_: SemanticState).implicitGraphVariable(this)

  def declare(possibleTypes: TypeSpec): (SemanticState) => Either[SemanticError, SemanticState] =
    (_: SemanticState).declareVariable(this, possibleTypes)

  def declare(typeGen: SemanticState => TypeSpec, positions: Set[InputPosition] = Set.empty)
  : (SemanticState) => Either[SemanticError, SemanticState] =
    (s: SemanticState) => s.declareVariable(this, typeGen(s), positions)

  def implicitDeclaration(possibleType: CypherType): (SemanticState) => Either[SemanticError, SemanticState] =
    (_: SemanticState).implicitVariable(this, possibleType)

  def ensureGraphDefined(): SemanticCheck =
    ensureDefined() chain expectType(CTGraphRef.covariant)

  def ensureDefined(): (SemanticState) => Either[SemanticError, SemanticState] =
    (_: SemanticState).ensureVariableDefined(this)

  def copyId: Variable = copy()(position)

  def renameId(newName: String): Variable = copy(name = newName)(position)

  def bumpId: Variable = copy()(position.bumped())

  def asAlias: AliasedReturnItem = AliasedReturnItem(this.copyId, this.copyId)(this.position)

  override def asCanonicalStringVal: String = name
}

object Variable {
  implicit val byName: Ordering[Variable] =
    Ordering.by { (variable: Variable) =>
      (variable.name, variable.position)
    }(Ordering.Tuple2(implicitly[Ordering[String]], InputPosition.byOffset))
}
