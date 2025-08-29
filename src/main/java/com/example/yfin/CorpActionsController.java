package com.example.yfin;

import com.example.yfin.model.corp.CorpActionsResponse;
import com.example.yfin.service.CorpActionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "Corporate Actions API", description = "배당락/배당예정/최근 배당/분할 요약")
@RequiredArgsConstructor
public class CorpActionsController {

    private final CorpActionsService corpActionsService;

    

    @GetMapping("/corp-actions")
    @Operation(summary = "기업행위 요약", description = "예정 배당락/지급일 + 최근 배당 10건")
    public Mono<CorpActionsResponse> corp(
            @Parameter(description = "티커") @RequestParam String ticker
    ) { return corpActionsService.corp(ticker.trim().toUpperCase()); }
}


