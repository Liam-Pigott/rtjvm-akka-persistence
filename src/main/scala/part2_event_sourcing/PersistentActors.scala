package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor

import java.util.Date

object PersistentActors extends App {

  /*
  Scenario: business and an accountant which keeps track of our invoices
   */

  //COMMANDS
  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])

  //SPECIAL MESSAGES
  case object Shutdown

  //EVENTS
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceId = 0
    var totalAmount = 0

    //  how events persisted by this actor will be identified, know which actor wrote what event
    // uniqueness not enforced by akka but is best practice
    override def persistenceId: String = "simple-accountant"

    // normal receive method
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
        when you receive a command
        1 - create an event to persist into the store
        2 - persist the event, pass in a callback that will get triggered once the event is written
        3 - we update the actor state when the event has persisted
         */
        log.info(s"Received invoice for amount: $amount")
        persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount))
        /* time gap: all other messages between persist/callback are stashed */
        { e => // persist is async, race conditions? nope
          // SAFE to access mutable state here as akka persistence guarantees no other thread will access this callback
          latestInvoiceId += 1
          totalAmount += amount

          //correctly identify sender of the command
//          sender() ! "PersistenceAck"

          log.info(s"Persisted $e as invoice #${e.id}, for total amount $totalAmount")
        }
      case InvoiceBulk(invoices) =>
        /*
        create events
        persist all events
        update state after each event is persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map { pair =>
          val id = pair._2
          val invoice = pair._1

          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) { e =>
          latestInvoiceId += 1
          totalAmount += e.amount
          log.info(s"Persisted SINGLE $e as invoice #${e.id}, for total amount $totalAmount")
        }
      case Shutdown =>
        log.info("Shutting down actor")
        context.stop(self)

        // can act like a normal actor too
      case "print" => log.info(s"latest invoice id: $latestInvoiceId")
    }

    // handler called on recovery
    override def receiveRecover: Receive = {
      /*
      best practice: follow the logic in the persist steps of receiveCommand
       */
      case InvoiceRecorded(id, _, _, amount) =>
        log.info(s"Recovered invoice #$id for amount $amount, total amount: $totalAmount")
        latestInvoiceId = id
        totalAmount += amount
    }

    /*
    this method is called if persisting failed.
    the actor will be STOPPED because actor is in unreliable state

    Best practice: start the actor again after some time (use Backoff supervisor)
     */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Failed to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
    Called if journal fails to persist the event
    Actor is RESUMED because we know event was not persisted, actor state still valid.
     */
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for(i <- 1 to 10) accountant ! Invoice("Sofa company", new Date, i * 1000)

  /**
   * Persisting multiple events
   *
   * persistAll
   */

  val newInvoices = for(i <- 1 to 5) yield Invoice("The awesome chairs", new Date, i * 2000)
  //  accountant ! InvoiceBulk(newInvoices.toList)

  /*
  NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES!
  can break actor encapsulation where actor thread is free to process messages and may persist events simultaneously and corrupt actor state
   */

  /**
   * Shutdown persistent actors
   *
   * POISONPILL AND KILL risk killing actor while persisting due to being handled in separate mailbox
   *
   * best practice: define your own shutdown messages to stay in same mailbox
   */
//  accountant ! PoisonPill // no invoice persisted
  accountant ! Shutdown // correctly handled at the end

}
