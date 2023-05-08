package unit

import com.tosware.nkm.*
import com.tosware.nkm.models.game.event.GameEvent.GameEvent
import helpers.TestUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.reflect.runtime.universe.*

class ImplementationCheckerSpec
  extends AnyWordSpecLike
    with Matchers
    with Logging
    with TestUtils
{
  "Scala files in project" must {
    "implement all events in NkmJsonProtocol" in {
      // Get the names of all classes that derive from GameEvent
      val traitType = typeOf[GameEvent]
      val subTypes = traitType.typeSymbol.asClass.knownDirectSubclasses
      val subTypeNames = subTypes.map(_.name.toString)

      val nkmJsonProtocolFileContents =
        getFileContents("""src\main\scala\com\tosware\nkm\serializers\NkmJsonProtocol.scala""")

      val writeClassNames =
        findMatchingStrings("""case e: (.*) => GameEventSerialized\(e\.getClass\.getSimpleName""".r, nkmJsonProtocolFileContents)

      val readClassNames =
        findMatchingStrings("""case "(.*)" => ges.eventJson""".r, nkmJsonProtocolFileContents)

      subTypeNames.diff(writeClassNames) shouldBe empty
      subTypeNames.diff(readClassNames) shouldBe empty
    }

    def testMetadataProvider(modelPath: String, providerName: String) = {
      val fileNames = readFileNames(s"src/main/scala/com/tosware/nkm/models/$modelPath").toSet

      val providerFileContents =
        getFileContents(s"""src/main/scala/com/tosware/nkm/providers/$providerName.scala""")

      val metadatasDefinedInProvider = findMatchingStrings("""(\w+).metadata""".r, providerFileContents)

      println(fileNames)
      println(metadatasDefinedInProvider)

      fileNames.diff(metadatasDefinedInProvider) shouldBe empty
    }

    "provide all ability metadatas in API" in {
      testMetadataProvider("game/abilities", "AbilityMetadatasProvider")
    }

    "provide all effect metadatas in API" in {
      testMetadataProvider("game/effects", "CharacterEffectMetadatasProvider")
    }

    "provide all hex effect metadatas in API" in {
      testMetadataProvider("game/hex_effects", "HexCellEffectMetadatasProvider")
    }
  }
}
