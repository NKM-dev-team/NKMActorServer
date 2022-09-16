package com.tosware.nkm.models.game.abilities.crona

import com.tosware.nkm.NkmConf
import com.tosware.nkm.models.game.Ability.AbilityId
import com.tosware.nkm.models.game.NkmCharacter.CharacterId
import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.hex.HexUtils._
import com.tosware.nkm.models.game.hex.NkmUtils

import scala.util.Random

object ScreechAlpha {
  val metadata: AbilityMetadata =
    AbilityMetadata(
      name = "Screech Alpha",
      abilityType = AbilityType.Normal,
      description =
        """Stun nearby enemies for {stunDuration}t and slow them by {slowAmount} for {slowDuration}t.
          |
          |Radius: {radius}""".stripMargin,
      variables = NkmConf.extract("abilities.crona.screechAlpha"),
    )
}

case class ScreechAlpha(abilityId: AbilityId, parentCharacterId: CharacterId)
  extends Ability(abilityId, parentCharacterId)
    with UsableWithoutTarget {
  override val metadata = ScreechAlpha.metadata

  override def rangeCellCoords(implicit gameState: GameState) =
    parentCell.get.coordinates.getCircle(metadata.variables("radius")).whereExists

  override def targetsInRange(implicit gameState: GameState) =
    rangeCellCoords.whereEnemiesOfC(parentCharacterId)

  private def addEffects(target: CharacterId)(implicit random: Random, gameState: GameState) = {
    val silenceEffect = effects.Stun(
      NkmUtils.randomUUID(),
      metadata.variables("stunDuration")
    )
    val slowEffect = effects.StatNerf(
      NkmUtils.randomUUID(),
      metadata.variables("slowDuration"),
      StatType.Speed,
      metadata.variables("slowAmount")
    )
    gameState
      .addEffect(target, silenceEffect)(random, abilityId)
      .addEffect(target, slowEffect)(random, abilityId)
  }
  override def use()(implicit random: Random, gameState: GameState): GameState =
    targetsInRange.characters.map(_.id)
      .foldLeft(gameState)((acc, cid) => {
        addEffects(cid)(random, acc)
      })
}