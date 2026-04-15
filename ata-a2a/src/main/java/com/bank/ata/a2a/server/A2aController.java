package com.bank.ata.a2a.server;

import com.bank.ata.a2a.model.A2aTask;
import com.bank.ata.a2a.model.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * A2A HTTP transport layer — exposes ATA as a peer agent.
 *
 * <pre>
 * GET  /a2a/.well-known/agent.json  — Agent Card (capabilities + skills)
 * POST /a2a/tasks/send              — Submit a task synchronously
 * GET  /a2a/health                  — Liveness probe
 * </pre>
 */
@RestController
@RequestMapping("/a2a")
public class A2aController {

    private static final Logger log = LoggerFactory.getLogger(A2aController.class);

    private final A2aTaskHandler handler;
    private final AgentCard      agentCard;

    public A2aController(A2aTaskHandler handler,
                         @Value("${a2a.server.base-url:http://localhost:8080/a2a}") String baseUrl) {
        this.handler   = handler;
        this.agentCard = handler.buildAgentCard(baseUrl);
        log.info("A2A server ready: baseUrl={} skills={}", baseUrl, agentCard.skills().size());
    }

    // -------------------------------------------------------------------------
    // Agent Card
    // -------------------------------------------------------------------------

    /**
     * Returns the well-known Agent Card so remote agents can discover this agent's
     * capabilities before sending the first task.
     */
    @GetMapping("/.well-known/agent.json")
    public AgentCard agentCard() {
        return agentCard;
    }

    // -------------------------------------------------------------------------
    // Tasks
    // -------------------------------------------------------------------------

    /**
     * Accept an A2A task, process it synchronously, and return the completed task.
     *
     * @param task incoming task with message and optional skillId
     * @return 200 with the completed/failed task, or 400 if the task has no message
     */
    @PostMapping("/tasks/send")
    public ResponseEntity<A2aTask> sendTask(@RequestBody A2aTask task) {
        if (task.message() == null) {
            return ResponseEntity.badRequest().build();
        }

        log.debug("A2A tasks/send received: taskId={}", task.id() != null ? task.id() : "<new>");

        // Assign a new ID if the caller didn't provide one
        A2aTask toProcess = task.id() != null ? task
                : A2aTask.of(task.message(), task.skillId());

        A2aTask result = handler.handle(toProcess);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status",     "UP",
                "agent",      agentCard.name(),
                "version",    agentCard.version(),
                "skillCount", agentCard.skills().size()
        );
    }
}

