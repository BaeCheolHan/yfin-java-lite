package com.example.yfin.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "옵션 종류")
public enum OptionType {
    CALL, PUT;
}


