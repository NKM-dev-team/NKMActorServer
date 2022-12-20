package com.tosware.nkm.models.game

import com.softwaremill.quicklens._
import com.tosware.nkm.Logging
import com.tosware.nkm.actors.Game.GameId
import com.tosware.nkm.models.{Damage, DamageType}
import com.tosware.nkm.models.game.Ability.AbilityId
import com.tosware.nkm.models.game.CharacterEffect.CharacterEffectId
import com.tosware.nkm.models.game.CharacterMetadata.CharacterMetadataId
import com.tosware.nkm.models.game.GameEvent._
import com.tosware.nkm.models.game.NkmCharacter.CharacterId
import com.tosware.nkm.models.game.Player.PlayerId
import com.tosware.nkm.models.game.blindpick._
import com.tosware.nkm.models.game.draftpick._
import com.tosware.nkm.models.game.hex.{HexCell, HexCoordinates, HexMap, NkmUtils}

import scala.util.Random

case class GameState(
                      id: GameId,
                      charactersMetadata: Set[CharacterMetadata],
                      gameStatus: GameStatus,
                      pickType: PickType,
                      numberOfBans: Int,
                      numberOfCharactersPerPlayers: Int,
                      draftPickState: Option[DraftPickState],
                      blindPickState: Option[BlindPickState],
                      hexMap: Option[HexMap],
                      players: Seq[Player],
                      characters: Set[NkmCharacter],
                      phase: Phase,
                      turn: Turn,
                      characterIdsOutsideMap: Set[CharacterId],
                      characterIdsThatTookActionThisPhase: Set[CharacterId],
                      characterTakingActionThisTurn: Option[CharacterId],
                      playerIdsThatPlacedCharacters: Set[PlayerId],
                      abilityStates: Map[AbilityId, AbilityState],
                      characterEffectStates: Map[CharacterEffectId, CharacterEffectState],
                      clockConfig: ClockConfig,
                      clock: Clock,
                      gameLog: GameLog,
                    ) {
  import GameState._
  private implicit val p: Phase = phase
  private implicit val t: Turn = turn

  def host: Player = players.find(_.isHost).get

  def currentPlayerNumber: Int = turn.number % players.size

  def playerNumber(playerId: PlayerId): Int = players.indexWhere(_.id == playerId)

  def playerById(playerId: PlayerId): Option[Player] = players.find(_.id == playerId)

  def currentPlayer: Player = players(currentPlayerNumber)

  def currentCharacter: Option[NkmCharacter] = characterTakingActionThisTurn.flatMap(characterById)

  def currentPlayerTime: Long = clock.playerTimes(currentPlayer.id)

  def abilities: Set[Ability] = characters.flatMap(_.state.abilities)

  def effects: Set[CharacterEffect] = characters.flatMap(_.state.effects)

  def triggerAbilities: Set[Ability with GameEventListener] = abilities.collect {case a: GameEventListener => a}

  def triggerEffects: Set[CharacterEffect with GameEventListener] = effects.collect {case e: GameEventListener => e}

  def characterById(characterId: CharacterId): Option[NkmCharacter] = characters.find(_.id == characterId)

  def abilityById(abilityId: AbilityId): Option[Ability] = abilities.find(_.id == abilityId)

  def effectById(effectId: CharacterEffectId): Option[CharacterEffect] = effects.find(_.id == effectId)

  def characterPickFinished: Boolean = {
    val draftPickFinished = draftPickState.fold(false)(_.pickPhase == DraftPickPhase.Finished)
    val blindPickFinished = blindPickState.fold(false)(_.pickPhase == BlindPickPhase.Finished)
    draftPickFinished || blindPickFinished
  }

  def placingCharactersFinished: Boolean = playerIdsThatPlacedCharacters.size == players.size

  def timeoutNumber: Int = gameStatus match {
    case GameStatus.NotStarted => 0
    case GameStatus.CharacterPick | GameStatus.CharacterPicked =>
      pickType match {
        case PickType.AllRandom => 0
        case PickType.DraftPick => draftPickState.fold(0)(_.pickNumber)
        case PickType.BlindPick => blindPickState.fold(0)(_.pickNumber)
      }
    case GameStatus.CharacterPlacing | GameStatus.Running | GameStatus.Finished =>
      turn.number
  }
  private def handleTrigger(event: GameEvent, trigger: GameEventListener)(implicit random: Random, gameState: GameState) = {
    try {
      trigger.onEvent(event)(random, gameState)
    } catch {
      case e: Throwable =>
        logger.error(e.getMessage)
        gameState
    }

  }

  private def executeEventTriggers(e: GameEvent)(implicit random: Random): GameState = {
    val stateAfterAbilityTriggers = triggerAbilities.foldLeft(this)((acc, ability) => {
      handleTrigger(e, ability)(random, acc)
    })
    triggerEffects.foldLeft(stateAfterAbilityTriggers)((acc, effect) => {
      handleTrigger(e, effect)(random, acc)
    })
  }

  private def logEvent(e: GameEvent)(implicit random: Random): GameState =
    copy(gameLog = gameLog.modify(_.events).using(es => es :+ e))
      .executeEventTriggers(e)

  private def updateClock(newClock: Clock)(implicit random: Random, causedById: String): GameState =
    copy(clock = newClock).logEvent(ClockUpdated(NkmUtils.randomUUID(), newClock))

  private def updateGameStatus(newGameStatus: GameStatus): GameState =
    copy(gameStatus = newGameStatus)

  private def pickTime: Long = pickType match {
    case PickType.AllRandom => clockConfig.timeAfterPickMillis
    case PickType.DraftPick => clockConfig.maxBanTimeMillis
    case PickType.BlindPick => clockConfig.maxPickTimeMillis
  }

  def initializeCharacterPick()(implicit random: Random): GameState =
    updateClock(clock.setPickTime(pickTime))(random, id)

  def setHexMap(hexMap: HexMap): GameState =
    this.modify(_.hexMap).setTo(Some(hexMap))

  def startGame(g: GameStartDependencies)(implicit random: Random): GameState = {
    setHexMap(g.hexMap).copy(
      charactersMetadata = g.charactersMetadata,
      players = g.players,
      pickType = g.pickType,
      numberOfBans = g.numberOfBansPerPlayer,
      numberOfCharactersPerPlayers = g.numberOfCharactersPerPlayer,
      gameStatus = if (g.pickType == PickType.AllRandom) GameStatus.CharacterPicked else GameStatus.CharacterPick,
      draftPickState = if (g.pickType == PickType.DraftPick) Some(DraftPickState.empty(DraftPickConfig.generate(g))) else None,
      blindPickState = if (g.pickType == PickType.BlindPick) Some(BlindPickState.empty(BlindPickConfig.generate(g))) else None,
      clockConfig = g.clockConfig,
      clock = Clock.fromConfig(g.clockConfig, playerOrder = g.players.map(_.name)),
    ).initializeCharacterPick()
  }

  def placeCharactersRandomlyIfAllRandom()(implicit random: Random): GameState = {
    if (pickType == PickType.AllRandom) {
      // TODO: place characters as now they are outside of the map
      // TODO: move character assignment to start game
      assignCharactersToPlayers().copy(
        playerIdsThatPlacedCharacters = players.map(_.id).toSet,
      ).updateGameStatus(GameStatus.Running)
    } else this
  }

  def checkVictoryStatus(): GameState = {
    def filterPendingPlayers: Player => Boolean = _.victoryStatus == VictoryStatus.Pending

    if (gameStatus == GameStatus.CharacterPick && players.count(_.victoryStatus == VictoryStatus.Lost) > 0) {
      this.modify(_.players.eachWhere(filterPendingPlayers).victoryStatus)
        .setTo(VictoryStatus.Drawn)
        .updateGameStatus(GameStatus.Finished)
    } else if (players.count(_.victoryStatus == VictoryStatus.Pending) == 1) {
      this.modify(_.players.eachWhere(filterPendingPlayers).victoryStatus)
        .setTo(VictoryStatus.Won)
        .updateGameStatus(GameStatus.Finished)
    } else this
  }

  def generateCharacter(characterMetadataId: CharacterMetadataId)(implicit random: Random): NkmCharacter = {
    val characterId = NkmUtils.randomUUID()
    val metadata = charactersMetadata.find(_.id == characterMetadataId).get
    NkmCharacter.fromMetadata(characterId, metadata)
  }

  def assignCharactersToPlayers()(implicit random: Random): GameState = {
    val characterSelection: Map[PlayerId, Iterable[CharacterMetadataId]] = pickType match {
      case PickType.AllRandom =>
        val pickedCharacters = random
          .shuffle(charactersMetadata.map(_.id).toSeq)
          .grouped(numberOfCharactersPerPlayers)
          .take(players.length)
        players.map(_.id).zip(pickedCharacters).toMap
      case PickType.DraftPick =>
        draftPickState.get.characterSelection
      case PickType.BlindPick =>
        blindPickState.get.characterSelection
    }

    val playersWithCharacters =
      players.map(p => {
        val generatedCharacters = characterSelection(p.id).map(c => generateCharacter(c)).toSet
        (p, generatedCharacters)
      })
    val playersWithAssignedCharacters = playersWithCharacters.map{case (p, cs) => p.copy(characterIds = cs.map(_.id))}
    val characters = playersWithCharacters.flatMap(_._2).toSet
    val abilitiesByCharacter = characters.map(c => (c.id, c.state.abilities))
    val abilityStatesMap: Map[AbilityId, AbilityState] = abilitiesByCharacter.collect
    {
      case (cid: CharacterId, as: Seq[Ability]) => as.map(a => a.id -> AbilityState())
    }.flatten.toMap

    copy(
      players = playersWithAssignedCharacters,
      characters = characters,
      characterIdsOutsideMap = characters.map(c => c.id),
      abilityStates = abilityStates.concat(abilityStatesMap)
    )
  }

  def checkIfCharacterPickFinished()(implicit random: Random): GameState = {
    implicit val causedById: String = id
    if(characterPickFinished) {
      updateGameStatus(GameStatus.CharacterPicked)
        .updateClock(clock.setPickTime(clockConfig.timeAfterPickMillis))(random, id)
        .assignCharactersToPlayers()
        .logEvent(CharactersPicked(NkmUtils.randomUUID()))
    } else this
  }

  def startPlacingCharacters()(implicit random: Random): GameState =
    updateGameStatus(GameStatus.CharacterPlacing).placeCharactersRandomlyIfAllRandom()

  def decreasePickTime(timeMillis: Long)(implicit random: Random): GameState =
    updateClock(clock.decreasePickTime(timeMillis))(random, id)

  def decreaseTime(playerId: PlayerId, timeMillis: Long)(implicit random: Random): GameState =
    updateClock(clock.decreaseTime(playerId, timeMillis))(random, playerId)

  def increaseTime(playerId: PlayerId, timeMillis: Long)(implicit random: Random): GameState =
    updateClock(clock.increaseTime(playerId, timeMillis))(random, playerId)

  def pause()(implicit random: Random): GameState =
    updateClock(clock.pause())(random, id)

  def unpause()(implicit random: Random): GameState =
    updateClock(clock.unpause())(random, id)

  def surrender(playerIds: PlayerId*): GameState = {
    def filterPlayers: Player => Boolean = p => playerIds.contains(p.name)

    this.modify(_.players.eachWhere(filterPlayers).victoryStatus).setTo(VictoryStatus.Lost).checkVictoryStatus()
  }

  def ban(playerId: PlayerId, characterIds: Set[CharacterMetadataId]): GameState =
    copy(draftPickState = draftPickState.map(_.ban(playerId, characterIds)))

  def finishBanningPhase()(implicit random: Random): GameState =
    copy(
      draftPickState = draftPickState.map(_.finishBanning()),
    ).updateClock(clock.setPickTime(clockConfig.maxPickTimeMillis))(random, id)

  def pick(playerId: PlayerId, characterId: CharacterMetadataId)(implicit random: Random): GameState =
    copy(
      draftPickState = draftPickState.map(_.pick(playerId, characterId)),
    ).updateClock(clock.setPickTime(clockConfig.maxPickTimeMillis))(random, id)
      .checkIfCharacterPickFinished()

  def draftPickTimeout(): GameState =
    surrender(draftPickState.get.currentPlayerPicking.get)

  def blindPick(playerId: PlayerId, characterIds: Set[CharacterMetadataId])(implicit random: Random): GameState =
    copy(blindPickState = blindPickState.map(_.pick(playerId, characterIds)))
      .checkIfCharacterPickFinished()

  def blindPickTimeout(): GameState =
    surrender(blindPickState.get.pickingPlayers: _*)

  def checkIfPlacingCharactersFinished(): GameState =
    if(placingCharactersFinished) updateGameStatus(GameStatus.Running) else this

  def placeCharacters(playerId: PlayerId, coordinatesToCharacterIdMap: Map[HexCoordinates, CharacterId])(implicit random: Random): GameState =
    coordinatesToCharacterIdMap.foldLeft(this){case (acc, (coordinate, characterId)) => acc.placeCharacter(coordinate, characterId)(random, playerId)}
      .copy(playerIdsThatPlacedCharacters = playerIdsThatPlacedCharacters + playerId)
      .checkIfPlacingCharactersFinished()

  def placeCharacter(targetCellCoordinates: HexCoordinates, characterId: CharacterId)(implicit random: Random, causedBy: String): GameState =
    updateHexCell(targetCellCoordinates)(_.copy(characterId = Some(characterId)))
    .modify(_.characterIdsOutsideMap).using(_.filter(_ != characterId))
      .logEvent(CharacterPlaced(NkmUtils.randomUUID(), characterId, targetCellCoordinates))

  def basicMoveCharacter(characterId: CharacterId, path: Seq[HexCoordinates])(implicit random: Random): GameState = {
    implicit val causedBy: CharacterId = characterId
    val newGameState = takeActionWithCharacter(characterId)
    characterById(characterId).get.basicMove(path)(random, newGameState)
      .logEvent(CharacterBasicMoved(NkmUtils.randomUUID(), characterId, path))
  }

  def teleportCharacter(characterId: CharacterId, targetCellCoordinates: HexCoordinates)(implicit random: Random, causedBy: String): GameState = {
    val parentCellOpt = characterById(characterId).get.parentCell(this)

    val removedFromParentCellState = parentCellOpt.fold(this)(c => updateHexCell(c.coordinates)(_.copy(characterId = None)))
    val targetIsFreeToStand = hexMap.get.getCell(targetCellCoordinates).get.isFreeToStand
    val characterIsOnMap = characterById(characterId).get.isOnMap(this)

    if (targetIsFreeToStand) {
      if (characterIsOnMap)
        removedFromParentCellState.updateHexCell(targetCellCoordinates)(_.copy(characterId = Some(characterId)))
      else removedFromParentCellState.placeCharacter(targetCellCoordinates, characterId)
    } else {
      // probably just passing by a friendly character
      removedFromParentCellState.removeCharacterFromMap(characterId)
    }.logEvent(CharacterTeleported(NkmUtils.randomUUID(), characterId, targetCellCoordinates))
  }


  def basicAttack(attackingCharacterId: CharacterId, targetCharacterId: CharacterId)(implicit random: Random): GameState = {
    implicit val causedBy: CharacterId = attackingCharacterId
    val newGameState = takeActionWithCharacter(attackingCharacterId)
    val attackingCharacter = characterById(attackingCharacterId).get
    attackingCharacter.basicAttack(targetCharacterId)(random, newGameState)
      .logEvent(CharacterBasicAttacked(NkmUtils.randomUUID(), attackingCharacterId, targetCharacterId))
  }

  private def updateCharacter(characterId: CharacterId)(updateFunction: NkmCharacter => NkmCharacter): GameState =
    this.modify(_.characters.each).using {
      case character if character.id == characterId => updateFunction(character)
      case character => character
    }

  private def updateHexCell(targetCoords: HexCoordinates)(updateFunction: HexCell => HexCell): GameState =
    this.modify(_.hexMap.each.cells.each).using {
      case cell if cell.coordinates == targetCoords => updateFunction(cell)
      case cell => cell
    }

  def updateAbility(abilityId: AbilityId, newAbility: Ability): GameState =
    this.modify(_.characters.each.state.abilities.each).using {
      case ability if ability.id == abilityId => newAbility
      case ability => ability
    }

  def executeCharacter(characterId: CharacterId)(implicit random: Random, causedBy: String): GameState =
    damageCharacter(characterId, Damage(DamageType.True, Int.MaxValue))

  def damageCharacter(characterId: CharacterId, damage: Damage)(implicit random: Random, causedBy: String): GameState = {
    if(characterById(characterId).get.isDead) {
      logger.error(s"Unable to damage character $characterId. Character dead.")
      this
    } else {
      updateCharacter(characterId)(_.receiveDamage(damage))
        .removeFromMapIfDead(characterId) // needs to be removed first in order to avoid infinite triggers
        .logEvent(CharacterDamaged(NkmUtils.randomUUID(), characterId, damage))
    }
  }

  def heal(characterId: CharacterId, amount: Int)(implicit random: Random, causedBy: String): GameState =
    if(characterById(characterId).get.isDead) {
      logger.error(s"Unable to heal character $characterId. Character dead.")
      this
    } else {
      updateCharacter(characterId)(_.heal(amount))
        .logEvent(CharacterHealed(NkmUtils.randomUUID(), characterId, amount))
    }

  def setHp(characterId: CharacterId, amount: Int)(implicit random: Random, causedBy: String): GameState =
    updateCharacter(characterId)(_.modify(_.state.healthPoints).setTo(amount))
      .logEvent(CharacterHpSet(NkmUtils.randomUUID(), characterId, amount))

  def setStat(characterId: CharacterId, statType: StatType, amount: Int)(implicit random: Random, causedBy: String): GameState = {
    val updateStat = statType match {
      case StatType.AttackPoints => modify(_: NkmCharacter)(_.state.pureAttackPoints)
      case StatType.BasicAttackRange => modify(_: NkmCharacter)(_.state.pureBasicAttackRange)
      case StatType.Speed => modify(_: NkmCharacter)(_.state.pureSpeed)
      case StatType.PhysicalDefense => modify(_: NkmCharacter)(_.state.purePhysicalDefense)
      case StatType.MagicalDefense => modify(_: NkmCharacter)(_.state.pureMagicalDefense)
    }
    updateCharacter(characterId)(c => updateStat(c).setTo(amount))
      .logEvent(CharacterStatSet(NkmUtils.randomUUID(), characterId, statType, amount))
  }

  def removeFromMapIfDead(characterId: CharacterId)(implicit random: Random, causedById: String): GameState =
    if(characterById(characterId).get.isDead) {
      logger.info(characterId + " is dead")
      logEvent(CharacterDied(NkmUtils.randomUUID(), characterId))
        .removeCharacterFromMap(characterId)
    } else this

  def addEffect(characterId: CharacterId, characterEffect: CharacterEffect)(implicit random: Random, causedById: String): GameState =
    updateCharacter(characterId)(_.addEffect(characterEffect))
      .modify(_.characterEffectStates).using(ces => ces.updated(characterEffect.id, CharacterEffectState(characterEffect.initialCooldown)))
      .logEvent(EffectAddedToCharacter(NkmUtils.randomUUID(), characterEffect.id, characterId))

  def removeEffects(characterEffectIds: Seq[CharacterEffectId])(implicit random: Random, causedById: String): GameState =
    characterEffectIds.foldLeft(this){case (acc, eid) => acc.removeEffect(eid)}

  def removeEffect(characterEffectId: CharacterEffectId)(implicit random: Random, causedById: String): GameState = {
    val character = effectById(characterEffectId).get.parentCharacter(this)
    updateCharacter(character.id)(_.removeEffect(characterEffectId))
      .modify(_.characterEffectStates).using(ces => ces.removed(characterEffectId))
      .logEvent(EffectRemovedFromCharacter(NkmUtils.randomUUID(), characterEffectId))
  }

  def removeCharacterFromMap(characterId: CharacterId)(implicit random: Random, causedById: String): GameState = {
    val parentCellOpt = characterById(characterId).get.parentCell(this)

    parentCellOpt.fold(this)(c => updateHexCell(c.coordinates)(_.copy(characterId = None)))
      .modify(_.characterIdsOutsideMap).setTo(characterIdsOutsideMap + characterId)
      .logEvent(CharacterRemovedFromMap(NkmUtils.randomUUID(), characterId))
  }

  def takeActionWithCharacter(characterId: CharacterId)(implicit random: Random): GameState = {
    implicit val causedById: String = characterId

    if(characterTakingActionThisTurn.isDefined) // do not log event more than once
      return this
    this.modify(_.characterTakingActionThisTurn)
      .setTo(Some(characterId))
      .logEvent(CharacterTookAction(NkmUtils.randomUUID(), characterId))
  }

  def abilityHitCharacter(abilityId: AbilityId, targetCharacter: CharacterId)(implicit random: Random): GameState = {
    implicit val causedById: String = abilityId
    logEvent(AbilityHitCharacter(NkmUtils.randomUUID(), abilityId: AbilityId, targetCharacter: CharacterId))
  }

  def refreshBasicMove(targetCharacter: CharacterId)(implicit random: Random, causedById: String): GameState =
    logEvent(BasicMoveRefreshed(NkmUtils.randomUUID(), targetCharacter))

  def refreshBasicAttack(targetCharacter: CharacterId)(implicit random: Random, causedById: String): GameState =
    logEvent(BasicAttackRefreshed(NkmUtils.randomUUID(), targetCharacter))

  def putAbilityOnCooldown(abilityId: AbilityId): GameState = {
    val newState = abilityById(abilityId).get.getCooldownState(this)
    this.copy(abilityStates = abilityStates.updated(abilityId, newState))
  }

  def decrementAbilityCooldown(abilityId: AbilityId): GameState = {
    val newState = abilityById(abilityId).get.getDecrementCooldownState(this)
    this.copy(abilityStates = abilityStates.updated(abilityId, newState))
  }

  def decrementEffectCooldown(effectId: CharacterEffectId)(implicit random: Random): GameState = {
    val newState = effectById(effectId).get.getDecrementCooldownState(this)
    if(newState.cooldown > 0) {
      this.copy(characterEffectStates = characterEffectStates.updated(effectId, newState))
    } else {
      this.removeEffect(effectId)(random, id)
    }
  }

  def useAbilityWithoutTarget(abilityId: AbilityId)(implicit random: Random): GameState = {
    implicit val causedById: String = abilityId
    val ability = abilityById(abilityId).get.asInstanceOf[Ability with UsableWithoutTarget]
    val parentCharacter = ability.parentCharacter(this)

    val newGameState = takeActionWithCharacter(parentCharacter.id)
      .logEvent(AbilityUsedWithoutTarget(NkmUtils.randomUUID(), abilityId))
    ability.use()(random, newGameState)
      .putAbilityOnCooldown(abilityId)
  }

  def useAbilityOnCoordinates(abilityId: AbilityId, target: HexCoordinates, useData: UseData = UseData())(implicit random: Random): GameState = {
    implicit val causedById: String = abilityId
    val ability = abilityById(abilityId).get.asInstanceOf[Ability with UsableOnCoordinates]
    val parentCharacter = ability.parentCharacter(this)

    val newGameState = takeActionWithCharacter(parentCharacter.id)
    ability.use(target, useData)(random, newGameState)
      .logEvent(AbilityUsedOnCoordinates(NkmUtils.randomUUID(), abilityId, target))
      .putAbilityOnCooldown(abilityId)
  }

  def useAbilityOnCharacter(abilityId: AbilityId, target: CharacterId, useData: UseData = UseData())(implicit random: Random): GameState = {
    implicit val causedById: String = abilityId
    val ability = abilityById(abilityId).get.asInstanceOf[Ability with UsableOnCharacter]
    val parentCharacter = ability.parentCharacter(this)

    val newGameState = takeActionWithCharacter(parentCharacter.id)
    ability.use(target, useData)(random, newGameState)
      .logEvent(AbilityUsedOnCharacter(NkmUtils.randomUUID(), abilityId, target))
      .putAbilityOnCooldown(abilityId)
  }

  def incrementTurn(): GameState =
    this.modify(_.turn).using(oldTurn => Turn(oldTurn.number + 1))

  def endTurn()(implicit random: Random, causedById: String = id): GameState = {
    val currentCharacterAbilityIds = currentCharacter.get.state.abilities.map(_.id)
    val currentCharacterEffectIds = currentCharacter.get.state.effects.map(_.id)

    val decrementAbilityCooldownsState = currentCharacterAbilityIds.foldLeft(this)((acc, abilityId) => {
      acc.decrementAbilityCooldown(abilityId)
    })

    val turnFinishedState = decrementAbilityCooldownsState
      .modify(_.characterIdsThatTookActionThisPhase).using(c => c + characterTakingActionThisTurn.get)
      .modify(_.characterTakingActionThisTurn).setTo(None)
      .incrementTurn()
      .logEvent(TurnFinished(NkmUtils.randomUUID()))

    val decrementEffectCooldownsState = currentCharacterEffectIds.foldLeft(turnFinishedState)((acc, effectId) => {
      acc.decrementEffectCooldown(effectId)
    })

    decrementEffectCooldownsState
      .finishPhaseIfEveryCharacterTookAction()
      .logEvent(TurnStarted(NkmUtils.randomUUID()))
  }

  def passTurn(characterId: CharacterId)(implicit random: Random): GameState =
    takeActionWithCharacter(characterId).endTurn()

  def refreshCharacterTakenActions(): GameState =
    this.modify(_.characterIdsThatTookActionThisPhase).setTo(Set.empty)

  def incrementPhase(by: Int = 1): GameState =
    this.modify(_.phase).using(oldPhase => Phase(oldPhase.number + by))

  def finishPhase()(implicit random: Random, causedById: String = id): GameState =
    refreshCharacterTakenActions()
      .incrementPhase()
      .logEvent(PhaseFinished(NkmUtils.randomUUID()))

  def finishPhaseIfEveryCharacterTookAction()(implicit random: Random): GameState =
    if(characterIdsThatTookActionThisPhase == characters.map(_.id))
      this.finishPhase()
    else this

  def toView(forPlayer: Option[PlayerId]): GameStateView =
    GameStateView(
      id = id,
      charactersMetadata = charactersMetadata,
      gameStatus = gameStatus,
      pickType = pickType,
      numberOfBans = numberOfBans,
      numberOfCharactersPerPlayers = numberOfCharactersPerPlayers,
      draftPickState = draftPickState.map(_.toView(forPlayer)),
      blindPickState = blindPickState.map(_.toView(forPlayer)),
      hexMap = hexMap,
      players = players,
      characters = characters.map(_.toView(this)),
      phase = phase,
      turn = turn,
      characterIdsOutsideMap = characterIdsOutsideMap,
      characterIdsThatTookActionThisPhase = characterIdsThatTookActionThisPhase,
      characterTakingActionThisTurn = characterTakingActionThisTurn,
      playerIdsThatPlacedCharacters = playerIdsThatPlacedCharacters,
      abilities = abilities.map(_.toView(this)),
      effects = effects.map(_.toView(this)),
      clockConfig = clockConfig,
      clock = clock,
      currentPlayerId = currentPlayer.id,

    )

  def shortInfo: String =
    s"""
      | id: $id
      | hexMap: ${hexMap.fold("None")(_.toString)}
      | characterIdsOutsideMap: $characterIdsOutsideMap
      | characterIdsThatTookActionThisPhase: $characterIdsThatTookActionThisPhase
      | characterTakingActionThisTurn: $characterTakingActionThisTurn
      | phase: $phase
      | turn: $turn
      | players: $players
      | gameStatus: $gameStatus
      | currentPlayerId: ${if(players.nonEmpty) currentPlayer.id else "None"}
      |""".stripMargin
}

object GameState extends Logging {
  def empty(id: String): GameState = {
    val defaultPickType = PickType.AllRandom
    val defaultClockConfig = ClockConfig.defaultForPickType(defaultPickType)

    GameState(
      id = id,
      charactersMetadata = Set(),
      numberOfBans = 0,
      numberOfCharactersPerPlayers = 1,
      draftPickState = None,
      blindPickState = None,
      hexMap = None,
      players = Seq(),
      characters = Set(),
      phase = Phase(0),
      turn = Turn(0),
      gameStatus = GameStatus.NotStarted,
      pickType = defaultPickType,
      characterIdsOutsideMap = Set(),
      characterIdsThatTookActionThisPhase = Set(),
      characterTakingActionThisTurn = None,
      playerIdsThatPlacedCharacters = Set(),
      abilityStates = Map(),
      characterEffectStates = Map(),
      clockConfig = defaultClockConfig,
      clock = Clock.fromConfig(defaultClockConfig, Seq()),
      gameLog = GameLog(Seq.empty),
    )
  }
}

case class GameStateView(
                          id: GameId,
                          charactersMetadata: Set[CharacterMetadata],
                          gameStatus: GameStatus,
                          pickType: PickType,
                          numberOfBans: Int,
                          numberOfCharactersPerPlayers: Int,
                          draftPickState: Option[DraftPickStateView],
                          blindPickState: Option[BlindPickStateView],
                          hexMap: Option[HexMap],
                          players: Seq[Player],
                          characters: Set[NkmCharacterView],
                          abilities: Set[AbilityView],
                          effects: Set[CharacterEffectView],
                          phase: Phase,
                          turn: Turn,
                          characterIdsOutsideMap: Set[CharacterId],
                          characterIdsThatTookActionThisPhase: Set[CharacterId],
                          characterTakingActionThisTurn: Option[CharacterId],
                          playerIdsThatPlacedCharacters: Set[PlayerId],
                          clockConfig: ClockConfig,
                          clock: Clock,

                          currentPlayerId: PlayerId,
                        )
