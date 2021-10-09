package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object EventAdapters extends App {

  // acoustic guitar store
  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  //data
  case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC)
  //commands
  case class AddGuitar(guitar: Guitar, quantity: Int)
  //event
  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)
  case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)

  class InventoryManager extends PersistentActor with ActorLogging {
    override def persistenceId: String = "guitar-inventory-manager"

    val inventory: mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, quantity, guitarType)) { _ =>
          addGuitarInventory(guitar, quantity)
          log.info(s"Added $quantity x $guitar to inventory")
        }
      case "print" =>
        log.info(s"Current inventory: $inventory")
    }

    override def receiveRecover: Receive = {
      /*
        if we add recover events for all schema changes, this would get massive and not maintainable, create an adapter
      */
//      case event @ GuitarAdded(id, model, make, quantity) =>
//        log.info(s"Recovered $event")
//        val guitar = Guitar(id, model, make, ACOUSTIC)
//        addGuitarInventory(guitar, quantity)
      case event @ GuitarAddedV2(id, model, make, quantity, guitarType) =>
        log.info(s"Recovered $event")
        val guitar = Guitar(id, model, make, guitarType)
        addGuitarInventory(guitar, quantity)
    }

    def addGuitarInventory(guitar: Guitar, quantity: Int) = {
      val existingQuantity = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existingQuantity + quantity)
    }
  }

  // has config in application.conf
  class GuitarReadEventAdapter extends ReadEventAdapter {
    /*
    journal -> serializer -> read event adapter -> actor
    bytes       GuitarAdded   GuitarAddedV2      receiveRecover
     */
    // event = output of serializer
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(guitarId, guitarModel, guitarMake, quantity) =>
        EventSeq.single(GuitarAddedV2(guitarId, guitarModel, guitarMake, quantity, ACOUSTIC))
      case other => EventSeq.single(other)
    }
  }
  // WriteEventAdapter - used for backwards compatibility - toJournal
  // EventAdapter - extends both read and write

  val system = ActorSystem("EventAdapters", ConfigFactory.load().getConfig("eventAdapters"))
  val inventoryManager = system.actorOf(Props[InventoryManager], "inventoryManager")

  val guitars = for(i <- 1 to 10) yield Guitar(s"$i", s"Hakker $i", "RockTheJVM")
//  guitars.foreach(inventoryManager ! AddGuitar(_, 5))

  inventoryManager ! "print"


}
