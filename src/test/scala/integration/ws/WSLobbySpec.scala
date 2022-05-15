package integration.ws

import com.tosware.NKM.models.game._
import com.tosware.NKM.models.lobby.ws._
import helpers.WSTrait

class WSLobbySpec extends WSTrait
{
  import spray.json._

  def sendRequest(request: WebsocketLobbyRequest): Unit = sendRequestL(request)
  def fetchResponse(): WebsocketLobbyResponse = fetchResponseL()
  def sendWSRequest(route: LobbyRoute, requestJson: String = ""): WebsocketLobbyResponse = sendWSRequestL(route, requestJson)
  def auth(tokenId: Int): WebsocketLobbyResponse = authL(tokenId)
  def observe(lobbyId: String): WebsocketLobbyResponse = observeL(lobbyId)

  val lobbyName = "lobby_name"

  "WS" must {
    "respond to invalid requests" in {
      withLobbyWS {
        wsClient.sendMessage("invalid request")
        val response = fetchResponse()
        response.statusCode shouldBe 500
        response.lobbyResponseType shouldBe LobbyResponseType.Error
      }
    }

    "fetch empty lobbies state" in {
      withLobbyWS {
        fetchLobbies().body shouldBe "[]"
      }
    }

    "allow authenticating" in {
      withLobbyWS {
        auth(0).statusCode shouldBe ok
      }
    }

    "allow observing" in {
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        observe(lobbyId).statusCode shouldBe ok
        setLobbyName(lobbyId, "hi") shouldBe WebsocketLobbyResponse(LobbyResponseType.SetLobbyName, ok)

        val observedResponse = fetchResponse()
        observedResponse.lobbyResponseType shouldBe LobbyResponseType.Lobby
        observedResponse.statusCode shouldBe ok
      }
    }

    "allow creating lobbies" in {
      withLobbyWS {
        auth(0)

        val createLobbyRequest = LobbyCreationRequest(lobbyName).toJson.toString
        val wsRequest = WebsocketLobbyRequest(LobbyRoute.CreateLobby, createLobbyRequest)

        sendRequest(wsRequest)

        val response = fetchResponse()
        response.statusCode shouldBe ok
      }
    }

    "allow joining and leaving lobbies" in {
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        // this request should fail as it is not a lobby id, but lobby name
        joinLobby(lobbyName).statusCode shouldBe nok
        // user that creates the lobby joins it automatically
        joinLobby(lobbyId).statusCode shouldBe nok
        auth(1)
        joinLobby(lobbyId).statusCode shouldBe ok

        fetchAndParseLobby(lobbyId).userIds shouldEqual List(usernames(0), usernames(1))

        leaveLobby(lobbyId).statusCode shouldBe ok
        leaveLobby(lobbyId).statusCode shouldBe nok

        fetchAndParseLobby(lobbyId).userIds shouldEqual List(usernames(0))

        auth(0)
        leaveLobby(lobbyId).statusCode shouldBe ok
        leaveLobby(lobbyId).statusCode shouldBe nok

        fetchAndParseLobby(lobbyId).userIds shouldEqual List()
      }
    }

    "disallow joining a lobby you are already in" in {
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        // user that creates the lobby joins it automatically
        joinLobby(lobbyId).statusCode shouldBe nok
        auth(1)
        joinLobby(lobbyId).statusCode shouldBe ok
        joinLobby(lobbyId).statusCode shouldBe nok
      }
    }

    "disallow leaving a lobby you are already in" in {
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        // user that creates the lobby joins it automatically
        leaveLobby(lobbyId).statusCode shouldBe ok
        leaveLobby(lobbyId).statusCode shouldBe nok
        auth(1)
        leaveLobby(lobbyId).statusCode shouldBe nok
      }
    }

    "allow setting map name in a lobby for a host" in {
      val hexMapNames = List("Linia", "1v1v1")
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        hexMapNames.foreach { hexMapName =>
          setHexMap(lobbyId, hexMapName).statusCode shouldBe ok
          fetchAndParseLobby(lobbyId).chosenHexMapName shouldEqual Some(hexMapName)
        }
      }
    }

    "disallow setting invalid map name" in {
      val hexMapName = "this map does not exist"
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        setHexMap(lobbyId, hexMapName).statusCode shouldBe nok
      }
    }

    "allow setting pick type in a lobby for a host" in {
      val pickTypes = PickType.values
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        pickTypes.foreach { p =>
          setPickType(lobbyId, p)
          fetchAndParseLobby(lobbyId).pickType shouldBe p
        }
      }
    }

    "allow setting number of bans in a lobby for a host" in {
      val numberOfBansList = List(5,3,2,1,0,3)
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        numberOfBansList.foreach { b =>
          setNumberOfBans(lobbyId, b)
          fetchAndParseLobby(lobbyId).numberOfBans shouldBe b
        }
      }
    }

    "disallow setting invalid number of bans in a lobby for a host" in {
      val numberOfBansList = List(-1, -4, -234)
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        numberOfBansList.foreach { b =>
          setNumberOfBans(lobbyId, b).statusCode shouldBe nok
        }
      }
    }

    "allow setting number of characters in a lobby for a host" in {
      val numberOfCharactersList = List(5,3,2,3,8)
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        numberOfCharactersList.foreach { n =>
          setNumberOfCharacters(lobbyId, n)
          fetchAndParseLobby(lobbyId).numberOfCharactersPerPlayer shouldBe n
        }
      }
    }

    "disallow setting invalid number of characters in a lobby for a host" in {
      val numberOfCharactersList = List(-1, 0, -234)
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body

        numberOfCharactersList.foreach { n =>
          setNumberOfCharacters(lobbyId, n).statusCode shouldBe nok
        }
      }
    }

    "allow setting lobby name in a lobby for a host" in {
      val lobbyName2 = "lobby_name2"
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        setLobbyName(lobbyId, lobbyName2).statusCode shouldBe ok
        fetchAndParseLobby(lobbyId).name shouldBe Some(lobbyName2)
      }
    }

    "allow setting clock config in a lobby for a host" in {
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        val newConfig = ClockConfig.emptyDraftPickConfig.copy(maxBanTimeMillis = 500)
        setClockConfig(lobbyId, newConfig).statusCode shouldBe ok
        fetchAndParseLobby(lobbyId).clockConfig shouldBe newConfig
      }
    }

    "allow to start a game" in {
      val hexMapName = "Linia"
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        setHexMap(lobbyId, hexMapName)
        auth(1)
        joinLobby(lobbyId)
        auth(0)
        startGame(lobbyId).statusCode shouldBe ok

        Get(s"/api/state/$lobbyId") ~> routes ~> check {
          val gameState = responseAs[GameState]
          gameState.gamePhase shouldEqual GamePhase.Running
          gameState.players.length shouldEqual 2
          gameState.hexMap.get.name shouldEqual hexMapName
          gameState.numberOfBans shouldEqual 0
          gameState.numberOfCharactersPerPlayers shouldEqual 1
          gameState.pickType shouldEqual PickType.AllRandom
        }
      }
    }

    "allow to start a game with all things changed" in {
      val lobbyName = "A very interesting lobby name ;)"
      val hexMapName = "1v1v1"
      val pickType = PickType.DraftPick
      val numberOfBans = 4
      val numberOfCharacters = 5
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        setHexMap(lobbyId, hexMapName)
        setPickType(lobbyId, pickType)
        setNumberOfBans(lobbyId, numberOfBans)
        setNumberOfCharacters(lobbyId, numberOfCharacters)
        auth(1)
        joinLobby(lobbyId)
        auth(0)
        startGame(lobbyId).statusCode shouldBe ok
        Get(s"/api/state/$lobbyId") ~> routes ~> check {
          val gameState = responseAs[GameState]
          gameState.gamePhase shouldEqual GamePhase.CharacterPick
          gameState.players.length shouldEqual 2
          gameState.hexMap.get.name shouldEqual hexMapName
          gameState.numberOfBans shouldEqual numberOfBans
          gameState.numberOfCharactersPerPlayers shouldEqual numberOfCharacters
          gameState.pickType shouldEqual PickType.DraftPick
        }
      }
    }

    "disallow starting game without hexmap" in {
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        auth(1)
        joinLobby(lobbyId)
        auth(0)
        startGame(lobbyId).statusCode shouldBe nok
      }
    }

    "disallow starting game with 1 or 0 players" in {
      val hexMapName = "Linia"
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        setHexMap(lobbyId, hexMapName).statusCode shouldBe ok
        startGame(lobbyId).statusCode shouldBe nok
        leaveLobby(lobbyId)
        startGame(lobbyId).statusCode shouldBe nok
      }
    }

    "disallow setting stuff in a lobby for non host persons" in {
      var lobbyId: String = ""

      withLobbyWS {
        auth(0)
        lobbyId = createLobby(lobbyName).body
      }

      withLobbyWS {
        setHexMap(lobbyId, "Linia").statusCode shouldBe unauthorized
        setPickType(lobbyId, PickType.AllRandom).statusCode shouldBe unauthorized
        setNumberOfBans(lobbyId, 3).statusCode shouldBe unauthorized
        setNumberOfCharacters(lobbyId, 4).statusCode shouldBe unauthorized
        auth(1)
        setHexMap(lobbyId, "Linia").statusCode shouldBe nok
        setPickType(lobbyId, PickType.AllRandom).statusCode shouldBe nok
        setNumberOfBans(lobbyId, 3).statusCode shouldBe nok
        setNumberOfCharacters(lobbyId, 4).statusCode shouldBe nok
      }
    }

    "disallow setting stuff in a lobby after game start" in {
      val hexMapName = "Linia"
      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        setHexMap(lobbyId, hexMapName)
        auth(1)
        joinLobby(lobbyId)
        auth(0)
        startGame(lobbyId).statusCode shouldBe ok

        setHexMap(lobbyId, hexMapName).statusCode shouldBe nok
        startGame(lobbyId).statusCode shouldBe nok
        leaveLobby(lobbyId).statusCode shouldBe nok
        setPickType(lobbyId, PickType.AllRandom).statusCode shouldBe nok
        setNumberOfCharacters(lobbyId, 3).statusCode shouldBe nok
        setNumberOfBans(lobbyId, 4).statusCode shouldBe nok
      }
    }

    "disallow starting a game with more players than the map allows" in {
      val lobbyName = "lobby_name"
      val numberOfPlayers = 4
      val hexMapName = "1v1v1"
      val pickType = PickType.DraftPick
      val numberOfBans = 4
      val numberOfCharacters = 5

      withLobbyWS {
        auth(0)
        val lobbyId = createLobby(lobbyName).body
        setHexMap(lobbyId, hexMapName).statusCode shouldBe ok
        setPickType(lobbyId, pickType).statusCode shouldBe ok
        setNumberOfBans(lobbyId, numberOfBans).statusCode shouldBe ok
        setNumberOfCharacters(lobbyId, numberOfCharacters).statusCode shouldBe ok
        for (i <- 1 until numberOfPlayers) {
          auth(i)
          joinLobby(lobbyId).statusCode shouldBe ok
        }
        auth(0)
        startGame(lobbyId).statusCode shouldBe nok
        leaveLobby(lobbyId)
        startGame(lobbyId).statusCode shouldBe ok
      }
    }
  }
}