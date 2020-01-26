import io.opentracing.{Span, SpanContext, Tracer}
import io.opentracing.contrib.grpc.{ActiveSpanContextSource, ActiveSpanSource, OpenTracingContextKey, TracingClientInterceptor}

object TracingStuff {
  def create(tracer: Tracer): TracingClientInterceptor = {
    new TracingClientInterceptor
    .Builder().withTracer(tracer)
      .withStreaming()
      .withVerbosity()
      .withActiveSpanContextSource(ActiveSpanContextSource.GRPC_CONTEXT)
      .build()
  }
}
