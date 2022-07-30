package com.tosware.NKM.models.game.abilities.aqua

import com.tosware.NKM.models.game._

case class NaturesBeauty(initialState: AbilityState) extends Ability {
//  override val metadata = AbilityMetadata
//  (
//    abilityType = Passive,
//    parentCharacter = parentCharacter,
//    name = "Nature's Beauty",
//    description = s"${parentCharacter.name} może używać podstawowych ataków na sojuszników, lecząc ich za ilość HP równą jej obecnemu atakowi.",
//  )
//  )
  override val metadataId = getClass.getSimpleName
  override val id = java.util.UUID.randomUUID.toString
  override val state = initialState
  override val rangeCells = gameState => gameState.characterById(state.currentParentCharacterId).get.basicAttackCells(gameState)
  override val targetsInRange = gameState => ???
//    rangeCells(gameState)
//      .filter(c => c.characterId.nonEmpty
//        &&
//        gameState.players.filter(p => p.characters.map(_.id).contains(state.currentParentCharacterId)).head.characters.map(_.id).contains(c.characterId.get))
  override val canBeUsed = _ => false
  override val use = (gameState, _, _) => gameState
//  override val metadata = AbilityMetadata(
//    abilityType = Passive,
//    name = "Nature's Beauty",
//    description = c => s"${c.name} może używać podstawowych ataków na sojuszników, lecząc ich za ilość HP równą jej obecnemu atakowi.",
}
