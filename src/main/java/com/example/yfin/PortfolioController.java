package com.example.yfin;

import com.example.yfin.model.portfolio.PortfolioSummary;
import com.example.yfin.model.portfolio.PositionDto;
import com.example.yfin.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@Tag(name = "Portfolio API", description = "포트폴리오 손익/배당 요약")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping("/portfolio/summary")
    @Operation(summary = "포트폴리오 요약", description = "포지션 목록을 받아 평가손익/배당수익률을 계산")
    public Mono<PortfolioSummary> summary(@RequestBody List<PositionDto> positions) {
        return portfolioService.summarize(positions);
    }
}



