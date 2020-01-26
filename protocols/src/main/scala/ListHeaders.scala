import io.grpc.{CallOptions, Channel, ClientCall, ClientInterceptor, Context, Contexts, ForwardingClientCall, Metadata, MethodDescriptor, ServerCall, ServerCallHandler, ServerInterceptor}
import io.opentracing.contrib.grpc.OpenTracingContextKey

import scala.collection.JavaConverters._

class ListAllHeadersInterceptor extends ServerInterceptor {
  override def interceptCall[ReqT, RespT](call: ServerCall[ReqT, RespT], headers: Metadata, next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    println("SERVER INTERCEPTOR")
    headers.keys().asScala.foreach { key =>
      val a = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
      println(s"$key -> $a")
    }
    val span = OpenTracingContextKey.activeSpan()

    println(span)
    val context = Option(span).map(_.context())
    println(context)
    println("SERVER INTERCEPTOR END")
    Contexts.interceptCall(Context.current(), call, headers, next)
  }
}

class ListAllHeadersClietn extends ClientInterceptor {
  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT], callOptions: CallOptions, next: Channel): ClientCall[ReqT, RespT] = {
    new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit =  {
        println("")
        println(s"CLIENT INTERCEPTOR ${method.getFullMethodName}")
        headers.keys().asScala.foreach { key =>
          val a = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
          println(s"$key -> $a")
        }
        val span = OpenTracingContextKey.activeSpan()
        val trace = OpenTracingContextKey.activeSpanContext()
        println(trace)

        println(span)

        super.start(responseListener, headers)
      }
    }
  }
}