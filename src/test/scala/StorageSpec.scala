import akka.actor.{Props, ActorRef, ActorSystem}
import akka.util.Timeout
import enhanced._
import akka.testkit.TestActorRef
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import akka.pattern.ask

import scala.util.Success


import org.scalatest._

class StorageSpec extends FlatSpec with Matchers {
  import Storage._

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(60, SECONDS)
  implicit val executionContext = system.dispatcher

  trait StorageTest {
    private val storage = TestActorRef(Props(classOf[Storage[String, Int]], None, Map.empty))

    def run[A](f: (ActorRef) => Future[A]): A = {
      val future = f(storage)
      Thread.sleep(100)
      val Success(result: A) = future.value.get
      result
    }

    def transactionUnit[A](transaction: (ActorRef) => Unit): Unit = {
      (storage ? Begin).mapTo[ActorRef].map(transaction)
      Thread.sleep(100)
    }

    def transaction[A](transaction: (ActorRef) => Future[A]): A = {
      val future = (storage ? Begin).mapTo[ActorRef].flatMap(transaction)
      Thread.sleep(100)
      val Success(result: A) = future.value.get
      result
    }

  }

  "Storage" should "not return values when it's empty" in new StorageTest {
    val result = run { storage =>
      storage ? Get("a")
    }
    result should be(None)
  }

  it should "return transaction on begin" in new StorageTest  {
    val result = run { storage =>
      storage ? Begin
    }
    result.isInstanceOf[ActorRef] should be (true)
  }

  it should "return the value that is put in one-level transaction" in new StorageTest {
    val result = transaction { transaction =>
      transaction ! Put("a", 42)
      transaction ? Get("a")
    }

    result should be(Some(42))
  }

  it should "return the last value that is put in one-level transaction" in new StorageTest {
    val result = transaction { transaction =>
      transaction ! Put("a", 42)
      transaction ! Put("a", 1)
      transaction ? Get("a")
    }

    result should be(Some(1))
  }

  it should "not reflect the changes until transaction is commited" in new StorageTest {
    val result = run { storage =>
      storage ? Get("a")
    }

    transactionUnit { transaction =>
      transaction ! Put("a", 42)
    }

    result should be(None)
  }

  it should "reflect the changes after transaction is commited" in new StorageTest {

    transactionUnit { transaction =>
      transaction ! Put("a", 42)
      transaction ! Commit
    }

    val result = run { storage =>
      storage ? Get("a")
    }

    result should be(Some(42))
  }

  it should "not reflect the changes after transaction is rolled back" in new StorageTest {

    transactionUnit { transaction =>
      transaction ! Put("a", 42)
      transaction ! Rollback
    }

    val result = run { storage =>
      storage ? Get("a")
    }

    result should be(None)
  }

}
