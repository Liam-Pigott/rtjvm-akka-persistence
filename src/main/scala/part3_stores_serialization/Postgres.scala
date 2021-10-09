package part3_stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object Postgres extends App {

  /**
   * 1 - dependencies in build.sbt
   * 2 - application.conf to config the connection
   * 3 - docker-compose.yml: includes env creds, ports, volumes to run sql
   * 4 - database needs public.journal and public.snapshot to be used with persistence jdbc
   */

  val postgresActorSystem = ActorSystem("postgresSystem", ConfigFactory.load().getConfig("postgresDemo"))
  val persistentActor = postgresActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  for(i <- 1 to 10) persistentActor ! s"I love Akka [$i]"
  persistentActor ! "print"
  persistentActor ! "snap"
  for(i <- 11 to 20) persistentActor ! s"I love Akka [$i]"

}
