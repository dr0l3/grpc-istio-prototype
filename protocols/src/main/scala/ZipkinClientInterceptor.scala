import java.util.Base64

import io.grpc.{CallOptions, Channel, ClientCall, ClientInterceptor, ForwardingClientCall, Metadata, MethodDescriptor, ServerCall, ServerCallHandler, ServerInterceptor}

import scala.collection.JavaConverters._
import scala.util.{Random, Try}

object TracingKeys {
  val traceIdKey = Metadata.Key.of("x-b3-traceid", Metadata.ASCII_STRING_MARSHALLER)
  val spanIdKey = Metadata.Key.of("x-b3-spanid", Metadata.ASCII_STRING_MARSHALLER)
  val parentSpanIdKey = Metadata.Key.of("x-b3-parentspanid", Metadata.ASCII_STRING_MARSHALLER)
  val isSampledKey = Metadata.Key.of("x-b3-sampled", Metadata.ASCII_STRING_MARSHALLER)
  val flagsKey = Metadata.Key.of("x-b3-flags", Metadata.ASCII_STRING_MARSHALLER)
  val all = List(traceIdKey, spanIdKey, parentSpanIdKey, isSampledKey, flagsKey)
}

case class TraceHeaders(traceId: String, spanId: String)


class ZipkinClientInterceptor extends ClientInterceptor {

  def generateId(): String = {
    List.fill(16)(Random.nextInt(15))
      .map(Integer.toHexString)
      .mkString("")
  }

  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT], callOptions: CallOptions, next: Channel): ClientCall[ReqT, RespT] = {
    println("ZIPKIN")
    new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit = {
        println("START")
        val upstreamTraceId = Option(headers.getAll(TracingKeys.traceIdKey)).flatMap(_.asScala.headOption)
        println(upstreamTraceId)
        val upstreamSpanId = Option(headers.getAll(TracingKeys.spanIdKey)).flatMap(_.asScala.headOption)
        val upstreamSampled = Option(headers.getAll(TracingKeys.isSampledKey)).flatMap(_.asScala.headOption)
        val upstreamFlags = Option(headers.get(TracingKeys.flagsKey))

        println(headers)

        // Lets just generate a new set of traces and then pass stuff along
        if(upstreamSampled.contains("0")) {
          super.start(responseListener, headers)
        } else {
          val traceId = upstreamTraceId.getOrElse(generateId())
          val spanId = generateId()
          headers.put(TracingKeys.traceIdKey, traceId)
          headers.put(TracingKeys.spanIdKey, spanId)

          upstreamSpanId match {
            case Some(value) =>
              headers.put(TracingKeys.parentSpanIdKey, value)
            case None =>
              ()
          }

          println(headers)

          super.start(responseListener, headers)
        }
      }
    }
  }
}
