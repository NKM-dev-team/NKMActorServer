package com.tosware.nkm.models.game.abilities.monkey_d_luffy

import com.tosware.nkm._
import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.ability._
import com.tosware.nkm.models.game.hex.{HexCoordinates, SearchFlag}

import scala.util.Random

object RubberRubberFruit {
  val metadata: AbilityMetadata =
    AbilityMetadata(
      name = "Rubber Rubber Fruit",
      alternateName = "ゴムゴムの実 (Gomu Gomu no Mi)",
      abilityType = AbilityType.Normal,
      description =
        """Character uses Devil Fruit power:
          |
          |<i>Enemy in range {bazookaRange}</i>
          |<b>Bazooka</b>
          |Character deals {bazookaDamage} physical damage and knocks back the enemy by {bazookaKnockback} tiles.
          |Knocked back enemies will fly over walls and other characters.
          |
          |<i>Enemy in further range</i>
          |<b>Pistol</b>
          |Character deals {pistolDamage} physical damage.
          |
          |<i>Wall</i>
          |<b>Rocket</b>
          |Character grabs a wall, jumping behind it as many squares as it has to the wall.
          |
          |This ability can be enchanted:
          |
          |<b>Bazooka</b>
          |Damage: {jetBazookaDamage}
          |Knockback: {jetBazookaKnockback}
          |
          |<b>Pistol</b>
          |Damage: {jetPistolDamage}
          |
          |Range: linear, {range}
          |Bazooka cooldown: {bazookaCooldown}""".stripMargin,
      variables = NkmConf.extract("abilities.monkey_d_luffy.rubberRubberFruit"),
    )
}

case class RubberRubberFruit(abilityId: AbilityId, parentCharacterId: CharacterId)
  extends Ability(abilityId, parentCharacterId)
    with UsableOnCoordinates {
  override val metadata: AbilityMetadata = RubberRubberFruit.metadata

  override def rangeCellCoords(implicit gameState: GameState): Set[HexCoordinates] =
    parentCell.fold(Set.empty[HexCoordinates])(
      _.getArea(metadata.variables("range"), Set(SearchFlag.StraightLine)).toCoords
    )

  override def targetsInRange(implicit gameState: GameState): Set[HexCoordinates] =
    rangeCellCoords.whereEnemiesOfC(parentCharacterId) ++ rangeCellCoords.filter(_.toCell.isWall)

  private def rocket(target: HexCoordinates, distance: Int)(implicit random: Random, gameState: GameState): GameState = {
    val jumpDirection = parentCell.get.coordinates.getDirection(target).get
    gameState.jump(parentCharacterId, jumpDirection, distance*2)(random, id)
  }

  private def bazooka(target: HexCoordinates)(implicit random: Random, gameState: GameState): GameState = {
    val damageAmount = metadata.variables(if(isEnchanted) "jetBazookaDamage" else "bazookaDamage")
    val knockback = metadata.variables(if(isEnchanted) "jetBazookaKnockback" else "bazookaKnockback")
    val targetCharacterId = target.toCell.characterId.get
    val knockbackDirection = parentCell.get.coordinates.getDirection(target).get
    hitAndDamageCharacter(targetCharacterId, Damage(DamageType.Physical, damageAmount))
      .jump(targetCharacterId, knockbackDirection, knockback)(random, id)
  }
  private def pistol(target: HexCoordinates)(implicit random: Random, gameState: GameState): GameState = {
    val damageAmount = metadata.variables(if(isEnchanted) "jetPistolDamage" else "pistolDamage")
    hitAndDamageCharacter(target.toCell.characterId.get, Damage(DamageType.Physical, damageAmount))
  }

  override def use(target: HexCoordinates, useData: UseData)(implicit random: Random, gameState: GameState): GameState = {
    val distance = parentCell.get.coordinates.getDistance(target).get
    if(target.toCell.isWall)
      return rocket(target, distance)
    if(distance <= metadata.variables("bazookaRange"))
      return bazooka(target)
    pistol(target)
  }
}