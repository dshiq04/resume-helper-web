package com.jobtrack.application.controller;

import com.jobtrack.application.pojo.vo.TrashListVO;
import com.jobtrack.application.pojo.po.ApplicationRecord;
import com.jobtrack.application.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping
    public TrashListVO list() {
        return trashService.list();
    }

    @PostMapping("/{id}/restore")
    public ApplicationRecord restore(@PathVariable Long id) {
        return trashService.restore(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        return Map.of("deleted", trashService.delete(id));
    }

    @DeleteMapping
    public Map<String, Object> empty() {
        return Map.of("deleted", trashService.empty());
    }
}
