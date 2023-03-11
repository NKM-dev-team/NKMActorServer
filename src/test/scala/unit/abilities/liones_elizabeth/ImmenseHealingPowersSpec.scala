package unit.abilities.liones_elizabeth

import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.abilities.aqua.NaturesBeauty
import com.tosware.nkm.models.game.abilities.liones_elizabeth._
import com.tosware.nkm.models.game.character.CharacterMetadata
import com.tosware.nkm.models.game.event.GameEvent.{CharacterHealed, HealAmplified}
import helpers.{TestUtils, scenarios}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ImmenseHealingPowersSpec
  extends AnyWordSpecLike
    with Matchers
    with TestUtils
{
  private val abilityMetadata = ImmenseHealingPowers.metadata
  private val metadata = CharacterMetadata.empty().copy(initialAbilitiesMetadataIds = Seq(
    abilityMetadata.id,
    NaturesBeauty.metadata.id,
    Invigorate.metadata.id,
    PowerOfTheGoddess.metadata.id,
  ))
  private val s = scenarios.Simple2v2TestScenario(metadata)

  private val invigorateAbilityId =
    s.p(0)(0).character.state.abilities(2).id

  private val potgAbilityId =
    s.p(0)(0).character.state.abilities(3).id

  private def dmgGs(dmg: Int): GameState =
    s.gameState.damageCharacter(s.p(0)(1).character.id, Damage(DamageType.True, dmg))

  private val dmgGs10: GameState = dmgGs(10)
  private val dmgGs60: GameState = dmgGs(60)
  private val dmgGs95: GameState = dmgGs(95)

  private def invigorateAppliedGs(gs: GameState): GameState =
    gs.useAbilityOnCharacter(invigorateAbilityId, s.p(0)(1).character.id)
      .endTurn()
      .passTurn(s.p(1)(0).character.id)
      .passTurn(s.p(0)(1).character.id)

  private def potgUsedGs(gs: GameState): GameState =
    gs.useAbility(potgAbilityId)

  private def naturesBeautyUsedGs(gs: GameState): GameState =
    gs.basicAttack(s.p(0)(0).character.id, s.p(0)(1).character.id)

  abilityMetadata.name must {
    "apply heal amplifying effect" in {
      val ngs = invigorateAppliedGs(dmgGs95)
      val events = ngs.gameLog.events
      events.ofType[HealAmplified].size should be (1)
    }

    "heal stronger based on missing HP (invigorate)" in {
      val heal1 = invigorateAppliedGs(dmgGs10).gameLog.events.ofType[CharacterHealed].head.amount
      val heal2 = invigorateAppliedGs(dmgGs60).gameLog.events.ofType[CharacterHealed].head.amount
      val heal3 = invigorateAppliedGs(dmgGs95).gameLog.events.ofType[CharacterHealed].head.amount

      heal1 should be < heal2
      heal2 should be < heal3
    }

    "heal stronger based on missing HP (potg)" in {
        val heal1 = potgUsedGs(dmgGs10).gameLog.events.ofType[CharacterHealed].find(_.characterId == s.p(0)(1).character.id).head.amount
        val heal2 = potgUsedGs(dmgGs60).gameLog.events.ofType[CharacterHealed].find(_.characterId == s.p(0)(1).character.id).head.amount
        val heal3 = potgUsedGs(dmgGs95).gameLog.events.ofType[CharacterHealed].find(_.characterId == s.p(0)(1).character.id).head.amount

        heal1 should be < heal2
        heal2 should be < heal3
    }

    "heal stronger based on missing HP (natures beauty)" in {
      val heal1 = naturesBeautyUsedGs(dmgGs10).gameLog.events.ofType[CharacterHealed].head.amount
      val heal2 = naturesBeautyUsedGs(dmgGs60).gameLog.events.ofType[CharacterHealed].head.amount
      val heal3 = naturesBeautyUsedGs(dmgGs95).gameLog.events.ofType[CharacterHealed].head.amount

      heal1 should be < heal2
      heal2 should be < heal3
    }

    "not heal stronger when healed by system" in {
      val heal1 = dmgGs10.heal(s.p(0)(1).character.id, 10).gameLog.events.ofType[CharacterHealed].head.amount
      val heal2 = dmgGs60.heal(s.p(0)(1).character.id, 10).gameLog.events.ofType[CharacterHealed].head.amount
      val heal3 = dmgGs95.heal(s.p(0)(1).character.id, 10).gameLog.events.ofType[CharacterHealed].head.amount

      heal1 should be (heal2)
      heal2 should be (heal3)
    }
  }
}