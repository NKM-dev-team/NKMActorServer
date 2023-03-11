package unit.abilities.hecate

import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.abilities.hecate._
import com.tosware.nkm.models.game.character.CharacterMetadata
import helpers.{TestUtils, scenarios}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class MasterThroneSpec
  extends AnyWordSpecLike
    with Matchers
    with TestUtils
{
  private val metadata = CharacterMetadata.empty()
    .copy(initialAbilitiesMetadataIds = Seq(
      MasterThrone.metadata.id,
      Aster.metadata.id,
      PowerOfExistence.metadata.id,
    ))
  private val s = scenarios.Simple2v2TestScenario(metadata)
  private implicit val gameState: GameState = s.gameState
  private val abilityId =
    s.p(0)(0).character.state.abilities(0).id
  private val asterAbilityId =
    s.p(0)(0).character.state.abilities(1).id
  private val powerOfExistenceAbilityId =
    s.p(0)(0).character.state.abilities(2).id

  MasterThrone.metadata.name must {
    "not be initialized with energy" in {
      gameState.abilityById(abilityId).asInstanceOf[MasterThrone].collectedEnergy should be(0)
    }

    "be able to collect energy from basic attacks" in {
      val newGameState: GameState = gameState.basicAttack(s.p(0)(0).character.id, s.p(1)(0).character.id)
      newGameState.abilityById(abilityId).asInstanceOf[MasterThrone].collectedEnergy(newGameState) should be > 0
    }

    "be able to collect energy from normal ability" in {
      val newGameState: GameState = gameState.useAbilityOnCoordinates(asterAbilityId, s.p(0)(1).spawnCoordinates)
      newGameState.abilityById(abilityId).asInstanceOf[MasterThrone].collectedEnergy(newGameState) should be > 0
    }

    "not be able to collect energy from ultimate ability" in {
      val newGameState: GameState = gameState.useAbility(powerOfExistenceAbilityId)
      newGameState.abilityById(abilityId).asInstanceOf[MasterThrone].collectedEnergy(newGameState) should be (0)
    }
  }
}