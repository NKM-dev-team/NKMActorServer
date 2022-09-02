package com.tosware.nkm.models.game.effects

import com.tosware.nkm.models.game.CharacterEffect.CharacterEffectId
import com.tosware.nkm.models.game.{CharacterEffect, CharacterEffectMetadata, CharacterEffectName, CharacterEffectType}

object SnareEffect {
  val metadata: CharacterEffectMetadata =
    CharacterEffectMetadata(
      name = CharacterEffectName.Snare,
      effectType = CharacterEffectType.Negative,
      description = "This character cannot basic move.",
      isCc = true,
    )
}

case class SnareEffect(effectId: CharacterEffectId, cooldown: Int) extends CharacterEffect(effectId) {
  val metadata: CharacterEffectMetadata = SnareEffect.metadata
}