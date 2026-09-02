package com.group.resv.resource;

import com.group.resv.common.ApiResult;
import com.group.resv.common.BizException;
import com.group.resv.domain.ResvResource;
import com.group.resv.repo.ResvResourceRepository;
import com.group.resv.redis.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResvResourceRepository repository;
    private final StockService stockService;

    public ResourceController(ResvResourceRepository repository, StockService stockService) {
        this.repository = repository;
        this.stockService = stockService;
    }

    @GetMapping
    public ApiResult<List<ResvResource>> list(@RequestParam(required = false) String type) {
        List<ResvResource> list = type == null || type.isBlank()
                ? repository.findAll()
                : repository.findByTypeOrderByIdAsc(type);
        return ApiResult.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResult<ResvResource> detail(@PathVariable Long id) {
        return ApiResult.ok(repository.findById(id)
                .orElseThrow(() -> new BizException(404, "资源不存在")));
    }

    /** 管理员/运维手工预热库存（启动时也会全量预热）。 */
    @PostMapping("/{id}/preheat")
    public ApiResult<Boolean> preheat(@PathVariable Long id) {
        ResvResource r = repository.findById(id)
                .orElseThrow(() -> new BizException(404, "资源不存在"));
        return ApiResult.ok(stockService.prepare(r));
    }
}
