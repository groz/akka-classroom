package storage

import akka.actor._

object Storage {
  final case class Get(key: String)
  final case class Put(key: String, value: String)
  final case class Delete(key: String)
  final case class GetResult(key: String, value: Option[String])
  case object Ack
}

class Storage extends Actor {
  import Storage._

  // перейдем в начальное состояние
  override def receive = process(Map.empty)

  def process(store: Map[String, String]): Receive = {

    // в ответ на сообщение Get вернем значение ключа в текущем состоянии
    // актор-отправитель сообщения доступен под именем sender
    case Get(key) =>
      sender ! GetResult(key, store.get(key))

    // в ответ на сообщение Put перейдем в следующее состояние
    // и отправим подтверждение вызывающему
    case Put(key, value) =>
      context become process(store + (key -> value))
      sender ! Ack

    // аналогично
    case Delete(key) =>
      context become process(store - key)
      sender ! Ack
  }
}

object StorageApp extends App {
  val actorSystem = ActorSystem("storage-system")
  val storage: ActorRef = actorSystem.actorOf(Props[Storage], "storage")
  readLine()
  actorSystem.terminate()
}
