package com.example.yfin.model.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "기업 개요(summaryProfile) 핵심 필드")
public class SummaryProfileDto {
    @Schema(description = "산업")
    private String industry;

    @Schema(description = "섹터")
    private String sector;

    @Schema(description = "직원 수")
    private Integer fullTimeEmployees;

    @Schema(description = "전화번호")
    private String phone;

    @Schema(description = "웹사이트")
    private String website;

    @Schema(description = "국가")
    private String country;

    @Schema(description = "도시")
    private String city;

    @Schema(description = "주소 1")
    private String address1;

    @Schema(description = "기업 개요(설명)")
    private String longBusinessSummary;
}


