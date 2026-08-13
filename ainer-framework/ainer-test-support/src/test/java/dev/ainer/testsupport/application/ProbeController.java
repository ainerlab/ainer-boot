package dev.ainer.testsupport.application;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/probe")
public class ProbeController {

    private final Map<Long, String> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(1);

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable("id") long id) {
        if (!store.containsKey(id)) {
            return Map.of("id", id, "status", "missing");
        }
        return Map.of("id", id, "status", "found", "name", store.get(id));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, String> body) {
        long id = ids.getAndIncrement();
        store.put(id, body.get("name"));
        return Map.of("id", id, "status", "created");
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable("id") long id, @RequestBody Map<String, String> body) {
        store.put(id, body.get("name"));
        return Map.of("id", id, "status", "updated");
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") long id) {
        store.remove(id);
        return Map.of("id", id, "status", "deleted");
    }
}