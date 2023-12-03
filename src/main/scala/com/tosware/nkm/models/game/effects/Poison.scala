package com.tosware.nkm.models.game.effects

import com.tosware.nkm.*
import com.tosware.nkm.models.game.*
import com.tosware.nkm.models.game.character_effect.*
import com.tosware.nkm.models.game.effects.Poison.damageKey
import com.tosware.nkm.models.game.event.GameEvent.TurnFinished
import com.tosware.nkm.models.game.event.{GameEvent, GameEventListener}
import spray.json.*

import scala.util.Random

object Poison {
  def metadata: CharacterEffectMetadata =
    CharacterEffectMetadata(
      name = CharacterEffectName.Poison,
      initialEffectType = CharacterEffectType.Negative,
      description = "Receive damage at the end of the turn.",
    )

  val damageKey: String = "damage"
}

object MurasamePoison {
  def metadata: CharacterEffectMetadata =
    CharacterEffectMetadata(
      name = CharacterEffectName.MurasamePoison,
      initialEffectType = CharacterEffectType.Negative,
      description =
        s"""${Poison.metadata.description}
           |
           |Murasame Poison: executes when fully stacked.""".stripMargin,
    )
}

case class Poison(
    effectId: CharacterEffectId,
    initialCooldown: Int,
    damage: Damage,
    metadata: CharacterEffectMetadata = Poison.metadata,
) extends CharacterEffect(effectId)
    with GameEventListener {
  override def onEvent(e: GameEvent.GameEvent)(implicit random: Random, gameState: GameState): GameState =
    e match {
      case GameEvent.EffectAddedToCharacter(_, _, _, _, eid, _) =>
        if (effectId == eid)
          return gameState.setEffectVariable(id, damageKey, damage.toJson.toString)
        gameState
      case TurnFinished(_, _, _, _, _) =>
        val characterIdThatTookAction = gameState.gameLog.characterThatTookActionInTurn(e.turn.number).get
        if (characterIdThatTookAction == parentCharacter.id) {
          gameState.damageCharacter(parentCharacter.id, damage)(random, id)
        } else gameState
      case _ => gameState
    }
}
