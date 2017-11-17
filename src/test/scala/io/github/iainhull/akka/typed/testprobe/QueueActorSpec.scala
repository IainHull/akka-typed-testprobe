package io.github.iainhull.akka.typed.testprobe

import akka.typed.ActorSystem
import akka.typed.scaladsl.AskPattern._
import akka.util.Timeout
import org.scalatest.{CompleteLastly, FutureOutcome, Matchers, fixture}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class QueueActorSpec extends fixture.AsyncFlatSpec with CompleteLastly with Matchers {

  import QueueActor._


  // A fixture contains the actor as an ActorSystem and a single test probe
  case class Fixture(actor: ActorSystem[JobRequest], probe: AsyncTestProbe[JobRequest])
  type FixtureParam = Fixture

  // Construct the ActorSystem run the test lastly terminate the ActorSystem
  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val testProbe = new AsyncTestProbe[JobRequest]

    val actorSystem = ActorSystem(QueueActor(Map(), 0, _ => testProbe.behavior), "QueueActor")

    complete {
      withFixture(test.toNoArgAsyncTest(Fixture(actorSystem, testProbe)))
    } lastly {
      Await.result(actorSystem.terminate(), 2.second)
      println("Terminated")
    }
  }

  behavior of "QueueActor"

  it should "create child actors on demand" in { param =>
    val Fixture(actor, testProbe) = param
    implicit val timeout = Timeout(2.seconds)
    implicit val sched = actor.scheduler
    val tenant = "sometenant"
    val job = Job("12345678", tenant)

    // Now test

    val futureMessage = testProbe.nextMessage()  // 1
    val futureResponse: Future[EnqueueJobResponse] = actor ? (EnqueueJob(job, _)) // 2
    for {
      EnqueueJob(job, sender) <- futureMessage // 3
      _ = sender ! JobEnqueued(job) // 4
      JobEnqueued(j) <- futureResponse // 5
    } yield {
      j shouldBe job // 6
    }
  }
}
