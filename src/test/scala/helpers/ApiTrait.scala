package helpers

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestKit
import com.tosware.NKM.DBManager
import com.tosware.NKM.actors.CQRSEventHandler
import com.tosware.NKM.serializers.NKMJsonProtocol
import com.tosware.NKM.services.http.HttpService
import com.tosware.NKM.services.http.directives.JwtSecretKey
import com.tosware.NKM.services.{GameService, LobbyService, NKMDataService, UserService}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration.DurationInt

trait ApiTrait
    extends AnyWordSpec
      with Matchers
      with ScalatestRouteTest
      with HttpService
      with BeforeAndAfterAll
      with BeforeAndAfterEach
      with NKMJsonProtocol
      with SprayJsonSupport
  {
    implicit val db: JdbcBackend.Database = Database.forConfig("slick.db")
    implicit val NKMDataService: NKMDataService = new NKMDataService()
    implicit val userService: UserService = new UserService()
    implicit val lobbyService: LobbyService = new LobbyService()
    implicit val gameService: GameService = new GameService()
    implicit val jwtSecretKey: JwtSecretKey = JwtSecretKey("jwt_test_key")

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

    val logger = LoggerFactory.getLogger(getClass)

    override def beforeAll(): Unit = {
      // spawn CQRS Event Handler
      system.actorOf(CQRSEventHandler.props(db))
    }

    // Clean up persistence before each test
    override def beforeEach(): Unit = {
      DBManager.dropAllTables(db)
      DBManager.createNeededTables(db)
    }

    override def afterAll(): Unit = {
      TestKit.shutdownActorSystem(system)
      db.close()
    }
}
