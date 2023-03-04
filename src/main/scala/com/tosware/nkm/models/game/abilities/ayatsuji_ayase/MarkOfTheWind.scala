package com.tosware.nkm.models.game.abilities.ayatsuji_ayase

import com.tosware.nkm.NkmConf
import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.abilities.ayatsuji_ayase.MarkOfTheWind.trapLocationsKey
import com.tosware.nkm.models.game.ability.Ability.{AbilityId, UseCheck}
import com.tosware.nkm.models.game.ability._
import com.tosware.nkm.models.game.character.NkmCharacter.CharacterId
import com.tosware.nkm.models.game.hex.HexCoordinates
import com.tosware.nkm.models.game.hex_effect.HexCellEffectName
import spray.json._

import scala.util.Random

object MarkOfTheWind {
  val metadata: AbilityMetadata = {
    AbilityMetadata(
      name = "Mark of the Wind",
      abilityType = AbilityType.Normal,
      description =
        """Character sets up an invisible trap on a selected area.
          |
          |Range: circular, {range}
          |Max. number of traps: {trapLimit}""".stripMargin,
      variables = NkmConf.extract("abilities.ayatsuji_ayase.markOfTheWind"),
    )
  }
  val trapLocationsKey = "trapLocations"
}

case class MarkOfTheWind(abilityId: AbilityId, parentCharacterId: CharacterId)
  extends Ability(abilityId, parentCharacterId)
    with UsableOnCoordinates
{
  override val metadata: AbilityMetadata = MarkOfTheWind.metadata

  def trapLocations(implicit gameState: GameState): Seq[HexCoordinates] =
    state.variables.get(trapLocationsKey)
      .map(_.parseJson.convertTo[Seq[HexCoordinates]])
      .getOrElse(Seq.empty)

  override def rangeCellCoords(implicit gameState: GameState): Set[HexCoordinates] =
    parentCell.get.coordinates.getCircle(metadata.variables("range")).whereExists

  override def targetsInRange(implicit gameState: GameState): Set[HexCoordinates] =
    rangeCellCoords
      .toCells
      .filterNot(_.effects.exists(_.metadata.name == HexCellEffectName.MarkOfTheWind))
      .toCoords

  override def use(target: HexCoordinates, useData: UseData)(implicit random: Random, gameState: GameState): GameState = {
    val limitExceeded = trapLocations.size == metadata.variables("trapLimit")
    val newTrapLocations =
      if(limitExceeded)
        trapLocations.drop(1) :+ target
      else
        trapLocations :+ target

    val ngs = if(limitExceeded)
      gameState.removeHexCellEffect(trapLocations.head.toCell.effects.ofType[hex_effects.MarkOfTheWind].head.id)(random, id)
    else gameState

    ngs.addHexCellEffect(target, hex_effects.MarkOfTheWind(randomUUID(), Int.MaxValue))(random, id)
      .setAbilityVariable(id, trapLocationsKey, newTrapLocations.toJson.toString)
  }

  override def useChecks(implicit target: HexCoordinates, useData: UseData, gameState: GameState): Set[UseCheck] = {
    super.useChecks ++ Seq(
      !target.toCellOpt.fold(true)(_.effects.exists(_.metadata.name == HexCellEffectName.MarkOfTheWind)) ->
        "There is a trap already on target coordinates.",
    )
  }
}