package com.tosware.NKM.models.game.effects

import com.tosware.NKM.models.game.CharacterEffect.CharacterEffectId
import com.tosware.NKM.models.game.{CharacterEffect, CharacterEffectMetadata, CharacterEffectName, CharacterEffectType, StatType}

object StatBuffEffect {
  val metadata: CharacterEffectMetadata =
    CharacterEffectMetadata(
      name = CharacterEffectName.StatBuff,
      effectType = CharacterEffectType.Positive,
      description = "Buffs a certain stat in character.",
    )
}

case class StatBuffEffect(effectId: CharacterEffectId, cooldown: Int, statType: StatType, value: Int) extends CharacterEffect(effectId) {
  val metadata: CharacterEffectMetadata = StatBuffEffect.metadata
}
