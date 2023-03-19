package unit.abilities.monkey_d_luffy

import com.tosware.nkm._
import com.tosware.nkm.models.game._
import com.tosware.nkm.models.game.abilities.monkey_d_luffy.RubberRubberFruit
import com.tosware.nkm.models.game.ability.AbilityType
import com.tosware.nkm.models.game.event.GameEvent
import com.tosware.nkm.models.game.hex.{HexCoordinates, TestHexMapName}
import helpers.{TestScenario, TestUtils}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class RubberRubberFruitSpec
  extends AnyWordSpecLike
    with Matchers
    with TestUtils
{
  private val abilityMetadata = RubberRubberFruit.metadata
  private val s = TestScenario.generate(TestHexMapName.RubberRubberFruit, abilityMetadata.id)

  private val rocket1Gs = s.gameState.useAbilityOnCoordinates(s.defaultAbilityId, HexCoordinates(-6, 0))
  private val rocket2Gs = s.gameState.useAbilityOnCoordinates(s.defaultAbilityId, HexCoordinates(-4, 0))
  private val rocket3Gs = s.gameState.useAbilityOnCoordinates(s.defaultAbilityId, HexCoordinates(-2, 0))
  private val bazookaGs = s.gameState.useAbilityOnCoordinates(s.defaultAbilityId, HexCoordinates(1, 0))
  private val pistolGs = s.gameState.useAbilityOnCoordinates(s.defaultAbilityId, HexCoordinates(8, 0))

  private val enchantedGs = s.gameState
    .addEffect(
      s.defaultCharacter.id,
      effects.AbilityEnchant(randomUUID(), 2, AbilityType.Normal),
    )

  private val jetBazookaGs = enchantedGs.useAbilityOnCoordinates(s.defaultAbilityId, HexCoordinates(1, 0))
  private val jetPistolGs = enchantedGs.useAbilityOnCoordinates(s.defaultAbilityId, HexCoordinates(8, 0))

  abilityMetadata.name must {
    "jump with rocket" in {
      s.defaultCharacter.parentCell(rocket1Gs).get.coordinates.toTuple shouldBe (-5, 0)
      s.defaultCharacter.parentCell(rocket2Gs).get.coordinates.toTuple shouldBe (-5, 0)
      s.defaultCharacter.parentCell(rocket3Gs).get.coordinates.toTuple shouldBe (-3, 0)
    }

    "knockback with bazooka" in {
      s.p(1)(0).character.parentCell(bazookaGs).get.coordinates.toTuple shouldBe (9, 0)
    }

    "knockback further with jet bazooka" in {
      s.p(1)(0).character.parentCell(jetBazookaGs).get.coordinates.toTuple shouldBe (13, 0)
    }

    "damage with pistols and bazookas" in {
      def assertOneDamaged(gs: GameState) =
        gs.gameLog.events
          .ofType[GameEvent.CharacterDamaged]
          .causedBy(s.defaultAbilityId)
          .size should be (1)

      assertOneDamaged(bazookaGs)
      assertOneDamaged(pistolGs)
      assertOneDamaged(jetBazookaGs)
      assertOneDamaged(jetPistolGs)
    }
  }
}