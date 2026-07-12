package com.agent.trace;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 全链路追踪 + 工具调用计数。
 *
 * 工具调用追踪原理：
 *   Spring AI 的 .stream().content() 只返回最终文本流，
 *   不暴露中间的工具调用过程。所以用一个 static ThreadLocal 计数器，
 *   每个 @Tool 方法被真正调用时 +1，SSE 响应时读取。
 */
@Component
public class TracingService {

    private final List<Trace> traces = new CopyOnWriteArrayList<>();

    /** 工具调用计数器。
     *  不能用 ThreadLocal！因为 @Tool 方法在 Spring AI 内部线程执行，
     *  drainToolCalls() 在 Controller 的 executor 线程执行，ThreadLocal 隔离导致读不到。 */
    private static final AtomicInteger toolCounter = new AtomicInteger(0);

    /** @Tool 方法内部调用，记录一次工具调用 */
    public static void recordToolCall(String toolName) {
        toolCounter.incrementAndGet();
        System.out.println("  [Trace] 工具调用计数 +1: " + toolName);
    }

    /** 获取并重置工具调用次数 */
    public static int drainToolCalls() {
        return toolCounter.getAndSet(0);
    }

    public Trace startTrace(String userInput) {
        Trace t = new Trace(userInput);
        traces.add(t);
        return t;
    }

    public List<Trace> allTraces() { return traces; }

    public static class Trace {
        public final String userInput;
        public final Instant startTime = Instant.now();
        public final List<Span> spans = new ArrayList<>();

        Trace(String userInput) { this.userInput = userInput; }

        public Span addSpan(String type, String name) {
            Span s = new Span(type, name);
            spans.add(s);
            return s;
        }

        public long totalMs() { return Duration.between(startTime, Instant.now()).toMillis(); }

        /** 工具调用次数 = spans 中手动添加的 + ThreadLocal 计数器 */
        public int toolCount() {
            return (int) spans.stream().filter(s -> "TOOL".equals(s.type)).count() + drainToolCalls();
        }

        public static class Span {
            public String type, name, detail;
            public long durationMs;
            private Instant start = Instant.now();

            public Span(String type, String name) { this.type = type; this.name = name; }
            public void finish(String detail) {
                this.durationMs = Duration.between(start, Instant.now()).toMillis();
                this.detail = detail;
            }
        }
    }
}
