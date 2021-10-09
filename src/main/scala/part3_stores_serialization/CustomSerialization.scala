package part3_stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

//command
case class RegisterUser(email: String, name: String)
//event
case class UserRegistered(id: Int, email: String, name: String)

//serializer
class UserRegistrationSerializer extends Serializer {

  val SEPARATOR = "//"

  override def identifier: Int = 12345

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id, email, name) =>
      println(s"Serializing $event")
      s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes()
    case _ => throw new IllegalArgumentException("Only user registration events supported in this serializer")
  }

  // if manifest included, can use class constructor to instantiate object from parsed bytes
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val s = new String(bytes)
    val values = s.substring(1, s.length - 1).split(SEPARATOR)
    val id = values(0).toInt
    val email = values(1)
    val name = values(2)

    val res = UserRegistered(id, email, name)
    println(s"Deserialized $s to $res")

    res
  }

  override def includeManifest: Boolean = false
}

class UserRegistrationActor extends PersistentActor with ActorLogging {
  var currentId = 0
  override def persistenceId: String = "user-registration"

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { e =>
        currentId += 1
        log.info(s"Persisted $e")
      }
  }

  override def receiveRecover: Receive = {
    case event @ UserRegistered(id, _, _) =>
      log.info(s"recovered: $event")
      currentId = id
  }
}

object CustomSerialization extends App {

  /*
  send command to actor
  actor calls persist
  serializer serializes event into bytes
  journal writes the bytes
   */

  val system = ActorSystem("CustomSerialization", ConfigFactory.load().getConfig("customSerializerDemo"))
  val userRegistrationActor = system.actorOf(Props[UserRegistrationActor], "userRegistration")

//  for(i <- 1 to 10) {
//    userRegistrationActor ! RegisterUser(s"user_$i@rtjvm.com", s"User $i")
//  }

}
