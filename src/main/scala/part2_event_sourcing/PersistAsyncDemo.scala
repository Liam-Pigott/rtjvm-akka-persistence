package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  case class Command(contents: String)
  case class Event(contents: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef): Props = Props(new CriticalStreamProcessor(eventAggregator))
  }
  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def persistenceId: String = "critical-stream-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing $contents"
        // with persist, messages sent during time gap are stashed. with persistAsync, message will be processed instead of stashed
        persistAsync(Event(contents)) /*                 TIME GAP                 */{  e =>
          eventAggregator ! e
        }

        // some computation
        val processedContents = contents + "_processed"
        persistAsync(Event(processedContents))  /*                 TIME GAP                 */{ e =>
          eventAggregator ! e
        }
    }

    override def receiveRecover: Receive = {
      case message => log.info(s"Recovered: $message")
    }
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"$message")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "streamProcessor")


  streamProcessor ! Command("command1")
  streamProcessor ! Command("command2")

  /*

  persistAsync
  - better performance due to not stashing commands during time gap - high-throughput environments
  - bad for when absolute ordering must be maintained - mutable state within callbacks

   */



}
