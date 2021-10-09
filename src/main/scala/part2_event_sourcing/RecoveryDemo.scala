package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = online(0)

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted $event, recovery is ${if(this.recoveryFinished) "" else "Not"} finished.")
          context.become(online(latestPersistedEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info("I have finished recovering")
        // additional init
      case Event(id, contents) =>
//        if(contents.contains("314")){
//          throw new RuntimeException("I can't take this anymore")
//        }
        log.info(s"Recovered: $contents, recovery is ${if(this.recoveryFinished) "" else "Not"} finished.")
        context.become(online(id + 1))
      /*
       will NOT change the event handler during recovery
       after recovery, the normal handler will be the result of stacking all context.becomes
       */
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

//    override def recovery: Recovery = Recovery(toSequenceNr = 100) // recover at most 100 messages
//    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none

  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  /*
  Stashing commands
   */
  for(i <- 1 to 1000) {
    recoveryActor ! Command(s"command $i")
  }
  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
  failure during recovery
  - onRecoveryFailure and actor is STOPPED

  customizing recovery
  - e.g. Recovery(toSequenceNr = 100)
  - DO NOT persist events during incomplete recovery

  recovery status - knowing when done recovering
  - getting  a single when done recovering - RecoveryCompleted

  stateless actors
   */
  recoveryActor ! Command("special command 1")
  recoveryActor ! Command("special command 2")







}
