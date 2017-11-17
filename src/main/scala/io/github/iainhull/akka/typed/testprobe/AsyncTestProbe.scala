package io.github.iainhull.akka.typed.testprobe

import akka.typed.Behavior
import akka.typed.scaladsl._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * An actor test probe, that returns its results asynchronously. Each message the actor receives completes a promise
  *
  * @tparam A
  */
class AsyncTestProbe[A] {
  /**
    * Queue of promises that are completed when a message is received.
    * New promises are added to queueIn and queueOut at the same time
    */
  private val queueIn = mutable.Queue[Promise[A]]()

  /**
    * Queue of promises that are returned by `nextMessage`.
    */
  private val queueOut = mutable.Queue[Promise[A]]()

  /**
    * Read the next promise from the queue or create a new promise in both the in and out queues.
    *
    * NOTE: This method is synchronized as the requests for the queueOut come from the callers thread,
    * while the requests for the queueIn come from the actor's thread.
    *
    * @param queue The queue to read from
    *
    * @return the next promise.
    */
  private def nextPromiseFrom(queue: mutable.Queue[Promise[A]]): Promise[A] = this.synchronized {
    if (queue.isEmpty) {
      val promise = Promise[A]()
      queueIn.enqueue(promise)
      queueOut.enqueue(promise)
    }
    queue.dequeue()
  }

  /**
    * @return a future for the next message this test probe will receive
    */
  def nextMessage(): Future[A] = nextPromiseFrom(queueOut).future

  /**
    * @return a future for the next n messages this probe will receive
    */
  def nextMessages(n: Int)(implicit executor: ExecutionContext): Future[Seq[A]] = this.synchronized {
    require(n >= 1, s"Can only ask for one or more messages, not $n")

    val futures =  (0 to n) map { _ => nextPromiseFrom(queueOut).future }
    Future.sequence(futures)
  }

  private val mutableBehavior = new Actor.MutableBehavior[A] {
    override def onMessage(msg: A) = {
      nextPromiseFrom(queueIn).success(msg)
      this
    }
  }

  /**
    * @return the behavior of this test probe
    */
  def behavior: Behavior[A] = Actor.mutable { _ =>
    mutableBehavior
  }
}
