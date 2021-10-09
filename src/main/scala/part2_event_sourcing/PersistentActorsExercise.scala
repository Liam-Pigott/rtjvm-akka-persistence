package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props, actorRef2Scala}
import akka.persistence.PersistentActor

import scala.collection.mutable

object PersistentActorsExercise extends App {

  /*
  Persistent actor for a voting station
  Keep:
  - citizens who voted
  - the poll: mapping between a candidate and the number of received votes

  Actor must be able to recover it's state if it's shut down or restarted.
   */
  case class Vote(citizenPID: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {

    val citizens: mutable.Set[String] = new mutable.HashSet[String]()
    val poll: mutable.Map[String, Int] = new mutable.HashMap[String, Int]()

    override def persistenceId: String = "voting-station"

    override def receiveCommand: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        if(!citizens.contains(vote.citizenPID)) {
          persist(vote) { _ => // COMMAND SOURCING
            log.info(s"Persisted vote $vote")
            handleInternalStateChange(citizenPID, candidate)
          }
        }
        else log.info(s"Citizen $citizenPID has already voted, ignoring.")
      case "print" => log.info(s"Current state: \nCitizens: $citizens\nPolls: $poll")
    }

    def handleInternalStateChange(citizenPID: String, candidate: String): Unit = {
        citizens.add(citizenPID)
        val votes = poll.getOrElse(candidate, 0)
        poll.put(candidate, votes + 1)
    }

    override def receiveRecover: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        log.info(s"Recovered: $vote")
        handleInternalStateChange(citizenPID, candidate)
    }
  }

  val system = ActorSystem("PersistentActorsExercise")
  val votingStation = system.actorOf(Props[VotingStation], "simpleVotingStation")

  val votesMap = Map[String, String](
    "Alice" -> "Martin",
    "Bob" -> "Roland",
    "Charlie" -> "Martin",
    "David" -> "Jonas",
    "Daniel" -> "Martin"
  )

  votesMap.keys.foreach { citizen =>
    votingStation ! Vote(citizen, votesMap(citizen))
  }

  votingStation ! Vote("Daniel", "Daniel")
  votingStation ! "print"


}
