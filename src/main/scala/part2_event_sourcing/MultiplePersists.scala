package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object MultiplePersists extends App {

  /*
  Diligent accountant: with every invoice, will persist 2 events
  - tax record
  - invoice record
   */

  //command
  case class Invoice(recipient: String, date: Date, amount: Int)

  //event
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)
  case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef) = Props(new DiligentAccountant(taxId, taxAuthority))
  }
  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {

    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0

    override def persistenceId: String = "dilligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount / 3)) { record => // 1st message
          taxAuthority ! record
          latestTaxRecordId += 1

          persist("I hereby declare this tax record to be true and complete") { declaration => // 3rd message
            taxAuthority ! declaration
          }
        }
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) { invoiceRecord => // 2nd message
          taxAuthority ! invoiceRecord
          latestInvoiceRecordId += 1

          persist("I hereby declare this invoice record to be true and complete") { declaration => // 4th message
            taxAuthority ! declaration
          }
        }
    }

    override def receiveRecover: Receive = {
      case event => log.info(s"Recovered: $event")
    }
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received: $message")
    }
  }

  val system = ActorSystem("MultiplePersistsDemo")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "HMRC")
  val accountant = system.actorOf(DiligentAccountant.props("UK12345_6789", taxAuthority))

  accountant ! Invoice("The Sofa Company", new Date, 2000)

  /*
  message ordering is guaranteed
  callbacks will also be called in order because of this
   */

  /**
   * PERSISTENCE IS ALSO BASED ON MESSAGE PASSING
   */

  accountant ! Invoice("The Supercar Company", new Date, 20005000) // messages send here will all arrive after persistence is done for the 1st invoice above
}
