package com.tosware.nkm.models.game.effects

import com.tosware.nkm.models.game.CharacterEffect.CharacterEffectId
import com.tosware.nkm.models.game.{CharacterEffect, CharacterEffectMetadata, CharacterEffectName, CharacterEffectType}

object StunEffect {
  val metadata: CharacterEffectMetadata =
    CharacterEffectMetadata(
      name = CharacterEffectName.Stun,
      effectType = CharacterEffectType.Negative,
      description = "This character cannot take action.",
      isCc = true,
    )
}

case class StunEffect(effectId: CharacterEffectId, cooldown: Int) extends CharacterEffect(effectId) {
  val metadata: CharacterEffectMetadata = StunEffect.metadata
}