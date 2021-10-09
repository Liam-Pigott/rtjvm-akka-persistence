package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object Snapshots extends App {

  //commands
  case class ReceivedMessage(content: String) // message from contact
  case class SentMessage(content: String) // message to contact

  //events
  case class ReceivedMessageRecord(id: Int, contents: String)
  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String): Props = Props(new Chat(owner, contact))
  }
  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {

    val MAX_MESSAGES = 10

    var commandsWithoutCheckpoint = 0
    var currentMessageId = 0
    val lastMessages = new mutable.Queue[(String, String)]()

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received message: $contents")

          maybeReplaceMessage(contact, contents)

          currentMessageId += 1
          maybeCheckpoint()
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message: $contents")

          maybeReplaceMessage(owner, contents)

          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" =>
        log.info(s"Most recent messages: $lastMessages")
      //snapshot related messages
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Saving snapshot succeeded with $metadata")
      case SaveSnapshotFailure(metadata, reason) =>
        log.info(s"Saving snapshot $metadata failed because of $reason")

    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered received message $id: $contents")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"Recovered sent message $id: $contents")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovered snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def maybeReplaceMessage(sender: String, contents: String): Unit = {
      if(lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contents))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if(commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint...")
        saveSnapshot(lastMessages) // async operation
        commandsWithoutCheckpoint = 0
      }
    }

    // pre checkpoint
//    val MAX_MESSAGES = 10
//
//    var currentMessageId = 0
//    val lastMessages = new mutable.Queue[(String, String)]()
//
//    override def persistenceId: String = s"$owner-$contact-chat"
//
//    override def receiveCommand: Receive = {
//      case ReceivedMessage(contents) =>
//        persist(ReceivedMessageRecord(currentMessageId, contents)) { e =>
//          log.info(s"Received message: $contents")
//
//          maybeReplaceMessage(contact, contents)
//
//          currentMessageId += 1
//        }
//      case SentMessage(contents) =>
//        persist(SentMessageRecord(currentMessageId, contents)) { e =>
//          log.info(s"Sent message: $contents")
//
//          maybeReplaceMessage(owner, contents)
//
//          currentMessageId += 1
//        }
//    }
//
//    override def receiveRecover: Receive = {
//      case ReceivedMessageRecord(id, contents) =>
//        log.info(s"Recovered received message $id: $contents")
//        maybeReplaceMessage(contact, contents)
//        currentMessageId = id
//      case SentMessageRecord(id, contents) =>
//        log.info(s"Recovered sent message $id: $contents")
//        maybeReplaceMessage(owner, contents)
//        currentMessageId = id
//    }
//
//    def maybeReplaceMessage(sender: String, contents: String): Unit = {
//      if(lastMessages.size >= MAX_MESSAGES) {
//        lastMessages.dequeue()
//      }
//      lastMessages.enqueue((sender, contents))
//    }
  }

  val system = ActorSystem("SnapshotsDemo")
  val chat = system.actorOf(Chat.props("liam123", "daniel456"))

//  for(i <- 1 to 100000) {
//    chat ! ReceivedMessage(s"Akka rocks $i")
//    chat ! SentMessage(s"Akka rules $i")
//  }

  chat ! "print"

  /*
    event 1
    event 2
    event 3
    snapshot 1
    event 4
    snapshot 2
    event 5
   */

    /*
    pattern:
    - after each persist, maybe save a snapshot
    - if saving a snapshot, handle SnapshotOffer message in receiveRecover
    - optional but best practice: handle SaveSnapshotSuccess and SaveSnapshotFailure
     */

}
