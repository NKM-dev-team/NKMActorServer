package com.tosware.nkm.models.game.abilities.llenn

import com.tosware.nkm.NkmConf
import com.tosware.nkm.models.game.Ability.AbilityId
import com.tosware.nkm.models.game.NkmCharacter.CharacterId
import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.abilities.llenn.PChan.speedIncrease

import scala.util.Random

object PChan {
  val metadata: AbilityMetadata =
    AbilityMetadata(
      name = "P-Chan",
      abilityType = AbilityType.Passive,
      description = "This character permanently gains speed with every death of a friendly character.",
    )
  val speedIncrease = NkmConf.int("abilities.llenn.pChan.speedIncrease")
}

case class PChan
(
  abilityId: AbilityId,
  parentCharacterId: CharacterId,
) extends Ability(abilityId, parentCharacterId) with GameEventListener {
  override val metadata = PChan.metadata


  override def onEvent(e: GameEvent.GameEvent)(implicit random: Random, gameState: GameState): GameState = {
    e match {
      case GameEvent.CharacterDied(_, characterId) =>
        if(parentCharacter.isFriendForC(characterId)) {
          gameState.setStat(parentCharacterId, StatType.Speed, parentCharacter.state.pureSpeed + speedIncrease)(random, id)
        }
        else gameState
      case _ => gameState
    }
  }
}
