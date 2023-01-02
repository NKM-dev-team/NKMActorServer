package com.tosware.nkm.models.game.abilities.llenn

import com.tosware.nkm.NkmConf
import com.tosware.nkm.models.game.Ability.AbilityId
import com.tosware.nkm.models.game.NkmCharacter.CharacterId
import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.hex.HexCoordinates
import com.tosware.nkm.models.{Damage, DamageType}

import scala.util.Random

object GrenadeThrow {
  val metadata: AbilityMetadata =
    AbilityMetadata(
      name = "Grenade Throw",
      abilityType = AbilityType.Normal,
      description =
        """Character throws a grenade, dealing {damage} physical damage in a sphere to all characters.
          |
          |Range: circular, {range}
          |Radius: circular, {radius}""".stripMargin,
      variables = NkmConf.extract("abilities.llenn.grenadeThrow"),
    )
}

case class GrenadeThrow(abilityId: AbilityId, parentCharacterId: CharacterId) extends Ability(abilityId, parentCharacterId) with UsableOnCoordinates {
  override val metadata = GrenadeThrow.metadata

  override def rangeCellCoords(implicit gameState: GameState) =
    parentCell.get.coordinates.getCircle(metadata.variables("range")).whereExists

  override def use(target: HexCoordinates, useData: UseData)(implicit random: Random, gameState: GameState): GameState = {
    val targets = target.getCircle(metadata.variables("radius")).characters.map(_.id)
    val damage = Damage(DamageType.Physical, metadata.variables("damage"))
    targets.foldLeft(gameState)((acc, cid) => blastCharacter(cid, damage)(random, acc))
  }

  private def blastCharacter(target: CharacterId, damage: Damage)(implicit random: Random, gameState: GameState): GameState =
    gameState
      .abilityHitCharacter(id, target)
      .damageCharacter(target, damage)(random, id)
}
