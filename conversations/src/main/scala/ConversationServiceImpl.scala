import conversations.conversations.{Conversation, ConversationServiceGrpc, CreateConversationRequest, CreateConversationResponse, GetConversationRequest, GetConversationResponse}
import users.users.User

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ConversationServiceImpl extends ConversationServiceGrpc.ConversationService {
  val conversationDb = ListBuffer.empty[Conversation]
  override def create(request: CreateConversationRequest): Future[CreateConversationResponse] = Future {
    val id = conversationDb.size
    val conversation = Conversation(id, request.subject, request.creator)
    conversationDb.+=(conversation)
    CreateConversationResponse(conversation)
  }

  override def getById(request: GetConversationRequest): Future[GetConversationResponse] = Future{
    GetConversationResponse(conversationDb.find(_.id == request.id))
  }
}
