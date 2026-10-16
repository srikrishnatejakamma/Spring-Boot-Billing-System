package com.acme.billing.web;

import com.acme.billing.integrations.DemoDestination;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api")
public class OperationsController {
    private final QueryService queries;
    private final OperationsService operations;
    private final ObjectProvider<DemoDestination> demo;
    public OperationsController(QueryService queries, OperationsService operations, ObjectProvider<DemoDestination> demo) {
        this.queries = queries; this.operations = operations; this.demo = demo;
    }
    @GetMapping("/overview") public Views.Overview overview() { return queries.overview(); }
    @GetMapping("/events") public List<Views.Event> events(@RequestParam(required = false) String accountId) { return queries.events(accountId); }
    @PostMapping("/events/{id}/retry") public Views.Event retry(@PathVariable String id) { return operations.retry(id); }
    public record PlatformState(@NotNull Boolean available) {}
    @PutMapping("/demo/platform") public Map<String, Boolean> platform(@Valid @RequestBody PlatformState state) {
        requireDemo().setAvailable(state.available()); return Map.of("available", state.available());
    }
    @GetMapping("/demo/projection") public Collection<com.acme.billing.core.AccountSnapshot> projection() { return requireDemo().snapshots().values(); }
    private DemoDestination requireDemo() {
        var destination = demo.getIfAvailable();
        if (destination == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Only available in demo mode");
        return destination;
    }
}
