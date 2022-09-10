package com.tosware.nkm.models.game.effects

import com.tosware.nkm.NkmConf
import com.tosware.nkm.models.game.Ability.AbilityId
import com.tosware.nkm.models.{Damage, DamageType}
import com.tosware.nkm.models.game.CharacterEffect.CharacterEffectId
import com.tosware.nkm.models.game.NkmCharacter.CharacterId
import com.tosware.nkm.models.game.{CharacterEffect, CharacterEffectMetadata, CharacterEffectName, CharacterEffectType, GameEvent, GameEventListener, GameState}
import com.tosware.nkm.models.game.hex.HexUtils._

import scala.util.Random

object BlackBlood {
  def metadata: CharacterEffectMetadata =
    CharacterEffectMetadata(
      name = CharacterEffectName.BlackBlood,
      effectType = CharacterEffectType.Mixed,
      description = "Black Blood",
      isCc = true,
    )
}

case class BlackBlood(effectId: CharacterEffectId, cooldown: Int, sourceCharacterId: CharacterId, sourceAbilityId: AbilityId)
  extends CharacterEffect(effectId)
    with GameEventListener {
  val metadata: CharacterEffectMetadata = BlackBlood.metadata
  val range = NkmConf.int("abilities.crona.blackBlood.range")
  val damage = NkmConf.int("abilities.crona.blackBlood.damage")

  override def onEvent(e: GameEvent.GameEvent)(implicit random: Random, gameState: GameState): GameState =
    e match {
      case GameEvent.CharacterDamaged(_, characterId, _) =>
        if(characterId != parentCharacter.id) return gameState
        if(!parentCharacter.isOnMap) return gameState

        val enemiesInRange =
          parentCell.get.coordinates
            .getCircle(range)
            .whereEnemiesOfC(sourceCharacterId)
            .characters
            .map(_.id)

        val dmg = Damage(DamageType.Magical, damage)
        enemiesInRange.foldLeft(gameState)((acc, cid) => hitCharacter(cid, dmg)(random, acc))

      case _ => gameState
    }
  def hitCharacter(target: CharacterId, damage: Damage)(implicit random: Random, gameState: GameState): GameState =
    gameState
      .abilityHitCharacter(sourceAbilityId, target)
      .damageCharacter(target, damage)(random, sourceAbilityId)
}
