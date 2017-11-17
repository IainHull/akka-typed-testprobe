package io.github.iainhull.akka.typed.testprobe

import akka.typed.scaladsl._
import akka.typed.{ActorRef, Behavior}


object QueueActor {

  // Protocol
  type JobId = String
  type Tenant = String

  case class Job(id: JobId, tenant: Tenant)


  sealed trait JobRequest {
    def job: Job
  }

  sealed trait JobResponse

  final case class EnqueueJob(job: Job, sender: ActorRef[EnqueueJobResponse]) extends JobRequest

  sealed trait EnqueueJobResponse extends JobResponse

  final case class JobEnqueued(job: Job) extends EnqueueJobResponse

  final case class JobNotEnqueued(job: Job, reason: String) extends EnqueueJobResponse

  final case class CompleteJob(job: Job, sender: ActorRef[CompleteJobResponse]) extends JobRequest

  sealed trait CompleteJobResponse extends JobResponse

  final case class JobCompleted(job: Job) extends CompleteJobResponse

  final case class JobNotCompleted(job: Job, reason: String) extends CompleteJobResponse

  // Actor implementation

  type MyContext = ActorContext[JobRequest]
  type MyActorRef = ActorRef[JobRequest]
  type MyBehavior = Behavior[JobRequest]

  def apply(tenantActors: Map[Tenant, MyActorRef] = Map(),
            childCounter: Int = 0,
            childFactory: Tenant => MyBehavior = TenantQueueActor): MyBehavior = {

    def getOrCreateTenantActor(ctx: MyContext, tenant: Tenant): (MyActorRef, MyBehavior) = {
      if (tenantActors.contains(tenant)) {
        tenantActors(tenant) -> Actor.same
      } else {
        val next = childCounter + 1
        val child = ctx.spawn(childFactory(tenant), s"${tenant}-${next}")
        val newBehaviour = apply(tenantActors + (tenant -> child), next, childFactory)

        child -> newBehaviour
      }
    }

    Actor.immutable { (ctx, message) =>
      val (child, newBehavior) = getOrCreateTenantActor(ctx, message.job.tenant)
      child ! message
      newBehavior
    }
  }
}

object TenantQueueActor extends (QueueActor.Tenant => QueueActor.MyBehavior) {
  def apply(tenant: QueueActor.Tenant): QueueActor.MyBehavior = ???
}
